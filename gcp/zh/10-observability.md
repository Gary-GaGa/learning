# Cloud Logging 與 Cloud Monitoring（觀測性）

GCP 的觀測性堆疊（舊名 Stackdriver）。系統 metrics 和日誌**自動採集**，不需要裝 agent。重點是：怎麼查、怎麼設告警、怎麼做 dashboard。

## 1. 三大支柱對應

| 支柱 | GCP 服務 | 來源 |
| --- | --- | --- |
| **Logs** | Cloud Logging | 服務自動寫入 + 你的 app stdout/stderr |
| **Metrics** | Cloud Monitoring | 服務自動採集 + 自訂 metrics |
| **Traces** | Cloud Trace | OpenTelemetry / OpenCensus instrumentation |

> 加上 **Cloud Profiler**（CPU/heap profiling）和 **Error Reporting**（自動匯總 stack trace），構成完整 APM。

## 2. Cloud Logging

### 2.1 Log 自動進來的服務

| 來源 | 怎麼進來 |
| --- | --- |
| GKE Pod stdout/stderr | 自動（透過 fluent-bit） |
| Cloud Run / Functions | 自動 |
| GCE VM | 裝 Ops Agent（建議） |
| GCS / Pub/Sub / IAM 等 | Audit Logs 自動 |
| 你的 app | stdout 寫 JSON 即可（structured logging） |

### 2.2 結構化日誌（重要）

print 純文字也會進 Logging，但不能搜尋欄位。**寫 JSON**：

```python
import json, sys
print(json.dumps({
    "severity": "INFO",
    "message": "order created",
    "order_id": "A001",
    "user_id": "u123",
}), file=sys.stdout)
```

`severity` 是特殊欄位（`DEBUG/INFO/WARNING/ERROR/CRITICAL`），會被 Logging 識別。其他欄位變成可查詢的 `jsonPayload.xxx`。

### 2.3 查詢（Logs Explorer）

語法是 [Logging query language](https://cloud.google.com/logging/docs/view/logging-query-language)：

```
# 某個 GKE container 的 ERROR
resource.type="k8s_container"
resource.labels.cluster_name="demo-std"
resource.labels.container_name="api"
severity>=ERROR

# 結構化欄位 + 時間
jsonPayload.user_id="u123"
timestamp>="2026-05-05T00:00:00Z"

# 全文搜尋（會比較慢）
"connection refused"
```

CLI：

```bash
gcloud logging read \
  'resource.type="cloud_run_revision" AND severity>=ERROR' \
  --limit=20 --format=json --freshness=1h
```

### 2.4 Log-based Metrics

把日誌轉成 metric（counter / distribution），就能放進 dashboard、設告警：

```bash
# 每次出現 "payment failed" 增加 1
gcloud logging metrics create payment_failures \
  --description="Count of payment failures" \
  --log-filter='resource.type="cloud_run_revision"
                jsonPayload.event="payment_failed"'
```

接著就能在 Monitoring 用 `logging.googleapis.com/user/payment_failures` 設告警。

### 2.5 Sinks（把日誌路由出去）

```bash
# 把所有 ERROR 以上送到 BigQuery 長期分析
gcloud logging sinks create errors-to-bq \
  bigquery.googleapis.com/projects/PROJECT/datasets/logs \
  --log-filter='severity>=ERROR'

# 給 sink 的 SA 寫入權限（會在 console 顯示要做的指令）
```

常見 sink 目的地：BigQuery（分析）、GCS（長期歸檔）、Pub/Sub（即時轉發到 SIEM）。

### 2.6 Retention 與成本

- **_Required** bucket（30 天）裝 Audit Logs，免費。
- **_Default** bucket 預設 30 天。可以延長，但**超過 30 天才開始計費**。
- 不需要的 log 要 **exclude**，否則攝取量會貴：

```bash
gcloud logging sinks update _Default \
  --add-exclusion=name=skip-healthchecks,filter='httpRequest.requestUrl=~"/healthz"'
```

## 3. Cloud Monitoring

### 3.1 Metric 種類

| 類型 | 來源 |
| --- | --- |
| **GCP-managed**（內建） | CPU、Memory、Network、HTTP requests…全部自動 |
| **Agent metrics** | GCE 上 Ops Agent 採集的 process / disk metrics |
| **Custom metrics** | 你自己 push（OpenTelemetry / API） |
| **Log-based metrics** | 從 Cloud Logging 衍生 |
| **Prometheus（GMP）** | Google Managed Prometheus，吃 PromQL |

### 3.2 看 metrics（Metrics Explorer）

選 resource type → metric → group by → filter：

```
resource = "k8s_container"
metric   = "kubernetes.io/container/cpu/core_usage_time"
filter   = container_name = "api"
group by = pod_name
aggregator = rate
```

CLI 抓最近 5 分鐘：

```bash
gcloud monitoring time-series list \
  --filter='metric.type="run.googleapis.com/request_count" AND resource.labels.service_name="hello"' \
  --interval-end-time=$(date -u +%FT%TZ) \
  --interval-start-time=$(date -u -d '5 minutes ago' +%FT%TZ)
```

### 3.3 自訂 metric（Python，OpenTelemetry）

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

> 自己起伺服器讓 GMP 抓 `/metrics` 比直接 push 更省事，K8s 環境推薦走 GMP。

### 3.4 GMP（Google Managed Prometheus）

GKE 上的標準做法：

```bash
# Autopilot 預設啟用；Standard 加 flag
gcloud container clusters update demo-std \
  --zone=asia-east1-b \
  --enable-managed-prometheus
```

之後寫一個 `PodMonitoring` CRD 指到你的 `/metrics`，就會被自動採集，且可以用 PromQL 在 Metrics Explorer 查。

## 4. Alerting

### 4.1 建告警

```bash
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

### 4.2 通知管道

```bash
gcloud beta monitoring channels create \
  --display-name="oncall-slack" \
  --type=slack \
  --channel-labels=channel_name=#alerts \
  --user-labels=team=platform
```

支援 Email、Slack、PagerDuty、Webhook、SMS（部分國家）等。

### 4.3 SLO

把「good event / total event」定義出來，Monitoring 自動算 error budget burn rate，並能設「快速燃燒」告警（避免事故已經爆了才通知）。

## 5. Dashboard

- Console UI 直接拖 widget。
- 可以匯出 JSON 後 commit 進 git，搭配 Terraform `google_monitoring_dashboard` 管理。

## 6. 常用查詢範例

```text
# Cloud Run 5xx 比例（按服務）
fetch cloud_run_revision::run.googleapis.com/request_count
| filter metric.response_code_class = '5xx'
| group_by [resource.service_name], sum(value.request_count)
| ratio with fetch cloud_run_revision::run.googleapis.com/request_count
            | group_by [resource.service_name], sum(value.request_count)

# GKE Pod restart 次數
metric.type="kubernetes.io/container/restart_count"
```

## 7. 常見坑

- **沒寫 structured log**：純文字塞進 `textPayload`，所有欄位都搜不到。永遠 JSON。
- **Log 量爆炸**：debug log 上 prod 沒過濾，攝取費破表。設 exclude filter。
- **Alert 太敏感**：threshold 太低 + duration 太短 → 一直叫，oncall 累壞 → 大家把通知關掉。設 SLO + burn rate 告警比較合理。
- **跨 project 看不到 logs**：用 **Log Bucket aggregation** 或 sink 匯總到一個中央 project。
- **Custom metric 累積成本**：Custom metrics 依「active time series」計費，標籤組合爆炸（per user_id）會很貴。標籤要有「卡上限」的維度（region、env、service），不要無界值。
- **GMP scrape 不到**：`PodMonitoring` selector 對不到 Pod、或 Pod 沒開 `/metrics` port。
