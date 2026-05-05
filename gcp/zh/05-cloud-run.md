# Cloud Run

把容器當 serverless 跑。給一個 HTTP server 容器，Cloud Run 幫你自動擴展（含縮到 0）、處理 TLS、給你一個 URL。**不需要管 cluster、不需要管 node**。

## 1. 跟 GKE / Functions 的差別

| 服務 | 你給的東西 | 你管的東西 | 適合 |
| --- | --- | --- | --- |
| Cloud Functions | 一段 function 程式碼 | 幾乎沒有 | 簡單 webhook、單一進入點 |
| **Cloud Run** | 任何容器（HTTP server） | Image + 環境變數 + 設定 | 多數 web service / API |
| GKE | 完整 K8s manifests | Cluster + workloads | 複雜系統、需要 K8s 生態 |

> 一個簡單規則：**「我能用 Cloud Run 嗎？」是預設問題，不行才上 GKE。**

## 2. 兩種服務類型

| 類型 | 說明 |
| --- | --- |
| **Service** | 長駐 HTTP server，自動擴展，有 URL。最常用。 |
| **Job** | 一次性執行，跑完就結束，不接 HTTP。適合資料處理、定時任務（搭 Cloud Scheduler）。 |

## 3. 部署 Service

### 從原始碼（Buildpacks 自動 build）

```bash
# 在含 package.json / requirements.txt / go.mod 的目錄
gcloud run deploy hello \
  --source=. \
  --region=asia-east1 \
  --allow-unauthenticated
```

### 從現成 image

```bash
gcloud run deploy hello \
  --image=asia-east1-docker.pkg.dev/PROJECT/repo/hello:v1 \
  --region=asia-east1 \
  --allow-unauthenticated
```

部署完會給你一個 `https://hello-xxxx.a.run.app` 的 URL。

## 4. 重要設定

```bash
gcloud run services update hello \
  --region=asia-east1 \
  --memory=512Mi \
  --cpu=1 \
  --concurrency=80 \              # 單一容器同時處理幾個 request
  --min-instances=0 \             # 縮到 0（會有 cold start）
  --max-instances=20 \
  --timeout=60s \
  --service-account=app-runner@PROJECT.iam.gserviceaccount.com \
  --set-env-vars="LOG_LEVEL=info,FEATURE_X=true"
```

| 參數 | 經驗法則 |
| --- | --- |
| `--concurrency` | I/O bound 設 80（預設）；CPU bound 設 1～10 |
| `--min-instances` | 怕 cold start 設 1；省錢設 0 |
| `--cpu-boost` | cold start 給 2x CPU 加速啟動 |
| `--cpu-throttling` / `--no-cpu-throttling` | 預設只在處理 request 時給 CPU；設 no-throttling 才能跑背景任務 |

## 5. 認證

兩個層次：

### A. 誰可以呼叫 service

```bash
# 公開
gcloud run services add-iam-policy-binding hello --region=asia-east1 \
  --member=allUsers --role=roles/run.invoker

# 限定某個 SA（內部服務之間呼叫常用）
gcloud run services add-iam-policy-binding hello --region=asia-east1 \
  --member=serviceAccount:caller@PROJECT.iam.gserviceaccount.com \
  --role=roles/run.invoker
```

呼叫端要帶 ID token：

```bash
TOKEN=$(gcloud auth print-identity-token)
curl -H "Authorization: Bearer $TOKEN" https://hello-xxxx.a.run.app
```

### B. Service 自己用什麼身份

`--service-account=...` 指定 runtime SA。container 裡用 Google SDK 會自動拿 token，不需 key 檔。

## 6. 流量分配（Canary）

```bash
# 先部署但不接流量
gcloud run deploy hello --image=...:v2 --region=asia-east1 --no-traffic --tag=v2

# 5% 流量導到 v2
gcloud run services update-traffic hello --region=asia-east1 \
  --to-tags=v2=5
```

`v2=5` 代表 5%。觀察沒問題後再 `--to-tags=v2=100`。

## 7. Job 範例（一次性任務）

```bash
gcloud run jobs create import-data \
  --image=asia-east1-docker.pkg.dev/PROJECT/repo/importer:v1 \
  --region=asia-east1 \
  --tasks=10 \                    # 平行 10 個 task
  --task-timeout=10m \
  --max-retries=3

gcloud run jobs execute import-data --region=asia-east1
```

每個 task 會看到環境變數 `CLOUD_RUN_TASK_INDEX`（0~9）和 `CLOUD_RUN_TASK_COUNT`，讓程式自己分片。

## 8. 觀測

- **Logs**：`gcloud run services logs read hello --region=asia-east1 --limit=50`
- **Metrics**：Console 的 Cloud Run 頁面有 request count / latency / instance count。
- **Trace**：在 container 內用 OpenTelemetry，會自動進 Cloud Trace。

## 9. 清理

```bash
gcloud run services delete hello --region=asia-east1
```

> Cloud Run 縮到 0 時就不收錢（除非設了 min-instances）。**忘了刪也比較不會痛**，但建議實驗完就刪。

## 10. 常見坑

- **Service 啟動失敗**：container 必須在 `$PORT`（Cloud Run 注入）監聽，不是寫死 8080。
- **冷啟動慢**：image 太大、初始化太重 → 縮 image、用 `--cpu-boost`、設 `--min-instances=1`。
- **背景任務沒跑完**：預設 CPU 只在處理 request 時給。要做 background work 加 `--no-cpu-throttling`。
- **記憶體 OOM**：Cloud Run 看到 OOM 會殺 container 然後重啟，但你只會在 logs 看到 "Memory limit exceeded"。
- **一直 401**：caller 沒帶 ID token、或 audience 不對。audience 應該是 service URL（含 `https://`）。
