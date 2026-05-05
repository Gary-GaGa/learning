# GCP Learning Notes

> Language: [中文](../zh/README.md) ｜ **English**

Notes and runnable examples for common Google Cloud Platform services. Each topic follows a "concepts → commands → common pitfalls" structure.

## Contents

| # | Topic | Link |
| --- | --- | --- |
| 01 | GCP fundamentals (projects, IAM, billing, `gcloud` CLI) | [01-fundamentals.md](./01-fundamentals.md) |
| 02 | GKE (Google Kubernetes Engine) | [02-gke.md](./02-gke.md) |
| 03 | Cloud Storage (GCS) | [03-cloud-storage.md](./03-cloud-storage.md) |
| 04 | Pub/Sub | [04-pubsub.md](./04-pubsub.md) |
| 05 | Cloud Run (serverless containers) | [05-cloud-run.md](./05-cloud-run.md) |
| 06 | BigQuery (data warehouse) | [06-bigquery.md](./06-bigquery.md) |
| 07 | Cloud SQL (managed RDB) | [07-cloud-sql.md](./07-cloud-sql.md) |
| 08 | Artifact Registry (image / package registry) | [08-artifact-registry.md](./08-artifact-registry.md) |
| 09 | VPC and Networking | [09-vpc-networking.md](./09-vpc-networking.md) |
| 10 | Observability (Cloud Logging, Monitoring) | [10-observability.md](./10-observability.md) |
| 11 | Secret Manager and Cloud KMS | [11-secret-manager-kms.md](./11-secret-manager-kms.md) |
| 12 | Compute Engine (GCE / VMs) | [12-compute-engine.md](./12-compute-engine.md) |
| 13 | Cost management (FinOps) | [13-cost-management.md](./13-cost-management.md) |
| 14 | IAM advanced (Conditions, Org Policy, VPC-SC, WIF) | [14-iam-advanced.md](./14-iam-advanced.md) |
| 15 | HTTPS Load Balancer (deep dive) | [15-load-balancer.md](./15-load-balancer.md) |
| 16 | CI/CD: Cloud Build & Cloud Deploy | [16-cicd.md](./16-cicd.md) |
| 17 | Terraform / IaC | [17-terraform.md](./17-terraform.md) |

**End-to-end demo**: [`demo/`](../demo/README.md) — an "orders" mini-system wiring Cloud Run + Pub/Sub + GCS + Cloud SQL + Secret Manager + Artifact Registry + Terraform. Runnable with `terraform apply`.

## Setup

All examples assume you have:

1. Installed the [Google Cloud SDK](https://cloud.google.com/sdk/docs/install) (provides `gcloud`, `gsutil`).
2. Logged in and set your default project:

   ```bash
   gcloud auth login
   gcloud auth application-default login   # ADC for SDK / Terraform
   gcloud config set project YOUR_PROJECT_ID
   gcloud config set compute/region asia-east1
   gcloud config set compute/zone   asia-east1-b
   ```

3. Enabled the APIs you'll use (enable as needed, not all at once):

   ```bash
   gcloud services enable \
     container.googleapis.com \
     storage.googleapis.com \
     pubsub.googleapis.com \
     artifactregistry.googleapis.com
   ```

## Suggested learning path

1. Start with **01-fundamentals**: understand projects, IAM, and billing units, so you don't accidentally lose resources or get hit with a surprise bill.
2. Next, **03-cloud-storage**: simplest service; get comfortable with `gcloud storage` and IAM bindings.
3. Then **04-pubsub**: learn the async message model, push vs pull.
4. **08-artifact-registry**: learn to push images — needed for Cloud Run and GKE.
5. Want to run a service without managing a cluster → **05-cloud-run**; want full Kubernetes → **02-gke**.
6. Need a database → **07-cloud-sql**; need analytics → **06-bigquery**.
7. Advanced: **09-vpc-networking** — put all of the above into a private network, learn firewalls, NAT, Shared VPC.
8. Before production: **10-observability** (so you can see problems) and **11-secret-manager-kms** (don't push passwords to git).
9. **12-compute-engine**: a VM is what underpins GKE nodes and Cloud SQL — understand it.
10. Operating maturity: **13-cost-management** (don't blow the budget), **14-iam-advanced** (enterprise-grade permissions), **15-load-balancer** (full recipe for an external website).
11. Automation: **16-cicd** (Cloud Build / Cloud Deploy / GitHub Actions) → **17-terraform** (IaC, **mandatory**).
12. Tie it all together: walk through the [`demo/`](../demo/README.md) end-to-end project.

## Cost warning

> Before experimenting, **set a budget alert**. GKE Standard charges continuously (control plane + node VMs) even when idle; Cloud Storage egress and Pub/Sub message volume can both spike unexpectedly.

```bash
# Console → Billing → Budgets & alerts → e.g. $20/month, alert at 80%
```
