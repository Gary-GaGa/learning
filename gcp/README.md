# GCP Learning Notes / GCP 學習筆記

Notes and runnable examples for common Google Cloud Platform services.
針對 Google Cloud Platform 常用服務的學習筆記與可執行範例。

Each topic follows a **concepts → commands → common pitfalls** structure.
每篇遵循「**概念 → 指令 → 常見坑**」的結構。

## Pick your language / 選擇語言

- **English** → [`en/README.md`](./en/README.md)
- **中文** → [`zh/README.md`](./zh/README.md)

## Start here / 從這裡開始

🗺️ **First time / 第一次看？** [`en/00-overview.md`](./en/00-overview.md) ｜ [`zh/00-overview.md`](./zh/00-overview.md) — topic map, glossary, learning paths.

🆘 **Stuck / 卡住了？** [`en/troubleshooting.md`](./en/troubleshooting.md) ｜ [`zh/troubleshooting.md`](./zh/troubleshooting.md) — decision trees for common issues.

## Topics covered / 收錄主題

| # | Topic / 主題 | EN | ZH |
| --- | --- | --- | --- |
| 00 | Overview / 總覽 | [en](./en/00-overview.md) | [zh](./zh/00-overview.md) |
| 01 | Fundamentals (IAM, projects, gcloud) / 基礎 | [en](./en/01-fundamentals.md) | [zh](./zh/01-fundamentals.md) |
| 02 | GKE | [en](./en/02-gke.md) | [zh](./zh/02-gke.md) |
| 03 | Cloud Storage | [en](./en/03-cloud-storage.md) | [zh](./zh/03-cloud-storage.md) |
| 04 | Pub/Sub | [en](./en/04-pubsub.md) | [zh](./zh/04-pubsub.md) |
| 05 | Cloud Run | [en](./en/05-cloud-run.md) | [zh](./zh/05-cloud-run.md) |
| 06 | BigQuery | [en](./en/06-bigquery.md) | [zh](./zh/06-bigquery.md) |
| 07 | Cloud SQL | [en](./en/07-cloud-sql.md) | [zh](./zh/07-cloud-sql.md) |
| 08 | Artifact Registry | [en](./en/08-artifact-registry.md) | [zh](./zh/08-artifact-registry.md) |
| 09 | VPC & Networking / VPC 與 Networking | [en](./en/09-vpc-networking.md) | [zh](./zh/09-vpc-networking.md) |
| 10 | Observability (Logging / Monitoring) / 觀測性 | [en](./en/10-observability.md) | [zh](./zh/10-observability.md) |
| 11 | Secret Manager & KMS / 敏感資料 | [en](./en/11-secret-manager-kms.md) | [zh](./zh/11-secret-manager-kms.md) |
| 12 | Compute Engine / GCE | [en](./en/12-compute-engine.md) | [zh](./zh/12-compute-engine.md) |
| 13 | Cost management / 成本管理 | [en](./en/13-cost-management.md) | [zh](./zh/13-cost-management.md) |
| 14 | IAM advanced / IAM 進階 | [en](./en/14-iam-advanced.md) | [zh](./zh/14-iam-advanced.md) |
| 15 | HTTPS Load Balancer | [en](./en/15-load-balancer.md) | [zh](./zh/15-load-balancer.md) |
| 16 | CI/CD (Cloud Build & Deploy) | [en](./en/16-cicd.md) | [zh](./zh/16-cicd.md) |
| 17 | Terraform / IaC | [en](./en/17-terraform.md) | [zh](./zh/17-terraform.md) |
| 🆘 | Troubleshooting | [en](./en/troubleshooting.md) | [zh](./zh/troubleshooting.md) |

## End-to-end demo / 端對端示範

[`demo/`](./demo/README.md) — an Orders mini-system wiring Cloud Run + Pub/Sub + GCS + Cloud SQL + Secret Manager + Artifact Registry, provisioned with Terraform.
[`demo/`](./demo/README.zh.md) — 串起 Cloud Run + Pub/Sub + GCS + Cloud SQL + Secret Manager + Artifact Registry 的訂單小系統，附 Terraform。
