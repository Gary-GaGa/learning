# GCP 學習筆記

針對 Google Cloud Platform 常用服務的學習筆記與可執行範例，重點放在「概念 → 指令 → 常見坑」的串接。

## 目錄

| 主題 | 內容 | 連結 |
| --- | --- | --- |
| 01 | GCP 基礎（專案、IAM、計費、`gcloud` CLI） | [01-fundamentals.md](./01-fundamentals.md) |
| 02 | GKE（Google Kubernetes Engine） | [02-gke.md](./02-gke.md) |
| 03 | Cloud Storage（GCS） | [03-cloud-storage.md](./03-cloud-storage.md) |
| 04 | Pub/Sub | [04-pubsub.md](./04-pubsub.md) |

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
4. 最後做 **02-gke**：把上面三者組合起來——把容器部署到 GKE，從 GCS 讀檔、從 Pub/Sub 接訊息。

## 收費警示

> 動手做之前，**先設好預算告警**。GKE Standard 即使閒置也會持續計費（control plane + 節點 VM）；Cloud Storage 的 egress 與 Pub/Sub 的訊息量都可能爆量。

```bash
# 建議到 console 設定 Billing → Budgets & alerts，例如月預算 NT$500、80% 告警
```
