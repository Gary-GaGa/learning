# GCP 基礎

在開始任何服務之前，你需要先理解 GCP 的「資源組織方式」與「身份/權限模型」。沒先把這層搞懂，後面碰到 `permission denied` 會非常痛苦。

## 1. 資源階層

```
Organization
└── Folder（可選，用於部門/環境分組）
    └── Project   ← 帳單、API、IAM 的主要邊界
        └── Resource（GKE cluster、GCS bucket、Pub/Sub topic …）
```

- **Project** 是計費與權限的基本單位。每個 resource 都「屬於」一個 project。
- 同一帳號可以有多個 project，建議至少分 `dev` / `prod` 兩個。
- Project 有兩種識別子：
  - **Project ID**（不可變、全域唯一，例如 `my-app-prod-2026`）→ 指令用這個。
  - **Project Number**（純數字，自動產生）→ IAM service account 等地方會用到。

## 2. IAM（Identity and Access Management）

GCP 的權限模型公式：

```
誰（Principal）  對哪個 Resource  擁有什麼角色（Role）
```

- **Principal** 可以是：使用者帳號、Google Group、Service Account、整個網域。
- **Role** 是「一組權限的集合」，分三種：
  - **Basic**：`roles/owner`、`roles/editor`、`roles/viewer` — 範圍太大，正式環境避免使用。
  - **Predefined**：服務專屬，例如 `roles/storage.objectViewer`、`roles/pubsub.publisher` — **首選**。
  - **Custom**：自己組合權限，平台/安全團隊在用。
- 權限是**累加**的，且可以在不同層級綁定（Organization / Folder / Project / Resource）；下層繼承上層。

### Service Account（SA）

- 給「程式」用的身份，不是給人用的。
- 兩種使用方式：
  - **執行時身份**：例如 GKE Workload Identity、Cloud Run 的 runtime SA。應用直接拿到短期 token，**不需要 key 檔**。
  - **金鑰檔**：`gcloud iam service-accounts keys create key.json` — 方便但風險高，能不用就不用。

```bash
# 範例：給某個 SA 在某個 bucket 上的讀取權限
gcloud storage buckets add-iam-policy-binding gs://my-bucket \
  --member="serviceAccount:my-app@my-project.iam.gserviceaccount.com" \
  --role="roles/storage.objectViewer"
```

## 3. `gcloud` CLI 必備技巧

### 3.1 設定檔切換

如果你同時管多個 project / 環境，用 configurations 切：

```bash
gcloud config configurations create dev
gcloud config set project my-app-dev
gcloud config set account me@example.com

gcloud config configurations create prod
gcloud config set project my-app-prod

gcloud config configurations activate dev   # 切換
gcloud config configurations list
```

### 3.2 看權限與身份

```bash
gcloud auth list                              # 目前登入的帳號
gcloud config list                            # 目前設定
gcloud projects get-iam-policy MY_PROJECT     # 完整 IAM policy
gcloud projects describe MY_PROJECT
```

### 3.3 `--format` / `--filter`

`gcloud` 預設輸出對人友善但對腳本不友善，用 `--format` 解決：

```bash
# 只列 cluster 名稱
gcloud container clusters list --format="value(name)"

# 列特定區域、輸出 JSON
gcloud compute instances list \
  --filter="zone:asia-east1-b AND status=RUNNING" \
  --format=json
```

## 4. 計費觀念

| 計費維度 | 範例 |
| --- | --- |
| 運算時間 | GKE Node、Cloud Run vCPU 秒 |
| 儲存量 | GCS GB-月、Persistent Disk |
| 網路出向（Egress）| 跨 region、跨網際網路最貴 |
| 操作次數 | GCS Class A/B operations、Pub/Sub messages |

> **常見地雷**：跨 region 流量 / 把 bucket 放在錯的 region / 忘了刪測試 cluster。

設定 budget alert：

```text
Console → Billing → Budgets & alerts → Create budget
  Scope: 選 project
  Amount: e.g. NT$500/month
  Threshold: 50%, 90%, 100%（會寄信通知）
```

## 5. Application Default Credentials（ADC）

寫程式呼叫 GCP API 時，SDK 會依序找：

1. 環境變數 `GOOGLE_APPLICATION_CREDENTIALS` 指向的 key 檔
2. `gcloud auth application-default login` 產生的使用者憑證
3. GCE / GKE / Cloud Run 的中繼資料伺服器（自動取得 SA token）

本機開發跑：

```bash
gcloud auth application-default login
```

之後 Python / Node SDK 都不用設任何環境變數就能跑。

## 下一步

理解上面這些後，可以挑一個服務開始：

- 想學「最單純的雲服務」→ [03-cloud-storage.md](./03-cloud-storage.md)
- 想學「非同步系統」→ [04-pubsub.md](./04-pubsub.md)
- 想學 K8s on GCP → [02-gke.md](./02-gke.md)
