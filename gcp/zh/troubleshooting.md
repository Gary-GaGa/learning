# Troubleshooting：常見問題的決策流程

「為什麼壞了」是新手最痛的時刻。這篇把我見過 80% 的「卡住」狀況做成決策樹。

> 第一步：永遠先看錯誤訊息全文。GCP 的錯誤訊息**通常很精準**，但會被截斷在 console 的小視窗裡。從 Cloud Logging 撈完整的（搜 `severity>=ERROR` + 你的 service）。

## 1. 認證 / 權限：401、403、PERMISSION_DENIED

新手最常碰到的問題。先分清楚 **誰** 在做 **什麼** 操作。

```mermaid
flowchart TD
  A[看到 403 / PERMISSION_DENIED] --> B{是誰在打<br/>API?}
  B -->|我自己用 gcloud| C[gcloud auth list<br/>確認當前帳號]
  B -->|程式 / SDK| D[GOOGLE_APPLICATION_CREDENTIALS?<br/>有沒有設]
  B -->|Cloud Run / GKE Pod| E[該服務用的 service account]
  B -->|GitHub Actions / CI| F[Workload Identity Federation 設定]

  C --> G[gcloud projects get-iam-policy PROJECT<br/>看自己有哪些 role]
  D --> H[沒設的話走 ADC<br/>跑 gcloud auth application-default login]
  E --> I[gcloud run services describe ...<br/>看 spec.template.serviceAccount]
  F --> J[檢查 attribute-condition<br/>repo / branch 是否符合]

  G --> K{role 夠嗎?}
  I --> K
  H --> K
  J --> K
  K -->|不夠| L[加上對應的 predefined role<br/>不要直接給 owner]
  K -->|夠| M[檢查 IAM Conditions:<br/>有沒有時間/resource 限制]
  M --> N[檢查目標 resource:<br/>是不是同 project?<br/>有沒有 IAM 直接綁在 resource 上?]
```

### 速查：「我給了 role 還是不通」常見原因

| 症狀 | 原因 |
| --- | --- |
| 剛給 role 立刻打就不通 | IAM 變更最多需 **2 分鐘** propagate |
| 在 resource 上看不到自己的 binding | binding 可能在 project / folder / org 任一層 |
| Role 對但服務不懂 | 該 service 沒支援 IAM Conditions，但你加了 condition 等於沒給 |
| Cloud Run / Function 401 | caller 沒帶 ID token，或 audience 寫錯（要是完整 service URL） |
| GKE Pod 403 | Workload Identity 三步驟有一步漏：cluster pool / GCP SA 給 token / K8s SA 加 annotation |
| Service A 呼 Service B 401 | A 的 SA 要有 B 的 `run.invoker`（不是 A 自己的權限） |

## 2. 部署失敗 / 容器起不來

```mermaid
flowchart TD
  A[Cloud Run / GKE 部署失敗] --> B{症狀}
  B -->|Image pull error| C[image path 對嗎?<br/>node SA 有 artifactregistry.reader?]
  B -->|Container 啟動 crash| D[gcloud run services logs read<br/>kubectl logs --previous]
  B -->|Pod Pending| E[kubectl describe pod<br/>看 events]
  B -->|Cloud Run 一直 Revision failed| F[Cloud Run 預期 listen on $PORT]

  C --> C1[gcloud auth configure-docker<br/>本地 push 用<br/>node SA 加 reader role]
  D --> D1{錯誤訊息<br/>關鍵字}
  D1 -->|Address already in use| F
  D1 -->|connection refused| G[找不到外部依賴:<br/>DB / API / Secret 連不到]
  D1 -->|out of memory / OOMKilled| H[--memory 太小<br/>或調 GC / 重看洩漏]
  D1 -->|missing env var| I[檢查 env / Secret Manager 注入]

  E --> E1[Insufficient cpu/memory:<br/>requests 太大 / 沒 node]
  E --> E2[Image pull failed:<br/>到 C]
  E --> E3[FailedScheduling:<br/>nodeSelector / taint 不符]

  F --> F1[程式必須 listen 0.0.0.0:$PORT<br/>不能寫死 8080]
  G --> G1[去 §3 網路問題]
```

## 3. 網路：連不到、timeout、502

```mermaid
flowchart TD
  A[A 服務連不到 B] --> B{B 在哪?}
  B -->|公開網際網路| C[A 有出網路嗎?<br/>外部 IP / Cloud NAT]
  B -->|GCP API<br/>GCS, Pub/Sub...| D[A 有外部 IP 嗎?]
  B -->|VPC 內 Cloud SQL| E[Private IP 還 Public IP?]
  B -->|VPC 內 GKE / GCE| F[Firewall / NetworkPolicy]

  C --> C1{有 NAT?}
  C1 -->|否| C2[加 Cloud NAT<br/>或開外部 IP]
  C1 -->|是| C3[檢查 firewall egress<br/>是否擋出向]

  D --> D1{有外部 IP?}
  D1 -->|是| D2[直接走 googleapis.com 應該通]
  D1 -->|否| D3[subnet 開<br/>Private Google Access]

  E --> E1{Private IP?}
  E1 -->|是| E2[Service Networking peering<br/>有沒有建好<br/>Pod CIDR 在 authorized range?]
  E1 -->|否| E3[Auth Proxy / Connector 用了沒]

  F --> F1[gcloud compute firewall-rules list<br/>找 source/target 對應]
  F --> F2[Network Intelligence Center<br/>Connectivity Tests]
```

### LB 收到 502 的判斷

```mermaid
flowchart TD
  A[LB 回 502] --> B{Backend health?}
  B -->|UNHEALTHY| C[health check path 對嗎?<br/>對 firewall 有放行<br/>130.211.0.0/22 + 35.191.0.0/16?]
  B -->|HEALTHY| D[Backend latency 高嗎?]
  D -->|是| E[Backend timeout > LB timeout<br/>或下游慢]
  D -->|否| F[Backend 主動 close connection<br/>看 backend logs]
```

## 4. 訊息收不到 / 重複處理（Pub/Sub）

```mermaid
flowchart TD
  A[訊息行為怪] --> B{症狀}
  B -->|Subscriber 沒收到| C[訊息發出去前<br/>subscription 就存在嗎?]
  B -->|訊息一直重送| D[看 oldest_unacked_message_age<br/>metric]
  B -->|訊息突然消失| E[message retention 過期?<br/>預設 7 天]
  B -->|Push subscription 4xx| F[OIDC token / audience<br/>後端有沒有驗?]

  C --> C1[Pub/Sub 不保留<br/>沒 sub 之前的訊息<br/>先建 sub 再 publish]
  D --> D1{ack_deadline 內<br/>有 ack 嗎?}
  D1 -->|沒有| D2[處理太久 / crash<br/>調大 ack_deadline<br/>或用 streaming pull 自動延長]
  D1 -->|有| D3[業務 idempotent 沒做好<br/>at-least-once 必有重複]
  E --> E1[拉長 retention 上限 31d<br/>或設 sink 到 GCS 備份]
  F --> F1[push endpoint 必須回 2xx<br/>且驗 X-Goog-... JWT 簽章]
```

## 5. 帳單異常 / 突然變貴

```mermaid
flowchart TD
  A[帳單看到 spike] --> B[去 Cloud Billing → Reports<br/>group by SKU]
  B --> C{最大 SKU 是?}
  C -->|BigQuery analysis| D[看 INFORMATION_SCHEMA.JOBS<br/>找最大 query]
  C -->|Network Inter-Region Egress| E[資源跨 region<br/>app/db/log/sink 對齊]
  C -->|Network Internet Egress| F[出網路:<br/>API call / CDN miss / log to outside]
  C -->|Cloud SQL CPU| G[沒 stop instance / HA 開著<br/>連線數爆]
  C -->|Compute / GKE| H[VM/cluster 沒人用還在開]
  C -->|Logging Volume| I[沒設 exclude<br/>debug log 整天寫]

  D --> D1[找出 query → 改加 partition filter<br/>用 maximum_bytes_billed]
  E --> E1[搬資源到同 region<br/>或設 egress alert]
  H --> H1[gcloud recommender:<br/>idle-instance recommender]
  I --> I1[加 exclusion filter<br/>例如健康檢查]
```

## 6. Terraform 鬼故事

```mermaid
flowchart TD
  A[Terraform 出錯] --> B{症狀}
  B -->|state lock 卡住| C[gcloud storage cat<br/>state 同層 .tflock 看 holder<br/>真的沒人在跑就 force-unlock]
  B -->|資源 destroy 失敗| D[依賴沒清乾淨<br/>bucket 有物件 / subnet 有 VM]
  B -->|API not enabled| E[漏了 google_project_service]
  B -->|drift| F[有人在 Console 改了<br/>terraform plan 顯示]
  B -->|provider 升版爆| G[checkout 上一版<br/>讀 release notes]

  C --> C1[terraform force-unlock LOCK_ID<br/>確定真的沒在跑才用!]
  D --> D1[force_destroy=true<br/>或先手動清 child resource]
  F --> F1[把 console 改的 terraform import<br/>或 plan 蓋回去]
```

## 7. 萬用第一招：開 Audit Logs

很多「為什麼會被擋」的問題在 Audit Logs 看得一清二楚：

```text
# Cloud Logging 查詢
protoPayload.authorizationInfo.granted=false
resource.type="<service-type>"
```

如果出現的是「**Data Read** / Data Write 找不到 log」——那是預設關閉的，先到 IAM → Audit Logs 開啟。

## 8. 還是搞不定？

1. **重現最小範例**：用 curl / 一行程式重現問題，去掉自家程式雜訊。
2. **檢查 quota**：很多「為什麼建不了」是 project quota 用完。Console → IAM & Admin → Quotas。
3. **去 [Issue Tracker](https://issuetracker.google.com/)** 看是不是 known issue。
4. **官方 docs 的 troubleshooting 區**：每個服務都有，例如 [Cloud Run troubleshooting](https://cloud.google.com/run/docs/troubleshooting)。
