# Cloud Storage（GCS）

物件儲存服務。把它想成「無限大的 KV store」：key 是路徑、value 是檔案。**不是檔案系統**——沒有真正的目錄概念，`/` 只是 key 的一部分。

## 1. 核心概念

```
Bucket   ── 全域唯一名稱、綁定 region/multi-region、設定預設儲存類別
└── Object   ── 一份檔案 + metadata，key 不限長度（包含 /）
```

- **Bucket 名稱全 GCP 全域唯一**（不是「你的專案內唯一」）。命名建議：`{org}-{project}-{purpose}`，例如 `acme-data-lake-raw`。
- **Region 重要**：跨 region 讀寫會產生 egress 費用。資料給 GKE 用，bucket 就放在同一個 region。

## 2. 儲存類別（Storage Class）

| Class | 適合 | 最短儲存期 | 取出費 |
| --- | --- | --- | --- |
| `STANDARD` | 常存取、熱資料 | 無 | 低 |
| `NEARLINE` | 一個月存取一次 | 30 天 | 中 |
| `COLDLINE` | 一季存取一次 | 90 天 | 高 |
| `ARCHIVE` | 一年存取一次、合規備份 | 365 天 | 最高 |

> 提早刪除「冷資料」會被收「early deletion fee」當作有放滿最短儲存期。

可以用 **Object Lifecycle** 自動降級：例如 30 天後 STANDARD → NEARLINE，1 年後 → ARCHIVE。

## 3. 動手做

> 新版 CLI 全部用 `gcloud storage`（取代舊 `gsutil`）。下面用新版。

### 建立 bucket

```bash
gcloud storage buckets create gs://YOUR-UNIQUE-BUCKET \
  --location=asia-east1 \
  --default-storage-class=STANDARD \
  --uniform-bucket-level-access     # 強制只用 IAM，不要 ACL（推薦）
```

### 上傳 / 下載 / 列出

```bash
# 上傳
echo "hello gcs" > hello.txt
gcloud storage cp hello.txt gs://YOUR-UNIQUE-BUCKET/

# 上傳整個資料夾
gcloud storage cp -r ./data gs://YOUR-UNIQUE-BUCKET/data/

# 列出
gcloud storage ls gs://YOUR-UNIQUE-BUCKET/
gcloud storage ls -l gs://YOUR-UNIQUE-BUCKET/data/   # 含大小、修改時間

# 下載
gcloud storage cp gs://YOUR-UNIQUE-BUCKET/hello.txt ./

# 同步資料夾（類似 rsync）
gcloud storage rsync -r ./local-dir gs://YOUR-UNIQUE-BUCKET/dir/
```

### 給權限

```bash
# 讓某個 SA 只能讀某個 bucket
gcloud storage buckets add-iam-policy-binding gs://YOUR-UNIQUE-BUCKET \
  --member="serviceAccount:gke-app@PROJECT.iam.gserviceaccount.com" \
  --role="roles/storage.objectViewer"

# 開放公開讀（小心！僅用於 public assets）
gcloud storage buckets add-iam-policy-binding gs://YOUR-UNIQUE-BUCKET \
  --member="allUsers" \
  --role="roles/storage.objectViewer"
```

### 簽署網址（Signed URL）

要把私有檔案臨時開放給外部使用者下載：

```bash
gcloud storage sign-url gs://YOUR-UNIQUE-BUCKET/private.pdf \
  --duration=15m \
  --impersonate-service-account=signer@PROJECT.iam.gserviceaccount.com
```

得到的 URL 任何人 15 分鐘內可以下載，過期失效。常見用法：前端拿這個 URL 直接 PUT 上傳，避免檔案經過後端伺服器。

### 物件版本控制（Versioning）

防呆，避免誤刪/覆寫：

```bash
gcloud storage buckets update gs://YOUR-UNIQUE-BUCKET --versioning
```

開啟後刪除/覆寫舊物件會被保留為 noncurrent version，可以 `gcloud storage ls --all-versions` 看到。

### 生命週期規則

`lifecycle.json`：

```json
{
  "rule": [
    {
      "action": { "type": "SetStorageClass", "storageClass": "NEARLINE" },
      "condition": { "age": 30, "matchesStorageClass": ["STANDARD"] }
    },
    {
      "action": { "type": "Delete" },
      "condition": { "age": 365, "isLive": false }
    }
  ]
}
```

```bash
gcloud storage buckets update gs://YOUR-UNIQUE-BUCKET \
  --lifecycle-file=lifecycle.json
```

## 4. 程式呼叫（Python 範例）

```python
# pip install google-cloud-storage
from google.cloud import storage

client = storage.Client()                     # 自動拿 ADC
bucket = client.bucket("YOUR-UNIQUE-BUCKET")

# 寫
blob = bucket.blob("greetings/hi.txt")
blob.upload_from_string("hello from python")

# 讀
print(blob.download_as_text())

# 列出
for b in client.list_blobs("YOUR-UNIQUE-BUCKET", prefix="greetings/"):
    print(b.name, b.size, b.updated)
```

> 部署到 GKE 時搭配 [Workload Identity](./02-gke.md#5-workload-identity重要)，Pod 內這段程式不用任何 key 檔就能跑。

## 5. 清理

```bash
# 刪 bucket（必須先空）
gcloud storage rm -r gs://YOUR-UNIQUE-BUCKET/**
gcloud storage buckets delete gs://YOUR-UNIQUE-BUCKET
```

## 6. 常見坑

- **Bucket 名稱被佔用**：全域唯一，加上你的 org/project 前綴。
- **跨 region 抓資料慢/貴**：bucket region 跟 compute region 對齊。
- **公開了不該公開的**：開 `uniform-bucket-level-access`，避免 ACL 搞出意外公開。Console 的 **Public access** 欄位永遠檢查一下。
- **想用「目錄」操作**：GCS 沒有目錄。`gs://b/a/b/c.txt` 是一個 key。`/` 純粹用於前綴查詢。
- **大量小檔效能差**：每個 PUT 都是 Class A operation。能合併就合併（例如改用 tar / parquet）。
