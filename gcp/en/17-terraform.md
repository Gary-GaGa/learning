# Terraform / IaC on GCP

After 16 topics, you'll see that hand-running `gcloud` for 5+ services is unsustainable. **Production needs IaC.** Terraform is the most universal choice on GCP.

> Alternatives: **Config Connector** (manage GCP via K8s CRDs in GKE), **Pulumi**, **Deployment Manager** (deprecated). This topic focuses on Terraform.

## 1. Why IaC

| Pain (manual) | IaC fix |
| --- | --- |
| "What's different in prod again?" | All in git |
| "Cloning to another region from scratch" | Change a variable |
| "New engineer needs a week to learn the setup" | Read the `.tf` |
| "Who changed this and why?" | git blame + PR review |

## 2. Setup

### 2.1 Install

```bash
brew install terraform   # macOS; other platforms: https://developer.hashicorp.com/terraform/downloads
terraform -version
```

### 2.2 Auth (provider identity)

Best practice — **SA impersonation, no keys**:

```bash
# Local dev: your account impersonates a deployer SA
gcloud auth application-default login
gcloud config set auth/impersonate_service_account \
  tf-runner@PROJECT.iam.gserviceaccount.com
```

In CI: use [WIF](./14-iam-advanced.md#4-workload-identity-federation-wif) to mint tokens.

## 3. State backend: always GCS

**Don't keep `terraform.tfstate` locally or in git** — it contains plaintext secrets and needs locking.

```hcl
# backend.tf
terraform {
  required_version = ">= 1.6"
  required_providers {
    google = { source = "hashicorp/google", version = "~> 5.0" }
  }
  backend "gcs" {
    bucket = "my-tfstate-bucket"
    prefix = "prod"           # one prefix per state
  }
}
```

Bootstrap the state bucket once manually:

```bash
gcloud storage buckets create gs://my-tfstate-bucket \
  --location=asia-east1 \
  --uniform-bucket-level-access \
  --public-access-prevention
gcloud storage buckets update gs://my-tfstate-bucket --versioning   # safety net
```

## 4. Provider config

```hcl
# provider.tf
provider "google" {
  project = var.project
  region  = var.region
}

variable "project" { type = string }
variable "region"  { type = string  default = "asia-east1" }
```

## 5. Example: full stack

`main.tf` — Artifact Registry + Cloud Run + Pub/Sub wired together:

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

Run:

```bash
terraform init
terraform plan -var=project=my-project
terraform apply -var=project=my-project
```

## 6. Multi-environment

Two mainstream approaches:

### 6.1 Workspaces (simple)

```bash
terraform workspace new dev
terraform workspace new prod

# Switch via terraform.workspace in .tf
locals {
  is_prod = terraform.workspace == "prod"
}
```

**Downside**: env differences hide in `locals`, hard to read. **Only good for small differences.**

### 6.2 Per-env directory + shared module (recommended)

```
infra/
├── modules/
│   └── service/             ← reusable module
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

One state per env, isolated plan/apply, clean PR diffs.

## 7. Module example

`modules/service/main.tf`:

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

Use:

```hcl
module "hello" {
  source = "../../modules/service"
  name   = "hello"
  image  = "asia-east1-docker.pkg.dev/${var.project}/my-images/hello:v1"
  region = var.region
  env_vars = { LOG_LEVEL = "info" }
}
```

## 8. CI/CD integration

GitHub Actions (WIF, no keys):

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

`apply` runs in a separate workflow on merge to main.

## 9. Config Connector (KCC)

If you're already on GKE, manage GCP resources via K8s CRDs:

```yaml
apiVersion: pubsub.cnrm.cloud.google.com/v1beta1
kind: PubSubTopic
metadata:
  name: orders
  namespace: my-app
```

`kubectl apply` and KCC's controller creates the Pub/Sub topic. **Pro**: one toolchain with K8s. **Con**: requires GKE.

## 10. Common pitfalls

- **No state lock + concurrent applies**: GCS backend handles locking automatically. Don't put state locally or on S3 without locking.
- **Accidentally destroyed state**: always `plan` before `destroy`, and enable versioning + soft delete on the state bucket.
- **Provider major version surprises**: pin with `~> 5.0`; read release notes before upgrading.
- **`for_each` vs `count`**: prefer `for_each`; deleting an element in the middle doesn't reorder others.
- **Secrets in `.tf`**: use `data "google_secret_manager_secret_version"` to pull from Secret Manager, never commit.
- **Module sprawl**: keep module interfaces small (few inputs / outputs); avoid cross-module pass-through.
- **Drift (someone clicked in console)**: `terraform plan` shows drift. Use Org Policy to only let `tf-runner` SA modify prod.
- **Can't destroy**: usually a dependency isn't clean (bucket has objects, subnet has VMs). Read the error.
