# BigQuery

GCP's serverless data warehouse. **Not for OLTP** — it's for analytics: throw TBs/PBs of data at it, query with SQL, get results in seconds.

## 1. Core concepts

```
Project
└── Dataset            ← unit of permissions / billing (like schema/database)
    └── Table          ← a table
        ├── Partition  ← split by date or integer; only relevant slices are scanned
        └── Cluster    ← physical sort within a partition for fast filtering
```

| Term | Traditional DB analog |
| --- | --- |
| Dataset | Database / schema |
| Table | Table |
| **Partition** | Date-based table sharding |
| **Cluster** | Index (but it's physical sort, not a B-tree) |

## 2. Pricing models (important!)

| Model | How charged | Best for |
| --- | --- | --- |
| **On-demand** | Per **bytes scanned** (per TB) | Sporadic / unpredictable workloads |
| **Capacity / Editions (slots)** | Reserved slots (compute units) | High-frequency steady workloads |

> **`SELECT *` is expensive on-demand** — it scans every column. Always select only the columns you need.

## 3. Hands-on

### Create a dataset, query public data

```bash
bq mk --location=asia-east1 my_dataset

# BigQuery has many public datasets, e.g. bigquery-public-data.samples.shakespeare
bq query --use_legacy_sql=false '
SELECT word, COUNT(*) AS n
FROM `bigquery-public-data.samples.shakespeare`
WHERE word_count > 5
GROUP BY word
ORDER BY n DESC
LIMIT 10
'
```

### Load data from GCS

```bash
bq load \
  --location=asia-east1 \
  --source_format=NEWLINE_DELIMITED_JSON \
  --autodetect \
  my_dataset.events \
  gs://YOUR-BUCKET/events/*.json
```

| Format | When to use |
| --- | --- |
| `CSV` | Simple / human-readable, but slow and quirky (quotes, nulls) |
| `NEWLINE_DELIMITED_JSON` | Flexible nested structures |
| **`PARQUET`** / `AVRO` / `ORC` | **Production default** — schema, compression, columnar |

### Streaming insert

```python
# pip install google-cloud-bigquery
from google.cloud import bigquery
client = bigquery.Client()

errors = client.insert_rows_json("PROJECT.my_dataset.events", [
    {"user_id": "u1", "event": "click", "ts": "2026-05-05T08:00:00"}
])
assert errors == []
```

> Use streaming only if you need near-realtime data (extra cost). Batch loads are free.

### Partitioned + clustered table

```sql
CREATE TABLE my_dataset.events (
  user_id   STRING,
  event     STRING,
  payload   JSON,
  event_ts  TIMESTAMP
)
PARTITION BY DATE(event_ts)
CLUSTER BY user_id, event;
```

Always filter on the partition column for pruning to kick in:

```sql
-- Good: scans 1 day
SELECT * FROM my_dataset.events
WHERE DATE(event_ts) = '2026-05-01' AND user_id = 'u1';

-- Bad: scans the whole table
SELECT * FROM my_dataset.events WHERE user_id = 'u1';
```

## 4. Cost-control habits

### 4.1 Dry run to see scan size

```bash
bq query --use_legacy_sql=false --dry_run '
SELECT user_id, COUNT(*)
FROM `my_dataset.events`
WHERE DATE(event_ts) = "2026-05-01"
GROUP BY user_id
'
# Returns "This query will process X bytes."
```

The SQL UI shows the same estimate at the top.

### 4.2 Cap query bytes

```bash
bq query --maximum_bytes_billed=1000000000 ...   # aborts if it would exceed
```

### 4.3 Project / user quotas

Console → IAM & Admin → Quotas → search BigQuery → set `Query usage per day per user`.

## 5. Materialized View / Scheduled Query

- **Materialized view**: precomputed aggregate, BigQuery maintains it incrementally. Great for repetitive group-by queries.
- **Scheduled query**: runs SQL on a schedule (e.g. nightly rollup from raw → daily table).

```sql
CREATE MATERIALIZED VIEW my_dataset.events_daily AS
SELECT DATE(event_ts) AS d, user_id, COUNT(*) AS n
FROM my_dataset.events
GROUP BY d, user_id;
```

## 6. Integrations

| Scenario | Approach |
| --- | --- |
| Pub/Sub → BigQuery | BigQuery subscription (no ETL code) |
| GCS → BigQuery | `bq load` or external table (read in place) |
| BQ → Looker Studio | Direct connector for dashboards |
| BQ ML | Train models in SQL (linear, trees, ARIMA, embeddings) |

External table:

```sql
CREATE EXTERNAL TABLE my_dataset.gcs_events
OPTIONS (
  format = 'PARQUET',
  uris = ['gs://YOUR-BUCKET/events/*.parquet']
);
```

## 7. Cleanup

```bash
bq rm -r -f -d PROJECT:my_dataset
```

## 8. Common pitfalls

- **`SELECT *` is expensive**: on-demand bills by bytes scanned. Always list columns.
- **No partition / partition not used**: query scans the whole table. Always partition; always filter on the partition column.
- **JOINs on huge tables are slow**: filter / aggregate before joining. Use `EXPLAIN` to see stages.
- **Streaming inserts can't be updated immediately**: streaming buffer is non-mutable for ~90 minutes after insert.
- **Timezones**: `TIMESTAMP` is UTC, `DATETIME` has no zone. 80% of date-rollover bugs come from this.
- **DML quota**: BigQuery is not OLTP — daily UPDATE/DELETE limits per table. For heavy mutations, append + materialized views instead.
