# End-to-end demo: Orders mini-system

> Language: **English** ｜ [中文](./README.zh.md)

A minimal-but-real example that ties together six services from the topic notes. The goal is to show **how the pieces wire up**, not to be production-ready code.

## Architecture

```
                  ┌──────────────────────────────────────────┐
   client ──POST──►  Cloud Run: api                          │
                  │   1. write invoice → GCS bucket          │
                  │   2. publish event → Pub/Sub topic       │
                  └──────────────────────────────────────────┘
                                            │
                                Pub/Sub push subscription (OIDC)
                                            ▼
                  ┌──────────────────────────────────────────┐
                  │  Cloud Run: worker                       │
                  │   1. read DB password ← Secret Manager   │
                  │   2. INSERT order   →  Cloud SQL (PG)    │
                  └──────────────────────────────────────────┘
```

Services exercised:

- **Cloud Run** (api + worker)
- **Pub/Sub** (push subscription with OIDC auth)
- **Cloud Storage** (invoice files)
- **Cloud SQL** (Postgres, Private IP via Auth Proxy sidecar would be ideal — here we use the Cloud SQL Connector library for simplicity)
- **Secret Manager** (DB password injected as env)
- **Artifact Registry** (image storage)
- **Workload Identity / IAM** (services authenticate via runtime SAs, no keys)

## Layout

```
demo/
├── README.md              ← you are here
├── README.zh.md
├── api/                   ← order ingestion service
│   ├── Dockerfile
│   ├── main.py
│   └── requirements.txt
├── worker/                ← Pub/Sub-driven DB writer
│   ├── Dockerfile
│   ├── main.py
│   └── requirements.txt
├── terraform/             ← provisioning
│   ├── main.tf
│   ├── variables.tf
│   └── outputs.tf
└── deploy.sh              ← build + push images, set env vars
```

## Prerequisites

- A GCP project, billing enabled
- `gcloud`, `terraform`, `docker` installed locally
- `gcloud auth login` and `gcloud auth application-default login` done
- Set `PROJECT_ID` and pick a `REGION` (defaults to `asia-east1`)

## Running it

### 1. Set env vars

```bash
export PROJECT_ID=your-project-id
export REGION=asia-east1
```

### 2. Provision infra with Terraform

```bash
cd terraform
terraform init
terraform apply -var=project="$PROJECT_ID" -var=region="$REGION"
```

This creates:

- Artifact Registry repo `demo`
- GCS bucket `<project>-demo-invoices`
- Pub/Sub topic `demo-orders` and a push subscription `demo-orders-sub`
- Cloud SQL Postgres instance `demo-pg` + db `orders` + user `app`
- Secret Manager secret `demo-db-password`
- Two runtime service accounts (`demo-api`, `demo-worker`) with minimum IAM
- Cloud Run services `demo-api` and `demo-worker` (initially with `gcr.io/cloudrun/hello` placeholder image)

> First-time Cloud SQL creation takes 5–10 minutes.

### 3. Build and push images, redeploy

```bash
cd ..
./deploy.sh
```

`deploy.sh` will:

1. Build `api/` and `worker/` images
2. Push them to Artifact Registry
3. Update both Cloud Run services to use the new images

### 4. Try it

```bash
URL=$(gcloud run services describe demo-api --region="$REGION" --format="value(status.url)")

curl -X POST "$URL/orders" \
  -H "Content-Type: application/json" \
  -d '{"order_id":"A001","amount":120,"item":"book"}'
# → {"status":"accepted","invoice":"gs://.../invoices/A001.json"}
```

Check the worker logs and the DB:

```bash
gcloud run services logs read demo-worker --region="$REGION" --limit=20

# Connect via Cloud SQL Auth Proxy or Console SQL Studio:
#   SELECT * FROM orders;
```

### 5. Tear down

```bash
cd terraform
terraform destroy -var=project="$PROJECT_ID" -var=region="$REGION"
```

## What to study in this demo

| Topic | Where in code |
| --- | --- |
| Workload Identity / runtime SA | `terraform/main.tf` — `service_account =` on each Cloud Run, plus IAM bindings |
| Secret Manager mounting | `terraform/main.tf` — `volume_mounts` / `env { value_source { secret_key_ref ... }}` |
| Pub/Sub push + OIDC auth | `terraform/main.tf` — subscription `oidc_token { service_account_email }`, and `worker/main.py` verifies the JWT |
| Cloud SQL connection from Cloud Run | `worker/main.py` — uses `cloud-sql-python-connector` with IAM auth |
| GCS write from Cloud Run | `api/main.py` — `google-cloud-storage` with ADC |
| Per-service minimum IAM | `terraform/main.tf` — `pubsub.publisher` only on api SA, `cloudsql.client` only on worker SA |

## Caveats / "this is a demo"

- No tests, no migrations, no health checks, no graceful shutdown.
- `db_password` is generated random by Terraform — fine for demo, in real life rotate via Secret Manager rotation events.
- Cloud SQL is given a **public IP** with the connector for simplicity. Production: Private IP + Cloud SQL Auth Proxy sidecar, or Private Service Connect.
- No Cloud Build / Cloud Deploy; `deploy.sh` is the simplest possible CI. See [16-cicd.md](../en/16-cicd.md) to wire this into a pipeline.
- Single region. Production: align all resources to the same region; consider DR.
