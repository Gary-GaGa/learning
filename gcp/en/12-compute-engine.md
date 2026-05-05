# Compute Engine (GCE)

GCP's foundational IaaS: launching VMs. New projects often skip straight to Cloud Run / GKE, but **understanding GCE is foundational** — GKE nodes are GCE VMs, Cloud SQL runs on GCE underneath.

## 1. Core objects

```
Instance (VM)
├── Machine type (e2-small, n2-standard-4, c3-highmem-22 ...)
├── Image / Source (OS image or disk snapshot)
├── Boot disk + extra disks (Persistent Disk / Hyperdisk / Local SSD)
├── Network interface (VPC + subnet, internal IP, optional external IP)
├── Service account (the VM's own GCP identity)
├── Metadata (startup-script, ssh-keys ...)
└── Labels / Tags (for firewall / billing)
```

## 2. Machine families

| Family | Best for |
| --- | --- |
| `e2-*` | General purpose, cheap, shared-core entry |
| `n2-*` / `n2d-*` | Balanced; most production workloads |
| `c3-*` / `c3d-*` | High CPU performance (Intel / AMD) |
| `m3-*` / `m2-*` | High memory (in-memory DBs, SAP HANA) |
| `a2-*` / `g2-*` | GPU (A2 = A100, G2 = L4) |
| `t2d-*` | Arm (cheap, fits web workloads) |

**Spot / Preemptible**: up to 70–91% cheaper, but GCP can reclaim them anytime (max 24h). Good for retryable batch work.

## 3. Launch a VM

```bash
gcloud compute instances create demo-vm \
  --zone=asia-east1-b \
  --machine-type=e2-small \
  --image-family=debian-12 \
  --image-project=debian-cloud \
  --network=prod-vpc --subnet=prod-tw \
  --no-address \                      # no external IP (pair with Cloud NAT)
  --service-account=app-vm@PROJECT.iam.gserviceaccount.com \
  --scopes=cloud-platform \
  --tags=ssh-allowed,http-server \
  --metadata=enable-oslogin=TRUE \
  --metadata-from-file=startup-script=startup.sh
```

`startup.sh` (runs on every boot):

```bash
#!/bin/bash
apt-get update && apt-get install -y nginx
systemctl enable --now nginx
```

## 4. SSH (use IAP, don't expose port 22)

```bash
# Through IAP TCP forwarding (no external IP needed)
gcloud compute ssh demo-vm --zone=asia-east1-b --tunnel-through-iap

# Grant a user SSH (no need to manage ~/.ssh/authorized_keys)
gcloud projects add-iam-policy-binding PROJECT \
  --member=user:alice@example.com \
  --role=roles/compute.osLogin
gcloud projects add-iam-policy-binding PROJECT \
  --member=user:alice@example.com \
  --role=roles/iap.tunnelResourceAccessor
```

> **OS Login** + **IAP**: keys managed via Google identity, audit logs show who logged in, no public port 22.

## 5. Persistent Disk

```bash
# Create a 100GB SSD
gcloud compute disks create data-disk \
  --zone=asia-east1-b --size=100GB --type=pd-ssd

# Attach
gcloud compute instances attach-disk demo-vm \
  --disk=data-disk --zone=asia-east1-b

# Format + mount inside the VM
gcloud compute ssh demo-vm --zone=asia-east1-b -- "
  sudo mkfs.ext4 -F /dev/disk/by-id/google-data-disk
  sudo mkdir -p /data
  sudo mount /dev/disk/by-id/google-data-disk /data
"
```

| Type | Best for |
| --- | --- |
| `pd-standard` | Cold data, logs |
| `pd-balanced` | General workloads (recommended default) |
| `pd-ssd` | High-IOPS DB, low-latency |
| `pd-extreme` / `Hyperdisk` | Highest performance (custom IOPS / throughput) |
| Local SSD | Ephemeral, extreme performance (lost when VM stops) |

## 6. Snapshots and images

```bash
# Snapshot (incremental, can restore cross-region)
gcloud compute snapshots create boot-snap-$(date +%s) \
  --source-disk=demo-vm --source-disk-zone=asia-east1-b

# Restore into a new disk
gcloud compute disks create restored \
  --zone=asia-east1-b --source-snapshot=boot-snap-XXX
```

You can set up **Snapshot Schedules** for daily backups with retention.

## 7. Managed Instance Group (MIG)

For autoscaling, autohealing, rolling updates → use a MIG, not hand-managed VMs.

```bash
# 1. Create an instance template (VM blueprint)
gcloud compute instance-templates create web-tpl \
  --machine-type=e2-small \
  --image-family=debian-12 --image-project=debian-cloud \
  --metadata-from-file=startup-script=startup.sh \
  --tags=http-server

# 2. Create a regional MIG
gcloud compute instance-groups managed create web-mig \
  --base-instance-name=web \
  --template=web-tpl \
  --size=3 \
  --region=asia-east1

# 3. Add health check + autohealing
gcloud compute health-checks create http web-hc --request-path=/healthz --port=80
gcloud compute instance-groups managed update web-mig \
  --region=asia-east1 \
  --health-check=web-hc --initial-delay=300

# 4. Configure autoscaling
gcloud compute instance-groups managed set-autoscaling web-mig \
  --region=asia-east1 \
  --min-num-replicas=2 --max-num-replicas=10 \
  --target-cpu-utilization=0.6
```

To roll a new version: update the template, then trigger `rolling-action` to replace VMs gradually.

## 8. Observability

- Ops Agent auto-ships syslog + system metrics to Cloud Logging / Monitoring:
  ```bash
  curl -sSO https://dl.google.com/cloudagents/add-google-cloud-ops-agent-repo.sh
  sudo bash add-google-cloud-ops-agent-repo.sh --also-install
  ```
- Console shows CPU / Disk / Network charts per VM.

## 9. Cleanup

```bash
gcloud compute instances delete demo-vm --zone=asia-east1-b
gcloud compute disks delete data-disk --zone=asia-east1-b
```

> A **stopped** VM still costs disk fees (boot disk + any attached disks). Delete when truly unused.

## 10. Common pitfalls

- **VM has no internet**: no external IP and no Cloud NAT, or egress firewall blocks it.
- **Can't SSH**: firewall doesn't allow IAP range `35.235.240.0/20`, or user lacks `osLogin` + `iap.tunnelResourceAccessor`.
- **Spot VM disappeared**: expected — design for restart. MIG + Spot is a good combo.
- **PD full**: `gcloud compute disks resize ... --size=200GB`, then `resize2fs` / `xfs_growfs` inside the VM.
- **Boot disk too small for patches**: debian-12 default is 10GB; `/var` fills up. Use 20GB+.
- **Service Account scope too narrow**: legacy `--scopes` excluded GCS write. New best practice: `--scopes=cloud-platform` and let IAM enforce the actual perms.
