# Cloud SQL

GCP managed relational database, supporting **MySQL / PostgreSQL / SQL Server**. Google handles backups, patching, and HA failover; you choose the instance shape and write the schema.

> Don't confuse with **AlloyDB** (PostgreSQL-compatible, faster, pricier) or **Spanner** (globally-distributed, strongly-consistent).

## 1. Choosing instance specs

| Dimension | How to choose |
| --- | --- |
| Edition | `Enterprise` (default) / `Enterprise Plus` (higher perf, near-zero-downtime maintenance) |
| Machine type | vCPU / RAM; can be resized later |
| Storage | SSD (recommended) / HDD (rare); enable **automatic storage increase** |
| HA | Regional (multi-zone failover) / Zonal (single-zone, cheaper, no HA) |

> Production: **always enable HA**. Dev/test: zonal to save cost.

## 2. Create an instance

### PostgreSQL example

```bash
gcloud sql instances create demo-pg \
  --database-version=POSTGRES_15 \
  --region=asia-east1 \
  --tier=db-custom-2-7680 \         # 2 vCPU, 7.5GB RAM
  --availability-type=REGIONAL \    # HA
  --storage-type=SSD \
  --storage-size=20GB \
  --storage-auto-increase \
  --backup-start-time=18:00 \
  --enable-point-in-time-recovery \
  --root-password='change-me-strong'
```

### Create db / user

```bash
gcloud sql databases create app_db --instance=demo-pg
gcloud sql users create app_user --instance=demo-pg --password='strong-pass'
```

## 3. Connecting (the most error-prone part)

| Method | Best for | Security |
| --- | --- | --- |
| Public IP + Authorized Networks | Quick test from your laptop | Low (exposed to internet) |
| Public IP + **Cloud SQL Auth Proxy** | Local development | High (IAM + TLS) |
| **Private IP** (VPC peering) | GKE / Cloud Run / GCE in same VPC | High |
| **Cloud SQL Connector** (Go/Java/Python lib) | App-embedded connection | High |

### Auth Proxy (recommended for local dev)

```bash
# One-time download
curl -o cloud-sql-proxy https://storage.googleapis.com/cloud-sql-connectors/cloud-sql-proxy/v2.11.0/cloud-sql-proxy.linux.amd64
chmod +x cloud-sql-proxy

# Run proxy with your gcloud identity
./cloud-sql-proxy PROJECT:asia-east1:demo-pg --port=5432 &

# Connect normally
psql -h 127.0.0.1 -p 5432 -U app_user -d app_db
```

Connection goes through IAM + TLS — no IP allowlist needed.

### Private IP + GKE

```bash
# 1. Add Private IP to instance
gcloud sql instances patch demo-pg \
  --network=projects/PROJECT/global/networks/default \
  --no-assign-ip
# (Requires Service Networking peering enabled first;
#  Console will prompt the first time.)

# 2. GKE pods reach Private IP (10.x.x.x) directly via VPC.
```

### IAM database authentication (no password)

PostgreSQL / MySQL support IAM SAs as DB users — no password management:

```bash
gcloud sql instances patch demo-pg \
  --database-flags=cloudsql.iam_authentication=on

# Add an SA as DB user
gcloud sql users create app-sa@PROJECT.iam \
  --instance=demo-pg --type=cloud_iam_service_account
```

Then GKE pods using Workload Identity log in with the SA email as username and the token as password — proxy/connector handles it automatically.

## 4. Backup and restore

```bash
# Manual backup
gcloud sql backups create --instance=demo-pg --description="before migration"

# List backups
gcloud sql backups list --instance=demo-pg

# Restore into the same instance
gcloud sql backups restore BACKUP_ID --restore-instance=demo-pg

# Point-in-time recovery (PITR must be enabled)
gcloud sql instances clone demo-pg demo-pg-clone \
  --point-in-time='2026-05-05T10:30:00.000Z'
```

> **Backups ≠ PITR.** PITR needs `--enable-point-in-time-recovery` and replays binlog/WAL — restore to second-level precision.

## 5. Read replicas

```bash
gcloud sql instances create demo-pg-replica \
  --master-instance-name=demo-pg \
  --region=asia-east1 \
  --tier=db-custom-2-7680
```

- Use for read traffic distribution, cross-region DR, or analytical queries that shouldn't hit primary.
- Writes still only go to primary.
- **Cross-region replicas**: a replica in another region acts as DR; promote manually if primary dies.

## 6. Observability

- **Cloud Monitoring**: `Cloud SQL Database` metrics — CPU / memory / connections / replication lag.
- **Query Insights**: Console → Cloud SQL → instance → Query insights — slow queries, p99 latency.
- **Logs**: error logs, slow query logs are pushed to Cloud Logging.

## 7. Cleanup

```bash
# Note: deletion protection is on by default
gcloud sql instances patch demo-pg --no-deletion-protection
gcloud sql instances delete demo-pg
```

## 8. Common pitfalls

- **Public IP exposed**: weak password + open allowlist = compromised. Always Auth Proxy or Private IP.
- **Connection storms**: each Cloud SQL instance has a connection limit; PostgreSQL connections cost ~10MB RAM each. **Put a connection pool in front** (PgBouncer, app-side pool).
- **Maintenance window not set**: Google patches at a time of their choosing, possibly during your peak hours. Set `--maintenance-window-day` to your off-peak.
- **Storage only grows**: Cloud SQL storage never shrinks. Alert at 80%.
- **Delete instance, lose backups**: deleting the instance removes its backups. Export to GCS first:
  ```bash
  gcloud sql export sql demo-pg gs://YOUR-BUCKET/dump.sql.gz --database=app_db
  ```
- **Cross-region latency**: keep app and DB in the same region.
