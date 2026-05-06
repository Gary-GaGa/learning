# Cloud SQL

GCP 託管的關聯式資料庫，支援 **MySQL / PostgreSQL / SQL Server**。Google 幫你扛備份、patch、HA failover，你只要設定 instance 規格 + schema。

> 不要跟 **AlloyDB**（PostgreSQL 相容、效能更高、更貴）和 **Spanner**（全球分散式、強一致）混淆。

## 1. 選擇 instance 規格

| 維度 | 怎麼選 |
| --- | --- |
| Edition | `Enterprise`（一般）/ `Enterprise Plus`（更高效能、近 0 downtime maintenance） |
| 機型 | 由 vCPU / RAM 決定，可後期升級 |
| 儲存 | SSD（建議）/ HDD（極少數場景）；可開 **automatic storage increase** |
| HA | Regional（同 region 跨 zone failover）/ Zonal（單 zone，便宜但無 HA） |

> Production：**永遠開 HA**。Dev/test：用 zonal 省錢。

## 2. 建立 instance

### PostgreSQL 範例

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

### 建 db / user

```bash
gcloud sql databases create app_db --instance=demo-pg
gcloud sql users create app_user --instance=demo-pg --password='strong-pass'
```

## 3. 連線方式（最容易踩雷的部分）

| 方式 | 適合 | 安全性 |
| --- | --- | --- |
| Public IP + Authorized Networks | 從家裡測試 | 低（暴露在公網） |
| Public IP + **Cloud SQL Auth Proxy** | 本機開發 | 高（IAM + TLS） |
| **Private IP**（VPC peering） | GKE / Cloud Run / GCE 在同 VPC | 高 |
| **Cloud SQL Connector**（Go/Java/Python lib） | 應用內建連線 | 高 |

### Auth Proxy（本機 dev 推薦）

```bash
# 下載 cloud-sql-proxy（一次性）
curl -o cloud-sql-proxy https://storage.googleapis.com/cloud-sql-connectors/cloud-sql-proxy/v2.11.0/cloud-sql-proxy.linux.amd64
chmod +x cloud-sql-proxy

# 起 proxy（用你的 gcloud 身份）
./cloud-sql-proxy PROJECT:asia-east1:demo-pg --port=5432 &

# 然後一般 client 連 localhost
psql -h 127.0.0.1 -p 5432 -U app_user -d app_db
```

連線就走 IAM + TLS，不需要白名單 IP。

### Private IP + GKE

```bash
# 1. 開 instance 時加 Private IP
gcloud sql instances patch demo-pg \
  --network=projects/PROJECT/global/networks/default \
  --no-assign-ip
# （需要先開 Service Networking peering：
#  gcloud services vpc-peerings connect ...，第一次做時 Console 會提示）

# 2. GKE 走 VPC 直接連 Private IP（10.x.x.x）
```

### IAM 資料庫驗證（不用密碼）

PostgreSQL / MySQL 都支援讓 IAM SA 直接登入，不用管 db user 密碼：

```bash
gcloud sql instances patch demo-pg \
  --database-flags=cloudsql.iam_authentication=on

# 把某個 SA 加成 db user
gcloud sql users create app-sa@PROJECT.iam \
  --instance=demo-pg --type=cloud_iam_service_account
```

之後在 GKE Pod 用 Workload Identity 登入時，直接用 SA email 當 username，token 當密碼，proxy / connector 自動處理。

## 4. 備份與還原

```bash
# 手動備份
gcloud sql backups create --instance=demo-pg --description="before migration"

# 列備份
gcloud sql backups list --instance=demo-pg

# 還原到同一個 instance
gcloud sql backups restore BACKUP_ID --restore-instance=demo-pg

# Point-in-time recovery（要先開啟 PITR）
gcloud sql instances clone demo-pg demo-pg-clone \
  --point-in-time='2026-05-05T10:30:00.000Z'
```

> **備份不等於 PITR**。PITR 需要開 `--enable-point-in-time-recovery`，靠 binlog/WAL 重播，可以還原到秒級時間點。

## 5. Read Replica

```bash
gcloud sql instances create demo-pg-replica \
  --master-instance-name=demo-pg \
  --region=asia-east1 \
  --tier=db-custom-2-7680
```

- 用於分散讀流量、跨 region disaster recovery、做分析查詢避免影響 primary。
- 寫入仍只能寫 primary。
- **跨 region replica**：開另一個 region 的 replica 可作為 DR；primary 死掉時手動 promote。

## 6. 觀測

- **Cloud Monitoring**：`Cloud SQL Database` 預設有 CPU / Mem / Connections / Replication lag 指標。
- **Query Insights**：Console → Cloud SQL → 該 instance → Query insights，看慢查詢、p99 latency。
- **Logs**：error log、slow query log 都進 Cloud Logging。

## 7. 清理

```bash
# 注意：Cloud SQL 預設開「deletion protection」，要先關掉
gcloud sql instances patch demo-pg --no-deletion-protection
gcloud sql instances delete demo-pg
```

## 8. 常見坑

- **直接用 Public IP**：忘了關白名單 / 用簡單密碼 → 被掃進來。一律 Auth Proxy 或 Private IP。
- **連線數爆掉**：每個 Cloud SQL instance 連線數有上限，且 PostgreSQL 一個連線吃 ~10MB RAM。**前面要放 connection pool**（PgBouncer、應用內 pool）。
- **Maintenance window 沒設**：Google 會在他選的時間幫你 patch，可能正好打到上班時間。`--maintenance-window-day` 設成你的離峰時段。
- **storage 自動增長後縮不回去**：Cloud SQL 儲存只增不減。設 alert 在 80%。
- **沒備份就刪 instance**：Cloud SQL instance 刪掉同時備份也會被刪。重要資料先 export 到 GCS：
  ```bash
  gcloud sql export sql demo-pg gs://YOUR-BUCKET/dump.sql.gz --database=app_db
  ```
- **跨 region 連線延遲**：app 跟 db 要在同一個 region。
