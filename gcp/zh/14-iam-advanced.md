# IAM 進階

[01-fundamentals](./01-fundamentals.md) 講了 principal / role / resource 的基本模型。這篇處理「**正式環境會碰到的進階控制**」：條件式 IAM、組織政策、VPC-SC、自訂角色、SA 影分身。

## 1. IAM Conditions（條件式繫結）

普通 binding 是「永久、無條件」的；Conditions 讓你加上 **時間 / 資源屬性** 條件。

```bash
# Alice 只能在工作日 08:00–20:00 存取
gcloud projects add-iam-policy-binding PROJECT \
  --member="user:alice@example.com" \
  --role="roles/storage.objectAdmin" \
  --condition='expression=request.time.getHours("Asia/Taipei") >= 8 && request.time.getHours("Asia/Taipei") < 20,title=workhours'

# 只對 prefix 是 logs/ 的 bucket 生效
... --condition='expression=resource.name.startsWith("projects/_/buckets/logs-"),title=logs-only'
```

支援的屬性（取一些常用的）：

| 屬性 | 範例 |
| --- | --- |
| `request.time` | 時間視窗 |
| `resource.name` / `resource.type` | 限定 resource |
| `resource.matchTag(...)` | 用 GCP **Tags**（不是 labels）做標籤式控管 |
| `request.auth.claims.xxx` | OIDC claims（外部身份） |

> 不是所有 service 都支援 conditions；先到 docs 查 [conditions support matrix](https://cloud.google.com/iam/docs/conditions-resource-attributes)。

### Tags vs Labels

- **Labels**：純標籤、用於計費/搜尋。
- **Tags**：可以拿來做 IAM Conditions 與 Org Policy 的條件對象，**有權限模型**。

```bash
# 建立 tag key / value（在 Org 層）
gcloud resource-manager tags keys create env --parent=organizations/ORG_ID
gcloud resource-manager tags values create prod --parent=tagKeys/KEY_ID

# 把 tag 綁到一個 project
gcloud resource-manager tags bindings create \
  --tag-value=tagValues/VALUE_ID \
  --parent=//cloudresourcemanager.googleapis.com/projects/PROJECT_NUMBER
```

之後 IAM Condition 就可以用 `resource.matchTag('ORG_ID/env', 'prod')`。

## 2. Custom Role

預設 role 太大或不對位時自製：

```yaml
# role.yaml
title: "Bucket Lister"
description: "Only list buckets"
stage: GA
includedPermissions:
- storage.buckets.list
- storage.buckets.get
```

```bash
gcloud iam roles create bucketLister \
  --project=PROJECT --file=role.yaml
```

> 自訂 role 不能跨 project 共用（除非建在 Org 層）。維護成本高，**先試試 predefined role 組合**。

## 3. Service Account Impersonation（SA 影分身）

替代 SA key 的最佳實踐：用使用者帳號短暫扮演 SA。

```bash
# 1. 給某人「impersonate 該 SA」的權限
gcloud iam service-accounts add-iam-policy-binding \
  deployer@PROJECT.iam.gserviceaccount.com \
  --member="user:alice@example.com" \
  --role="roles/iam.serviceAccountTokenCreator"

# 2. Alice 直接以 SA 身份跑指令（不用 key）
gcloud storage ls gs://prod-bucket \
  --impersonate-service-account=deployer@PROJECT.iam.gserviceaccount.com

# 3. 要設成預設身份
gcloud config set auth/impersonate_service_account \
  deployer@PROJECT.iam.gserviceaccount.com
```

優點：

- 不需要產生 key 檔
- token 短期（~1 小時）
- 完整 audit trail：「Alice 透過 deployer SA 做了 X」

## 4. Workload Identity Federation（WIF）

讓**外部身份**（GitHub Actions、AWS、Azure、OIDC）直接呼叫 GCP，**不用 SA key**。

GitHub Actions 範例：

```bash
# 1. 建 Workload Identity Pool
gcloud iam workload-identity-pools create github-pool \
  --location=global --display-name="GitHub Actions"

# 2. 建 OIDC provider 指向 GitHub
gcloud iam workload-identity-pools providers create-oidc github \
  --location=global \
  --workload-identity-pool=github-pool \
  --display-name="GitHub OIDC" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-condition='assertion.repository_owner=="my-org"'

# 3. 把該 repo 的 workflow 綁到一個 SA
gcloud iam service-accounts add-iam-policy-binding \
  deployer@PROJECT.iam.gserviceaccount.com \
  --member="principalSet://iam.googleapis.com/projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/attribute.repository/my-org/my-repo" \
  --role="roles/iam.workloadIdentityUser"
```

GitHub Actions workflow：

```yaml
permissions:
  id-token: write
jobs:
  deploy:
    steps:
    - uses: google-github-actions/auth@v2
      with:
        workload_identity_provider: projects/NUM/locations/global/workloadIdentityPools/github-pool/providers/github
        service_account: deployer@PROJECT.iam.gserviceaccount.com
    - run: gcloud run deploy ...
```

從此 **GHA 完全不用塞 SA key 進 secret**。

## 5. Org Policy（組織政策）

組織層級的「**強制規則**」，子 project 不能繞過。

| 常用 constraint | 用途 |
| --- | --- |
| `iam.disableServiceAccountKeyCreation` | 禁止建 SA key |
| `iam.allowedPolicyMemberDomains` | 只准本公司網域被加 IAM |
| `compute.vmExternalIpAccess` | 禁止 VM 開外部 IP |
| `storage.publicAccessPrevention` | 禁止 GCS bucket 公開 |
| `compute.requireOsLogin` | 強制用 OS Login |
| `compute.restrictSharedVpcSubnetworks` | 限制可用的 Shared VPC subnet |
| `gcp.resourceLocations` | 限制資源建在哪些 region |

```bash
# 例：禁止建 SA key（整個 org）
gcloud resource-manager org-policies set-policy \
  --organization=ORG_ID \
  policy.yaml
# policy.yaml:
# constraint: constraints/iam.disableServiceAccountKeyCreation
# booleanPolicy: { enforced: true }
```

## 6. VPC Service Controls（VPC-SC）

把 GCP API（GCS、BQ、Pub/Sub…）關進**服務邊界（perimeter）**：

```
Perimeter = { project A, project B }
規則：
 - perimeter 內 ↔ perimeter 內：通
 - perimeter 內 → 外：擋
 - 外 → perimeter 內：擋（除非加 Access Level）
```

防的是「**內鬼或被 phish 的帳號把資料拉到自家專案**」這種威脅模型。

```bash
# 建 perimeter
gcloud access-context-manager perimeters create prod-perimeter \
  --policy=POLICY_ID \
  --title="Prod" \
  --resources=projects/PROJECT_NUMBER \
  --restricted-services=storage.googleapis.com,bigquery.googleapis.com
```

進階：用 **Access Levels**（IP / device / OS）允許特定條件下從 perimeter 外存取。

> VPC-SC 設定錯誤會 **整個 org 一起當機**——一定先 dry-run（`--dry-run`）跑幾天看 audit log。

## 7. Audit Logs

四種：

| 類型 | 預設 | 內容 |
| --- | --- | --- |
| Admin Activity | 永遠開、免費 | 寫操作（create/update/delete） |
| System Event | 永遠開、免費 | GCP 自己觸發的事件 |
| **Data Access** | **預設關**（除 BQ）| 讀取資料（GCS get、Secret access） |
| Policy Denied | 永遠開 | 被 IAM/Org Policy 擋下的請求 |

要查「誰讀過 secret」必須先**手動開** Data Access logs。

```text
Console → IAM → Audit Logs → 選 service → 勾 Data Read / Data Write
```

## 8. 健康檢查清單

實務上每個 GCP org 至少要做到：

- [ ] 禁止建 SA key（`iam.disableServiceAccountKeyCreation`）
- [ ] 禁止 VM 外部 IP（`compute.vmExternalIpAccess`）+ 用 IAP
- [ ] 禁 GCS 公開（`storage.publicAccessPrevention`）
- [ ] 限制 resource location（`gcp.resourceLocations`）
- [ ] 啟用相關服務的 Data Access audit log
- [ ] 所有 production access 都用 SA impersonation 或 WIF（不要產 SA key）
- [ ] 重要 service 放 VPC-SC perimeter

## 9. 常見坑

- **IAM Condition 寫錯沒擋下來**：condition 是「額外限制」，不會把不存在的權限變出來。錯打成 OR 邏輯就等於沒設。
- **Conditions 不被該服務支援**：許多較新的 API 沒支援，會被忽略。
- **Custom role 升級爆掉**：自訂 role 沒包到新加的 permission，新功能會失敗。儘量用 predefined。
- **WIF attribute condition 太鬆**：忘了限定 `assertion.repository_owner`，全 GitHub 都能換到 token。
- **Org policy 一刀切擋到自己**：例如禁外部 IP 後，舊 VM 開不起來。先 dry-run。
- **VPC-SC 鎖死**：管理員自己都被擋出去（連 Console 都進不去）。**永遠保留一個沒進 perimeter 的 break-glass 帳號**。
