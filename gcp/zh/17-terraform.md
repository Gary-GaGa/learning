# Terraform / IaC on GCP

學完前 16 篇你會發現：手敲 `gcloud` 管 5+ 服務不可持續。**Production 一定要 IaC**。Terraform 是 GCP 上最通用的選擇。

> 替代方案：**Config Connector**（在 GKE 裡用 K8s CRD 管 GCP）、**Pulumi**、**Deployment Manager**（已不推薦）。本篇以 Terraform 為主。

## 1. 為什麼要 IaC

| 痛點（手敲） | IaC 解法 |
| --- | --- |
| 「prod 環境多了什麼設定我不記得」 | 全部寫在 git 裡 |
| 「複製到另一 region 要重來一遍」 | 改 variable 即可 |
| 「下個工程師接手要學一週」 | 看 `.tf` 就懂 |
| 「誰改的、為什麼改」 | git blame + PR review |

## 2. 環境設定

### 2.1 安裝

```bash
brew install terraform   # macOS；其他平台見 https://developer.hashicorp.com/terraform/downloads
terraform -version
```

### 2.2 認證（Provider 用什麼身份）

最佳實踐——**SA impersonation**，不要產 key：

```bash
# 本機開發：你的帳號 impersonate 一個 deployer SA
gcloud auth application-default login
gcloud config set auth/impersonate_service_account \
  tf-runner@PROJECT.iam.gserviceaccount.com
```

CI 跑：用 [WIF](./14-iam-advanced.md#4-workload-identity-federationwif) 換到 token。

## 3. State backend：永遠用 GCS

**不要把 `terraform.tfstate` 放在本機或 git**——state 含密碼明文且需要 lock。

```hcl
# backend.tf
terraform {
  required_version = ">= 1.6"
  required_providers {
    google = { source = "hashicorp/google", version = "~> 5.0" }
  }
  backend "gcs" {
    bucket = "my-tfstate-bucket"
    prefix = "prod"           # 一個 prefix 一個 state
  }
}
```

先手動建 state bucket（一次性）：

```bash
gcloud storage buckets create gs://my-tfstate-bucket \
  --location=asia-east1 \
  --uniform-bucket-level-access \
  --public-access-prevention
gcloud storage buckets update gs://my-tfstate-bucket --versioning   # 安全網
```

## 4. Provider 設定

```hcl
# provider.tf
provider "google" {
  project = var.project
  region  = var.region
}

variable "project" { type = string }
variable "region"  { type = string  default = "asia-east1" }
```

## 5. 範例：完整 stack

`main.tf` — 建 Artifact Registry、Cloud Run、Pub/Sub、把它們串起來：

```hcl
resource "google_artifact_registry_repository" "images" {
  location      = var.region
  repository_id = "my-images"
  format        = "DOCKER"
}

resource "google_pubsub_topic" "orders" {
  name = "orders"
}

resource "google_service_account" "run_sa" {
  account_id   = "hello-runner"
  display_name = "Cloud Run runtime SA"
}

resource "google_pubsub_topic_iam_member" "run_can_publish" {
  topic  = google_pubsub_topic.orders.name
  role   = "roles/pubsub.publisher"
  member = "serviceAccount:${google_service_account.run_sa.email}"
}

resource "google_cloud_run_v2_service" "hello" {
  name     = "hello"
  location = var.region

  template {
    service_account = google_service_account.run_sa.email
    containers {
      image = "${var.region}-docker.pkg.dev/${var.project}/${google_artifact_registry_repository.images.repository_id}/hello:latest"
      env {
        name  = "TOPIC"
        value = google_pubsub_topic.orders.id
      }
    }
  }
}

resource "google_cloud_run_v2_service_iam_member" "public" {
  name     = google_cloud_run_v2_service.hello.name
  location = google_cloud_run_v2_service.hello.location
  role     = "roles/run.invoker"
  member   = "allUsers"
}

output "url" {
  value = google_cloud_run_v2_service.hello.uri
}
```

執行：

```bash
terraform init
terraform plan -var=project=my-project
terraform apply -var=project=my-project
```

## 6. 多環境管理

兩種主流做法：

### 6.1 Workspaces（簡單）

```bash
terraform workspace new dev
terraform workspace new prod

# 在 .tf 用 terraform.workspace 切換
locals {
  is_prod = terraform.workspace == "prod"
}
```

**缺點**：環境差異藏在 `locals`，不直觀。**只適合小差異**。

### 6.2 各自目錄 + 共用 module（推薦）

```
infra/
├── modules/
│   └── service/             ← 可重用 module
│       ├── main.tf
│       ├── variables.tf
│       └── outputs.tf
└── envs/
    ├── dev/
    │   ├── backend.tf       ← prefix=dev
    │   └── main.tf          ← module "service" { ... }
    ├── staging/
    └── prod/
```

每個 env 一個 state、各自 plan/apply、PR diff 清楚。

## 7. Module 範例

`modules/service/main.tf`：

```hcl
variable "name"     { type = string }
variable "image"    { type = string }
variable "region"   { type = string }
variable "env_vars" { type = map(string)  default = {} }

resource "google_cloud_run_v2_service" "this" {
  name     = var.name
  location = var.region
  template {
    containers {
      image = var.image
      dynamic "env" {
        for_each = var.env_vars
        content {
          name  = env.key
          value = env.value
        }
      }
    }
  }
}

output "url" { value = google_cloud_run_v2_service.this.uri }
```

呼叫：

```hcl
module "hello" {
  source = "../../modules/service"
  name   = "hello"
  image  = "asia-east1-docker.pkg.dev/${var.project}/my-images/hello:v1"
  region = var.region
  env_vars = { LOG_LEVEL = "info" }
}
```

## 8. 與 CI/CD 整合

GitHub Actions（用 WIF，無 key）：

```yaml
name: terraform
on: [pull_request]
permissions:
  id-token: write
  contents: read
  pull-requests: write
jobs:
  plan:
    runs-on: ubuntu-latest
    defaults: { run: { working-directory: infra/envs/dev } }
    steps:
    - uses: actions/checkout@v4
    - uses: google-github-actions/auth@v2
      with:
        workload_identity_provider: projects/NUM/locations/global/workloadIdentityPools/github-pool/providers/github
        service_account: tf-runner@PROJECT.iam.gserviceaccount.com
    - uses: hashicorp/setup-terraform@v3
    - run: terraform init
    - run: terraform plan -no-color -var=project=my-project | tee plan.txt
    - uses: actions/github-script@v7
      with:
        script: |
          const fs = require('fs');
          const body = '```\n' + fs.readFileSync('infra/envs/dev/plan.txt', 'utf8') + '\n```';
          github.rest.issues.createComment({
            issue_number: context.issue.number,
            owner: context.repo.owner,
            repo: context.repo.repo,
            body
          });
```

`apply` 在 main branch 合併後跑（單獨 workflow）。

## 9. Config Connector（KCC）

如果你已經在用 GKE，可以直接在 cluster 裡用 CRD 管 GCP：

```yaml
apiVersion: pubsub.cnrm.cloud.google.com/v1beta1
kind: PubSubTopic
metadata:
  name: orders
  namespace: my-app
```

`kubectl apply` 之後 KCC controller 會建立對應的 Pub/Sub topic。**好處**：跟 K8s 工具鏈一致；**壞處**：需要先有 GKE。

## 10. 常見坑

- **State 沒 lock 兩人同時 apply**：GCS backend 自動處理 lock，但別把 state 放本機或 S3 沒設 lock。
- **State 誤刪**：`terraform destroy` 之前一定 `plan`、且 state bucket 開 versioning + soft delete。
- **Provider 升大版被坑**：`~> 5.0` pin major version，升版前讀 release notes。
- **`for_each` vs `count`**：能用 `for_each` 就用，刪除中間元素不會打亂順序。
- **把 secret 寫進 .tf**：用 `data "google_secret_manager_secret_version"` 從 Secret Manager 拉，不要 commit。
- **跨 module 引用變得難維護**：保持 module 介面小（input/output 數量少），不要互相穿透。
- **drift（手動改了 console）**：`terraform plan` 會顯示 drift。建議用 Org Policy 限制只准 tf-runner SA 改 prod resource。
- **資源刪不掉**：常見是有依賴沒清乾淨（例如 bucket 有物件、subnet 上還有 VM）。`terraform destroy` 失敗時讀錯誤訊息。
