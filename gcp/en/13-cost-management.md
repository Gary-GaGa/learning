# Cost Management (FinOps on GCP)

Cloud bills can explode overnight. This topic covers three properties to enforce: **predictable, attributable, and optimizable**.

## 1. Billing model recap

| Dimension | Examples |
| --- | --- |
| Compute time | GKE node, GCE VM, Cloud Run vCPU-seconds |
| Storage | GCS GB-month, PD GB-month, BQ active storage |
| Network egress | Cross-region and internet egress are the expensive ones |
| Operations | GCS class A/B, Pub/Sub messages, KMS calls |
| Managed services | Cloud SQL, BigQuery on-demand, AlloyDB |

The bill updates **once per day**, not in real time. By the time you see a spike, it's already 6–24h old.

## 2. Three lines of defense

### Line 1: Budget alert (early warning)

```text
Console → Billing → Budgets & alerts → Create budget
  Scope: project under the billing account
  Amount: e.g. $100 / month
  Threshold rules: 0.5, 0.9, 1.0
  Notifications: email + Pub/Sub topic
```

A Cloud Function subscribed to that Pub/Sub topic can auto-stop expensive resources (e.g. stop GCE VMs, disable APIs).

### Line 2: Quotas (hard cap)

Budgets only warn — they don't block. To actually block:

```text
Console → IAM & Admin → Quotas
  BigQuery → Query usage per day per user → set 1 TB
  Compute Engine → CPUs → cap project-wide vCPUs
```

**Custom org quotas** can be enforced at the org level so child projects can't exceed them.

### Line 3: Billing export to BigQuery (post-hoc analysis)

```text
Console → Billing → Billing export → BigQuery export
  Pick a dataset; from that day on every billing row lands there.
```

Then slice with SQL:

```sql
-- Last month's spend per project
SELECT project.name, SUM(cost) AS spend
FROM `BILLING_PROJECT.billing_export.gcp_billing_export_v1_XXX`
WHERE DATE(usage_start_time) BETWEEN DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY) AND CURRENT_DATE()
GROUP BY 1
ORDER BY spend DESC;

-- Top SKUs
SELECT sku.description, SUM(cost) AS c
FROM `...`
WHERE DATE(usage_start_time) >= DATE_SUB(CURRENT_DATE(), INTERVAL 7 DAY)
GROUP BY 1 ORDER BY c DESC LIMIT 20;
```

## 3. Cost attribution: Labels

**Label every resource** — labels show up in the billing export. Suggested standard labels:

| Label | Example values |
| --- | --- |
| `env` | `prod` / `staging` / `dev` |
| `team` | `platform` / `data` / `growth` |
| `app` | `orders-api` / `analytics-pipeline` |
| `cost-center` | `cc-1234` |

```bash
# Add labels
gcloud compute instances add-labels demo-vm --labels=env=prod,team=platform

# Cloud Run
gcloud run services update hello --labels=env=prod,app=hello

# GCS
gcloud storage buckets update gs://my-bucket --update-labels=env=prod
```

> Use **Org Policy** to enforce "no labels = no resource" (`compute.requireLabels` etc.).

Then in BQ:

```sql
SELECT labels.value AS team, SUM(cost) AS c
FROM `...`, UNNEST(labels) labels
WHERE labels.key = 'team'
GROUP BY 1 ORDER BY c DESC;
```

## 4. Discount mechanisms

| Mechanism | Discount | Best for |
| --- | --- | --- |
| **Sustained Use Discount (SUD)** | Automatic, up to 30% off if running full month | Long-running GCE / GKE |
| **Committed Use Discount (CUD)** | 1y 37%, 3y 55% (vCPU + RAM) | Known steady baseline |
| **Flex CUD** | Cross-machine-type / cross-region flexibility | Variable usage with stable total |
| **Spot VM** | 60–91% off | Interruptible work |
| **BigQuery Editions / reserved slots** | Prepaid slots | High-frequency steady BQ |

In practice: **Run for 1–2 months to find the steady baseline → buy 1y CUDs for "definitely-needed" capacity → use on-demand / Spot for the rest.**

## 5. Find waste: Recommender

GCP automatically detects idle / oversized resources:

```bash
# Idle VMs
gcloud recommender recommendations list \
  --recommender=google.compute.instance.IdleResourceRecommender \
  --location=asia-east1-b \
  --project=PROJECT

# Rightsizing
gcloud recommender recommendations list \
  --recommender=google.compute.instance.MachineTypeRecommender \
  --location=asia-east1-b --project=PROJECT

# Idle PDs
gcloud recommender recommendations list \
  --recommender=google.compute.disk.IdleResourceRecommender \
  --location=asia-east1-b --project=PROJECT
```

Console → **Active Assist → Recommendations** lists everything (idle SAs, broad IAM grants, unused IPs…).

## 6. Service-by-service savings

| Service | Quick wins |
| --- | --- |
| GCE / GKE | Spot VMs; MIG autoscaler max-cap; turn off dev clusters at night |
| Cloud Run | `min-instances=0` (if cold starts OK); raise `concurrency` |
| Cloud SQL | Use zonal for dev; stop instances when idle (only storage charged) |
| GCS | Lifecycle to Nearline/Coldline; expire old versions |
| BigQuery | Always partition + cluster; ban `SELECT *`; use `--maximum-bytes-billed` |
| Network | Keep traffic same-region; cache static content via CDN |
| Logging | Exclude health checks / debug logs; pay only past 30d |

## 7. Auto-shutdown dev/test

```bash
# Cloud Scheduler stops a dev VM every weekday at 20:00
gcloud scheduler jobs create http stop-dev-vm \
  --location=asia-east1 \
  --schedule="0 20 * * 1-5" \
  --time-zone="Asia/Taipei" \
  --uri="https://compute.googleapis.com/compute/v1/projects/PROJECT/zones/asia-east1-b/instances/dev-vm/stop" \
  --http-method=POST \
  --oauth-service-account-email=scheduler@PROJECT.iam.gserviceaccount.com
```

Same pattern for start. MIG autoscalers can also be scheduled to 0 replicas.

## 8. Detect runaway spend

```sql
-- Hourly cost outliers in last 24h
WITH hourly AS (
  SELECT project.id AS project_id,
         TIMESTAMP_TRUNC(usage_start_time, HOUR) AS h,
         SUM(cost) AS c
  FROM `...`
  WHERE usage_start_time > TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 24 HOUR)
  GROUP BY 1, 2
)
SELECT project_id, h, c
FROM hourly
WHERE c > (SELECT AVG(c) * 3 FROM hourly)
ORDER BY h DESC;
```

Push to Looker Studio dashboard, or set a Monitoring alert against this BQ data.

## 9. Common pitfalls

- **Forgotten test resources**: Standard GKE, Cloud SQL HA, idle PDs all bleed money. **Set a $20 budget + alert as a baseline.**
- **Cross-region traffic explosion**: app and data in different regions, or log sink across regions. Check SKU `Network Inter-Region Egress`.
- **Single BQ query costing $hundreds**: on-demand bills per TB scanned and there's no partition filter. Always dry-run.
- **Egress to internet**: external APIs, CDN misses count. Check SKU `Network Internet Egress`.
- **No labels = no attribution**: adding labels later only covers future bills. Enforce labels at creation.
- **Wrong-region/wrong-shape CUD**: 1y minimum, no refunds. Use `Recommender → CUD recommender` and confirm steady usage first.
