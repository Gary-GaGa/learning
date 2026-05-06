# 00 — 總覽：從哪裡開始

這份是給**第一次打開 GCP** 的讀者。用一張圖把所有主題的關係攤開，再給你一個讀完不混亂的順序。

## 1. GCP 服務地景（一張圖看懂）

```mermaid
flowchart TB
  subgraph foundation[基礎建設]
    IAM[IAM / Org / Project<br/>權限與計費邊界]
    NET[VPC / Networking<br/>內部網路]
    LOG[Logging / Monitoring<br/>看得見發生什麼]
    BILL[Billing / FinOps<br/>看得見花了什麼]
  end

  subgraph compute[運算]
    GCE[Compute Engine<br/>VM]
    GKE[GKE<br/>Kubernetes]
    RUN[Cloud Run<br/>serverless container]
  end

  subgraph data[資料]
    GCS[Cloud Storage<br/>物件]
    SQL[Cloud SQL<br/>關聯式 DB]
    BQ[BigQuery<br/>資料倉儲]
    PS[Pub/Sub<br/>訊息匯流排]
  end

  subgraph platform[平台]
    AR[Artifact Registry<br/>image / package]
    SM[Secret Manager / KMS<br/>秘密 / 金鑰]
    LB[HTTPS LB / Cloud Armor<br/>對外入口]
  end

  subgraph delivery[交付]
    CB[Cloud Build / Deploy<br/>CI/CD]
    TF[Terraform<br/>IaC]
  end

  IAM --> compute
  IAM --> data
  NET --> compute
  NET --> SQL
  AR --> RUN & GKE
  SM --> RUN & GKE & GCE
  LB --> RUN & GKE & GCE
  compute --> GCS & SQL & BQ & PS
  PS --> RUN & GKE
  TF -.管理.-> compute & data & platform & delivery
  CB -.部署.-> compute
  LOG & BILL -.觀察.-> compute & data & platform
```

**讀法**：底層（IAM、網路、觀測、計費）撐住一切；運算層（VM/K8s/Run）做事；資料層存東西；平台層管秘密與入口；交付層自動化。

## 2. 主題編號 ↔ 服務 ↔ 何時讀

```mermaid
flowchart LR
  s1["01 Fundamentals<br/>(IAM / project / gcloud)"] --> s3[03 Cloud Storage]
  s1 --> s4[04 Pub/Sub]
  s1 --> s12[12 Compute Engine]
  s3 --> s8[08 Artifact Registry]
  s4 --> s5[05 Cloud Run]
  s8 --> s5
  s8 --> s2[02 GKE]
  s5 --> s7[07 Cloud SQL]
  s5 --> s11[11 Secret / KMS]
  s2 --> s9[09 VPC]
  s12 --> s9
  s9 --> s15[15 HTTPS LB]
  s5 --> s10[10 Observability]
  s5 --> s6[06 BigQuery]
  s10 --> s13[13 Cost Mgmt]
  s11 --> s14[14 IAM Advanced]
  s5 --> s16[16 CI/CD]
  s16 --> s17[17 Terraform]
  s17 --> demo[demo/<br/>端對端]

  style demo fill:#fde68a,stroke:#b45309
```

> 編號是**建議順序**，不是嚴格依賴。需要時可以跳讀；但黃色的 demo 假設你看完前面 17 篇。

## 3. 三條學習路徑

依你的目標選一條，能省掉一半時間：

### 🚀 路徑 A：「我要把一個 web service 上 GCP」

最短可達生產：

```mermaid
flowchart LR
  A[01 Fundamentals] --> B[08 Artifact Registry]
  B --> C[05 Cloud Run]
  C --> D[11 Secret/KMS]
  D --> E[15 HTTPS LB]
  E --> F[10 Observability]
  F --> G[16 CI/CD]
  G --> H[17 Terraform]
```

跳過 GKE / GCE / Pub/Sub / BigQuery 也能上線。

### 📊 路徑 B：「我要做資料分析平台」

```mermaid
flowchart LR
  A[01 Fundamentals] --> B[03 Cloud Storage]
  B --> C[04 Pub/Sub]
  C --> D[06 BigQuery]
  D --> E[10 Observability]
  E --> F[13 Cost Mgmt]
```

**13 Cost Mgmt 對 BQ 特別重要**——一個 SELECT * 可能要幾百美。

### ☸️ 路徑 C：「我已經會 K8s，想搬到 GKE」

```mermaid
flowchart LR
  A[01 Fundamentals] --> B[09 VPC]
  B --> C[08 Artifact Registry]
  C --> D[02 GKE]
  D --> E[14 IAM Advanced<br/>Workload Identity]
  E --> F[10 Observability]
  F --> G[15 HTTPS LB]
  G --> H[17 Terraform]
```

## 4. 詞彙表（最容易混淆的）

| 詞 | 是什麼 | 常見誤解 |
| --- | --- | --- |
| **Project** | 計費 / IAM / API 的單位（每個 resource 屬於一個） | 跟 Folder / Org 搞混；Project ID（字串）跟 Project Number（數字）兩個都有 |
| **Service Account（SA）** | 給程式用的身份，是 IAM principal | 不是「服務的設定檔」；**SA email 與 K8s ServiceAccount 是兩回事** |
| **ADC** | Application Default Credentials | 不是某個檔案，是 SDK 的「找憑證順序」 |
| **Workload Identity** | 讓 GKE Pod / Cloud Run 自動拿 GCP 身份 | 不要跟 Workload Identity Federation（WIF，給外部用的）混 |
| **Region / Zone** | Region = 地理區（asia-east1）；Zone = region 內的 datacenter（asia-east1-a/b/c） | Cloud Storage bucket 也叫 location 但用 region 名 |
| **Tag vs Label** | Label = 純標籤（計費/搜尋）；Tag = 有 IAM 模型，可作 Org Policy 條件 | 兩個都叫 tag/label，會搞混 |
| **GCS Class A vs B operations** | A = 寫操作（PUT、LIST），B = 讀操作（GET） | A 比 B 貴 10 倍以上，大量小檔上傳會痛 |
| **VPC（GCP）** | **全域**物件，subnet 才綁 region | 跟 AWS 不一樣；可以一個 VPC 跨多 region |
| **Egress** | 出向流量 | 跨 region、出網際網路才貴；同 region 內部多半免費 |
| **CMEK / CSEK** | CMEK = 自管金鑰（KMS）；CSEK = 客戶提供金鑰 | CMEK 是 99% 場景；CSEK 幾乎不用 |

## 5. 前置知識

讀這份筆記**不需要**：GCP 經驗。

讀這份筆記**最好已經懂**：

- 基本 Linux / shell（會 `cd`、`curl`、environment variable）
- 基本網路（IP、DNS、TLS、HTTP status code）
- Docker 基本概念（image、container、Dockerfile）
- Git / GitHub 基本

完全不熟 K8s 也沒關係——讀 02-gke 時碰到 Pod / Deployment 不懂可以跳過實作部分。

## 6. 動手前先做這三件事

1. **建一個專用 GCP project**：不要用主帳號的舊 project，亂測會誤刪資源。
2. **設預算告警**：Console → Billing → Budgets。NT$300、NT$500、NT$1000 三段告警。
3. **裝 gcloud + 跑兩條登入**：
   ```bash
   gcloud auth login                       # 給 CLI 用
   gcloud auth application-default login   # 給 SDK / Terraform 用
   ```

之後從 [01-fundamentals](./01-fundamentals.md) 開始。

## 7. 卡住時去哪

- **指令錯誤 / 權限問題**：先看 [`troubleshooting.md`](./troubleshooting.md) 的決策樹。
- **觀念問題**：每篇底部都有「常見坑」，多半是新手會碰到的具體錯誤。
- **官方文件**：`https://cloud.google.com/<service>/docs`（例：`/run/docs`）。
- **搜尋技巧**：加上 `site:cloud.google.com` 過濾掉雜訊。
