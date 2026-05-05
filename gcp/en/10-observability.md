# Cloud Logging and Cloud Monitoring (Observability)

GCP's observability stack (formerly Stackdriver). System metrics and logs are **collected automatically** — no agent install needed. The skill is knowing how to query them, set alerts, and build dashboards.

## 1. Three pillars

| Pillar | GCP service | Source |
| --- | --- | --- |
| **Logs** | Cloud Logging | Service-emitted + your app stdout/stderr |
| **Metrics** | Cloud Monitoring | Service-collected + custom metrics |
| **Traces** | Cloud Trace | OpenTelemetry / OpenCensus instrumentation |

> Plus **Cloud Profiler** (CPU/heap profiling) and **Error Reporting** (auto-grouped stack traces) for full APM.

## 2. Cloud Logging

### 2.1 Sources flowing in automatically

| Source | How it gets in |
| --- | --- |
| GKE Pod stdout/stderr | Automatic (via fluent-bit) |
| Cloud Run / Functions | Automatic |
| GCE VM | Install Ops Agent (recommended) |
| GCS / Pub/Sub / IAM, etc. | Audit Logs (automatic) |
| Your app | Just write JSON to stdout (structured logging) |

### 2.2 Structured logging (important)

Plain text reaches Logging too, but you can't query fields. **Write JSON**:

```python
import json, sys
print(json.dumps({
    "severity": "INFO",
    "message": "order created",
    "order_id": "A001",
    "user_id": "u123",
}), file=sys.stdout)
```

`severity` is a special field (`DEBUG/INFO/WARNING/ERROR/CRITICAL`) Logging recognizes. Other fields become queryable as `jsonPayload.xxx`.

### 2.3 Querying (Logs Explorer)

Uses [Logging query language](https://cloud.google.com/logging/docs/view/logging-query-language):

```
# Errors from a GKE container
resource.type="k8s_container"
resource.labels.cluster_name="demo-std"
resource.labels.container_name="api"
severity>=ERROR

# Structured field + time range
jsonPayload.user_id="u123"
timestamp>="2026-05-05T00:00:00Z"

# Full-text search (slower)
"connection refused"
```

CLI:

```bash
gcloud logging read \
  'resource.type="cloud_run_revision" AND severity>=ERROR' \
  --limit=20 --format=json --freshness=1h
```

### 2.4 Log-based Metrics

Convert logs to metrics (counter / distribution) for dashboards and alerts:

```bash
# Increment on each "payment failed"
gcloud logging metrics create payment_failures \
  --description="Count of payment failures" \
  --log-filter='resource.type="cloud_run_revision"
                jsonPayload.event="payment_failed"'
```

Then in Monitoring, alert on `logging.googleapis.com/user/payment_failures`.

### 2.5 Sinks (route logs elsewhere)

```bash
# Send all ERROR+ to BigQuery for long-term analysis
gcloud logging sinks create errors-to-bq \
  bigquery.googleapis.com/projects/PROJECT/datasets/logs \
  --log-filter='severity>=ERROR'

# Grant the sink's writer SA write access (console shows the exact command)
```

Common destinations: BigQuery (analysis), GCS (long-term archive), Pub/Sub (forward to a SIEM).

### 2.6 Retention and cost

- **_Required** bucket (30 days) holds Audit Logs — free.
- **_Default** bucket retains 30 days. Extending is allowed; **billing kicks in past 30 days**.
- Exclude noise to control ingestion costs:

```bash
gcloud logging sinks update _Default \
  --add-exclusion=name=skip-healthchecks,filter='httpRequest.requestUrl=~"/healthz"'
```

## 3. Cloud Monitoring

### 3.1 Metric kinds

| Kind | Source |
| --- | --- |
| **GCP-managed** (built-in) | CPU, memory, network, HTTP requests, etc. — automatic |
| **Agent metrics** | Process / disk metrics from Ops Agent on GCE |
| **Custom metrics** | You push them (OpenTelemetry / API) |
| **Log-based metrics** | Derived from Cloud Logging |
| **Prometheus (GMP)** | Google Managed Prometheus, PromQL |

### 3.2 Querying (Metrics Explorer)

Pick resource type → metric → group by → filter:

```
resource = "k8s_container"
metric   = "kubernetes.io/container/cpu/core_usage_time"
filter   = container_name = "api"
group by = pod_name
aggregator = rate
```

CLI for the last 5 minutes:

```bash
gcloud monitoring time-series list \
  --filter='metric.type="run.googleapis.com/request_count" AND resource.labels.service_name="hello"' \
  --interval-end-time=$(date -u +%FT%TZ) \
  --interval-start-time=$(date -u -d '5 minutes ago' +%FT%TZ)
```

### 3.3 Custom metric (Python, OpenTelemetry)

```python
# pip install opentelemetry-exporter-gcp-monitoring
from opentelemetry import metrics
from opentelemetry.exporter.cloud_monitoring import CloudMonitoringMetricsExporter
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader

reader = PeriodicExportingMetricReader(CloudMonitoringMetricsExporter())
metrics.set_meter_provider(MeterProvider(metric_readers=[reader]))

meter = metrics.get_meter("my-app")
orders_total = meter.create_counter("orders_total", description="orders created")

orders_total.add(1, {"region": "tw", "tier": "pro"})
```

> In K8s, exposing `/metrics` for GMP to scrape is usually less work than direct push.

### 3.4 GMP (Google Managed Prometheus)

Standard practice on GKE:

```bash
# Autopilot enables it by default; Standard needs the flag
gcloud container clusters update demo-std \
  --zone=asia-east1-b \
  --enable-managed-prometheus
```

Then create a `PodMonitoring` CRD pointing at your `/metrics`. Metrics are auto-collected and queryable with PromQL in Metrics Explorer.

## 4. Alerting

### 4.1 Create an alert

```yaml
# alert.yaml
displayName: "High 5xx rate on hello"
combiner: OR
conditions:
- displayName: "5xx > 1% over 5min"
  conditionThreshold:
    filter: |
      metric.type="run.googleapis.com/request_count"
      resource.type="cloud_run_revision"
      resource.label.service_name="hello"
      metric.label.response_code_class="5xx"
    aggregations:
    - alignmentPeriod: 60s
      perSeriesAligner: ALIGN_RATE
    comparison: COMPARISON_GT
    thresholdValue: 0.01
    duration: 300s
notificationChannels:
- "projects/PROJECT/notificationChannels/CHANNEL_ID"
```

```bash
gcloud alpha monitoring policies create --policy-from-file=alert.yaml
```

### 4.2 Notification channels

```bash
gcloud beta monitoring channels create \
  --display-name="oncall-slack" \
  --type=slack \
  --channel-labels=channel_name=#alerts \
  --user-labels=team=platform
```

Supports Email, Slack, PagerDuty, Webhook, SMS (regional), etc.

### 4.3 SLOs

Define "good events / total events" — Monitoring computes error-budget burn rate and supports "fast burn" alerts that fire before an incident fully unfolds.

## 5. Dashboards

- Build by drag-and-drop in console.
- Export JSON, commit to git, manage via Terraform `google_monitoring_dashboard`.

## 6. Useful queries

```text
# Cloud Run 5xx ratio per service
fetch cloud_run_revision::run.googleapis.com/request_count
| filter metric.response_code_class = '5xx'
| group_by [resource.service_name], sum(value.request_count)
| ratio with fetch cloud_run_revision::run.googleapis.com/request_count
            | group_by [resource.service_name], sum(value.request_count)

# GKE Pod restart count
metric.type="kubernetes.io/container/restart_count"
```

## 7. Common pitfalls

- **No structured logging**: text lands in `textPayload`, so no fields are searchable. Always JSON.
- **Log volume explosion**: debug logs in prod with no filter → ingestion bill blows up. Add exclude filters.
- **Over-sensitive alerts**: low threshold + short duration → constant pages → oncall mutes notifications. SLO + burn-rate alerts are better.
- **Cross-project log access**: aggregate via Log Bucket or sinks into a central project.
- **Custom metric cost creep**: billed by **active time series**; high-cardinality labels (per user_id) get expensive fast. Use bounded labels (region, env, service), not unbounded values.
- **GMP doesn't scrape**: `PodMonitoring` selector doesn't match the Pod, or `/metrics` port not exposed.
