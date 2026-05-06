# GCP 學習筆記

> Language: **中文** ｜ [English](../en/README.md)

針對 Google Cloud Platform 常用服務的學習筆記與可執行範例，重點放在「概念 → 指令 → 常見坑」的串接。

## 從這裡開始

🗺️ **第一次看？先讀 [00-overview.md](./00-overview.md)**——一張圖看完所有主題的關係、三條學習路徑、詞彙表。

🆘 **卡住了？看 [troubleshooting.md](./troubleshooting.md)**——Auth/網路/部署/帳單/Pub/Sub/Terraform 的決策樹。

## 目錄

| 主題 | 內容 | 連結 |
| --- | --- | --- |
| 00 | 總覽（topic map / 詞彙表 / 學習路徑） | [00-overview.md](./00-overview.md) |
| 01 | GCP 基礎（專案、IAM、計費、`gcloud` CLI） | [01-fundamentals.md](./01-fundamentals.md) |
| 02 | GKE（Google Kubernetes Engine） | [02-gke.md](./02-gke.md) |
| 03 | Cloud Storage（GCS） | [03-cloud-storage.md](./03-cloud-storage.md) |
| 04 | Pub/Sub | [04-pubsub.md](./04-pubsub.md) |
| 05 | Cloud Run（serverless 容器） | [05-cloud-run.md](./05-cloud-run.md) |
| 06 | BigQuery（資料倉儲） | [06-bigquery.md](./06-bigquery.md) |
| 07 | Cloud SQL（託管 RDB） | [07-cloud-sql.md](./07-cloud-sql.md) |
| 08 | Artifact Registry（image / package registry） | [08-artifact-registry.md](./08-artifact-registry.md) |
| 09 | VPC 與 Networking | [09-vpc-networking.md](./09-vpc-networking.md) |
| 10 | 觀測性（Cloud Logging、Monitoring） | [10-observability.md](./10-observability.md) |
| 11 | Secret Manager 與 Cloud KMS | [11-secret-manager-kms.md](./11-secret-manager-kms.md) |
| 12 | Compute Engine（GCE / VM） | [12-compute-engine.md](./12-compute-engine.md) |
| 13 | 成本管理（FinOps） | [13-cost-management.md](./13-cost-management.md) |
| 14 | IAM 進階（Conditions、Org Policy、VPC-SC、WIF） | [14-iam-advanced.md](./14-iam-advanced.md) |
| 15 | HTTPS Load Balancer（深入） | [15-load-balancer.md](./15-load-balancer.md) |
| 16 | CI/CD：Cloud Build 與 Cloud Deploy | [16-cicd.md](./16-cicd.md) |
| 17 | Terraform / IaC | [17-terraform.md](./17-terraform.md) |
| 🆘 | Troubleshooting：常見問題決策樹 | [troubleshooting.md](./troubleshooting.md) |

**端對端示範**：[`demo/`](../demo/README.zh.md) — 用 Cloud Run + Pub/Sub + GCS + Cloud SQL + Secret Manager + Artifact Registry + Terraform 串成「訂單小系統」，可直接 `terraform apply` 跑。

## 環境準備

所有範例假設你已經：

1. 安裝 [Google Cloud SDK](https://cloud.google.com/sdk/docs/install)（提供 `gcloud`、`gsutil`）。
2. 登入並設定預設專案：

   ```bash
   gcloud auth login
   gcloud auth application-default login   # 給 SDK / Terraform 使用 ADC
   gcloud config set project YOUR_PROJECT_ID
   gcloud config set compute/region asia-east1
   gcloud config set compute/zone   asia-east1-b
   ```

3. 啟用會用到的 API（按需要啟用即可，不必一次全開）：

   ```bash
   gcloud services enable \
     container.googleapis.com \
     storage.googleapis.com \
     pubsub.googleapis.com \
     artifactregistry.googleapis.com
   ```

## 學習建議路徑

1. 先讀 **01-fundamentals**：理解專案、IAM、計費單位，避免之後做實驗時誤刪資源或被收費嚇到。
2. 接 **03-cloud-storage**：最單純的服務，熟悉 `gcloud` / `gsutil` 與 IAM 套用方式。
3. 再讀 **04-pubsub**：理解非同步訊息模型、Push vs Pull。
4. 接著 **08-artifact-registry**：學會把 image push 上去，後面 Cloud Run / GKE 都會用到。
5. 想跑服務又不想管 cluster → **05-cloud-run**；想用完整 K8s → **02-gke**。
6. 需要資料庫 → **07-cloud-sql**；需要分析 → **06-bigquery**。
7. 進階：**09-vpc-networking** — 把上面這些放進私網裡，理解 firewall、NAT、Shared VPC。
8. 上 production 前必看：**10-observability**（要看得到問題）+ **11-secret-manager-kms**（不要把密碼推上 git）。
9. **12-compute-engine**：理解 VM 是 GKE node / Cloud SQL 底下的東西。
10. 維運深化：**13-cost-management**（不要爆預算）、**14-iam-advanced**（企業級權限）、**15-load-balancer**（對外網站的完整配方）。
11. 自動化：**16-cicd**（Cloud Build / Cloud Deploy / GitHub Actions）→ **17-terraform**（IaC，**必學**）。
12. 把所學整合：跑一次 [`demo/`](../demo/README.zh.md) 端對端示範。

## 收費警示

> 動手做之前，**先設好預算告警**。GKE Standard 即使閒置也會持續計費（control plane + 節點 VM）；Cloud Storage 的 egress 與 Pub/Sub 的訊息量都可能爆量。

```bash
# 建議到 console 設定 Billing → Budgets & alerts，例如月預算 NT$500、80% 告警
```
