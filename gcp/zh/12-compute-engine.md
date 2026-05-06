# Compute Engine（GCE）

GCP 最基礎的 IaaS：開 VM。雖然新專案多半直上 Cloud Run / GKE，但**理解 GCE 是理解上層服務的基礎**——GKE node 是 GCE VM、Cloud SQL 底下也是 GCE。

## 1. 核心物件

```
Instance（VM）
├── Machine type (e2-small, n2-standard-4, c3-highmem-22 ...)
├── Image / Source（OS image 或 disk snapshot）
├── Boot disk + 額外 disks（Persistent Disk / Hyperdisk / Local SSD）
├── Network interface (VPC + subnet, internal IP, external IP?)
├── Service account（決定 VM 自身的 GCP 身份）
├── Metadata（startup-script, ssh-keys ...）
└── Labels / Tags（給 firewall / 計費分類用）
```

## 2. 機型家族

| 系列 | 適合 |
| --- | --- |
| `e2-*` | 一般用途、便宜、共享 CPU 起跳 |
| `n2-*` / `n2d-*` | 平衡型，多數 prod workload |
| `c3-*` / `c3d-*` | 高 CPU 效能（Intel / AMD） |
| `m3-*` / `m2-*` | 高記憶體（in-memory DB、SAP HANA） |
| `a2-*` / `g2-*` | GPU（A2 = A100、G2 = L4） |
| `t2d-*` | Arm（便宜、適合 web workload） |

**Spot / Preemptible**：最多省 70~91% 費用，但 GCP 隨時可砍（最多跑 24h）。適合可重試的批次工作。

## 3. 開一台 VM

```bash
gcloud compute instances create demo-vm \
  --zone=asia-east1-b \
  --machine-type=e2-small \
  --image-family=debian-12 \
  --image-project=debian-cloud \
  --network=prod-vpc --subnet=prod-tw \
  --no-address \                      # 不要外部 IP（搭配 Cloud NAT）
  --service-account=app-vm@PROJECT.iam.gserviceaccount.com \
  --scopes=cloud-platform \
  --tags=ssh-allowed,http-server \
  --metadata=enable-oslogin=TRUE \
  --metadata-from-file=startup-script=startup.sh
```

`startup.sh`（每次開機都會跑）：

```bash
#!/bin/bash
apt-get update && apt-get install -y nginx
systemctl enable --now nginx
```

## 4. SSH（建議走 IAP，不要開外部 SSH）

```bash
# 透過 IAP TCP forwarding（VM 不需外部 IP）
gcloud compute ssh demo-vm --zone=asia-east1-b --tunnel-through-iap

# 給某人 SSH 權限（不需要管 ~/.ssh/authorized_keys）
gcloud projects add-iam-policy-binding PROJECT \
  --member=user:alice@example.com \
  --role=roles/compute.osLogin
gcloud projects add-iam-policy-binding PROJECT \
  --member=user:alice@example.com \
  --role=roles/iap.tunnelResourceAccessor
```

> **OS Login** + **IAP**：金鑰由 GCP 帳號管理，audit log 看得到誰登入過，不需要開 22 port 對外。

## 5. Persistent Disk（資料碟）

```bash
# 建一顆 100GB SSD
gcloud compute disks create data-disk \
  --zone=asia-east1-b --size=100GB --type=pd-ssd

# 掛載
gcloud compute instances attach-disk demo-vm \
  --disk=data-disk --zone=asia-east1-b

# 進 VM 內 mkfs + mount
gcloud compute ssh demo-vm --zone=asia-east1-b -- "
  sudo mkfs.ext4 -F /dev/disk/by-id/google-data-disk
  sudo mkdir -p /data
  sudo mount /dev/disk/by-id/google-data-disk /data
"
```

| 類型 | 適合 |
| --- | --- |
| `pd-standard` | 冷資料、日誌 |
| `pd-balanced` | 一般 workload（推薦預設） |
| `pd-ssd` | 高 IOPS DB、低延遲 |
| `pd-extreme` / `Hyperdisk` | 高效能（自選 IOPS / 吞吐） |
| Local SSD | 暫時性、極高效能（VM 砍掉資料就沒了） |

## 6. Snapshot 與映像

```bash
# Snapshot（增量，可跨 region 還原）
gcloud compute snapshots create boot-snap-$(date +%s) \
  --source-disk=demo-vm --source-disk-zone=asia-east1-b

# 用 snapshot 還原成新 disk
gcloud compute disks create restored \
  --zone=asia-east1-b --source-snapshot=boot-snap-XXX
```

可以設 **Snapshot Schedule**（每日自動 snapshot + 保留 N 天）。

## 7. Managed Instance Group（MIG）

要做「自動擴展、自動修復、滾動更新」就用 MIG，不要自己手開 VM。

```bash
# 1. 建 instance template（VM 規格藍圖）
gcloud compute instance-templates create web-tpl \
  --machine-type=e2-small \
  --image-family=debian-12 --image-project=debian-cloud \
  --metadata-from-file=startup-script=startup.sh \
  --tags=http-server

# 2. 建 MIG，跨 zone
gcloud compute instance-groups managed create web-mig \
  --base-instance-name=web \
  --template=web-tpl \
  --size=3 \
  --region=asia-east1

# 3. 加 health check + auto-healing
gcloud compute health-checks create http web-hc --request-path=/healthz --port=80
gcloud compute instance-groups managed update web-mig \
  --region=asia-east1 \
  --health-check=web-hc --initial-delay=300

# 4. 設 autoscaler
gcloud compute instance-groups managed set-autoscaling web-mig \
  --region=asia-east1 \
  --min-num-replicas=2 --max-num-replicas=10 \
  --target-cpu-utilization=0.6
```

要更新版本：改 template，然後 `rolling-action` 滾動替換。

## 8. 觀測

- Ops Agent 自動把 syslog + system metrics 推到 Cloud Logging / Monitoring：
  ```bash
  curl -sSO https://dl.google.com/cloudagents/add-google-cloud-ops-agent-repo.sh
  sudo bash add-google-cloud-ops-agent-repo.sh --also-install
  ```
- Console 上 VM 直接看 CPU / Disk / Network 圖。

## 9. 清理

```bash
gcloud compute instances delete demo-vm --zone=asia-east1-b
gcloud compute disks delete data-disk --zone=asia-east1-b
```

> VM 即使 **stopped** 也還會收 disk 費（boot disk + 任何 attached disk）。完全不用就刪。

## 10. 常見坑

- **VM 連不到網路**：沒外部 IP 又沒 Cloud NAT，或 firewall egress 被擋。
- **SSH 連不上**：firewall 沒放 IAP 範圍 `35.235.240.0/20`、或沒給 `osLogin` + `iap.tunnelResourceAccessor`。
- **Spot VM 突然消失**：正常行為，要設計成可重啟。MIG + Spot 是好組合。
- **PD 滿了**：`gcloud compute disks resize ... --size=200GB`，再進 VM 內 `resize2fs` / `xfs_growfs`。
- **Boot disk 太小，patch 不下去**：debian-12 預設 10GB，跑久了 `/var` 滿。改成 20GB+。
- **Service Account scope 太窄**：舊版 `--scopes` 預設不含寫 GCS。新建議統一 `--scopes=cloud-platform` + IAM 控制（用 IAM 限制比 scope 細）。
