# 成本管理（FinOps on GCP）

雲端帳單可以一夜爆炸。這篇講「**怎麼可預測、可歸屬、可優化**」三件事。

## 1. 怎麼計費（複習）

| 維度 | 例子 |
| --- | --- |
| 運算時間 | GKE node、GCE VM、Cloud Run vCPU-秒 |
| 儲存 | GCS GB-月、PD GB-月、BQ active storage |
| 網路 egress | 跨 region / 對外網路最貴 |
| 操作次數 | GCS class A/B、Pub/Sub messages、KMS calls |
| 託管服務 | Cloud SQL、BigQuery on-demand、AlloyDB |

帳單**每天**更新一次，不是即時。看到 spike 通常已經發生 6–24 小時。

## 2. 三層防線

### 第 1 層：Budget alert（預警）

```bash
# 設專案月預算 NT$3000，達 50/90/100% 通知
# Console → Billing → Budgets & alerts → Create budget
#   Scope: 該 Billing Account 下的 project
#   Amount: 3000 TWD
#   Threshold rules: 0.5, 0.9, 1.0
#   Notifications: email + Pub/Sub topic
```

收 Pub/Sub 後可以接 Cloud Function 自動關 expensive resources（如 stop GCE VM、disable APIs）。

### 第 2 層：Quotas（硬上限）

預算只是警告，不會阻擋使用。要真的擋下來：

```bash
# Console → IAM & Admin → Quotas
#   找 BigQuery → Query usage per day per user → 設 1 TB
#   找 Compute Engine → CPUs → 設專案總 vCPU 上限
```

**Custom org quota** 可以在組織層強制下游不能超用。

### 第 3 層：Billing export to BigQuery（事後分析）

```bash
# Console → Billing → Billing export → BigQuery export
#   選一個 dataset，從那天起每筆計費 row 自動進 BQ
```

之後可以用 SQL 任意切：

```sql
-- 上個月各專案花費
SELECT project.name, SUM(cost) AS spend
FROM `BILLING_PROJECT.billing_export.gcp_billing_export_v1_XXX`
WHERE DATE(usage_start_time) BETWEEN DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY) AND CURRENT_DATE()
GROUP BY 1
ORDER BY spend DESC;

-- 最貴的 SKU
SELECT sku.description, SUM(cost) AS c
FROM `...`
WHERE DATE(usage_start_time) >= DATE_SUB(CURRENT_DATE(), INTERVAL 7 DAY)
GROUP BY 1 ORDER BY c DESC LIMIT 20;
```

## 3. 成本歸屬：Labels

**每個 resource 都加 label**，billing export 裡會出現。建議標準 labels：

| Label | 範例值 |
| --- | --- |
| `env` | `prod` / `staging` / `dev` |
| `team` | `platform` / `data` / `growth` |
| `app` | `orders-api` / `analytics-pipeline` |
| `cost-center` | `cc-1234` |

```bash
# 加 label
gcloud compute instances add-labels demo-vm --labels=env=prod,team=platform

# Cloud Run
gcloud run services update hello --labels=env=prod,app=hello

# GCS
gcloud storage buckets update gs://my-bucket --update-labels=env=prod
```

> 用 **Org Policy** 強制「沒打 label 不准建 resource」（`compute.requireLabels` 等）。

之後 BQ 查：

```sql
SELECT labels.value AS team, SUM(cost) AS c
FROM `...`, UNNEST(labels) labels
WHERE labels.key = 'team'
GROUP BY 1 ORDER BY c DESC;
```

## 4. 折扣機制

| 機制 | 折扣 | 適合 |
| --- | --- | --- |
| **Sustained Use Discount (SUD)** | 自動，跑滿月最多 30% off | 長期跑的 GCE / GKE |
| **Committed Use Discount (CUD)** | 1 年 37%、3 年 55%（vCPU 與 RAM） | 已知穩定基礎用量 |
| **Flex CUD** | 跨機型、跨 region 彈性折扣 | 用量會變但總量穩定 |
| **Spot VM** | 60–91% off | 可中斷的工作 |
| **BigQuery Editions / Reserved slots** | 預付 slot | BQ 高頻穩定用量 |

實務做法：**先跑 1–2 個月觀察基礎用量 → 對「保證會用到的部分」買 1 年 CUD → 浮動部分用 on-demand / Spot**。

## 5. 找浪費：Recommender

GCP 自動掃描閒置 / 過大資源並建議：

```bash
# 閒置 VM
gcloud recommender recommendations list \
  --recommender=google.compute.instance.IdleResourceRecommender \
  --location=asia-east1-b \
  --project=PROJECT

# 機型過大（rightsizing）
gcloud recommender recommendations list \
  --recommender=google.compute.instance.MachineTypeRecommender \
  --location=asia-east1-b --project=PROJECT

# 閒置 PD
gcloud recommender recommendations list \
  --recommender=google.compute.disk.IdleResourceRecommender \
  --location=asia-east1-b --project=PROJECT
```

Console → **Active Assist → Recommendations** 有完整列表（閒置 SA、過寬 IAM、未用 IP…）。

## 6. 服務專屬省錢心法

| 服務 | 大招 |
| --- | --- |
| GCE / GKE | Spot VM；MIG autoscaler 設好 max；下班把 dev cluster 關掉 |
| Cloud Run | `min-instances=0`（可接受 cold start）；`concurrency` 調大 |
| Cloud SQL | dev 用 zonal、停機（可手動 `stop`，計費降到只剩 storage） |
| GCS | Lifecycle 自動降級到 Nearline/Coldline；多版本要設過期 |
| BigQuery | partition + cluster 必設；`SELECT *` 禁用；用 `--maximum-bytes-billed` 卡上限 |
| Network | 跨 region / 對外 egress 最貴，盡量同 region；CDN cache 靜態 |
| Logging | exclude 健康檢查 / debug log；超過 30d 才付費 |

## 7. dev/test 自動關機（cron）

```bash
# Cloud Scheduler 每天 20:00 停 dev VM
gcloud scheduler jobs create http stop-dev-vm \
  --location=asia-east1 \
  --schedule="0 20 * * 1-5" \
  --time-zone="Asia/Taipei" \
  --uri="https://compute.googleapis.com/compute/v1/projects/PROJECT/zones/asia-east1-b/instances/dev-vm/stop" \
  --http-method=POST \
  --oauth-service-account-email=scheduler@PROJECT.iam.gserviceaccount.com
```

開機規則類推。MIG autoscaler 也可以排程縮到 0。

## 8. 監看流量瞬間爆量

```sql
-- 過去 6 小時哪個 project 出現異常 cost spike
WITH hourly AS (
  SELECT project.id AS project_id,
         TIMESTAMP_TRUNC(usage_start_time, HOUR) AS h,
         SUM(cost) AS c
  FROM `...`
  WHERE usage_start_time > TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 24 HOUR)
  GROUP BY 1, 2
)
SELECT project_id, h, c
FROM hourly
WHERE c > (SELECT AVG(c) * 3 FROM hourly)
ORDER BY h DESC;
```

可以放 Looker Studio 看板，或設 Monitoring alert（資料來自 BQ）。

## 9. 常見坑

- **忘了刪測試資源**：Standard GKE cluster、Cloud SQL HA、idle PD 都會默默扣錢。**設 1 個月預算 + alert，便宜的標準。**
- **跨 region 流量爆**：app 跟資料不同 region、log sink 跨 region。Billing export 看 SKU `Network Inter-Region Egress`。
- **BigQuery 一條 query 燒幾百美**：on-demand 每 TB scan 計費，沒過濾 partition。永遠 dry-run 先看。
- **Egress 從 GCP 出到網際網路**：對外 API、CDN miss 都算。檢查 SKU `Network Internet Egress`。
- **沒打 label 就難分攤**：事後再加只會 cover 之後的計費，往回追不出去。新建 resource 一律強制打 label。
- **CUD 買錯 region/機型**：1 年起跳不能退。先 `Recommender → CUD recommender` 看建議，確認用量穩定再買。
