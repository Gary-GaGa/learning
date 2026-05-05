# BigQuery

GCP 的 serverless 資料倉儲。**不是給 OLTP 用的**——它是分析用，丟 TB / PB 級資料進去用 SQL 查，幾秒回應。

## 1. 核心概念

```
Project
└── Dataset            ← 權限與計費的單位（類似 schema/database）
    └── Table          ← 一張表
        ├── Partition  ← 依日期或整數切片，掃描時只掃需要的
        └── Cluster    ← 在 partition 內依欄位排序，加速過濾
```

| 名詞 | 對應傳統 DB |
| --- | --- |
| Dataset | Database / Schema |
| Table | Table |
| **Partition** | 日期分表 |
| **Cluster** | 索引（但是是物理排序，不是 B-tree） |

## 2. 計費模式（重要）

| 模式 | 計費方式 | 適合 |
| --- | --- | --- |
| **On-demand** | 依「掃描的資料量」計費（per TB scanned） | 偶爾跑、查詢不固定 |
| **Capacity / Editions（slots）** | 包月 slot（運算單位） | 高頻穩定查詢、可預測 |

> **on-demand 模式下，`SELECT *` 很貴**——它會掃整張表。永遠只 select 需要的欄位。

## 3. 動手做

### 建 dataset、查公開資料

```bash
bq mk --location=asia-east1 my_dataset

# BigQuery 有大量公開資料集，例如 bigquery-public-data.samples.shakespeare
bq query --use_legacy_sql=false '
SELECT word, COUNT(*) AS n
FROM `bigquery-public-data.samples.shakespeare`
WHERE word_count > 5
GROUP BY word
ORDER BY n DESC
LIMIT 10
'
```

### 載入 GCS 資料

```bash
bq load \
  --location=asia-east1 \
  --source_format=NEWLINE_DELIMITED_JSON \
  --autodetect \
  my_dataset.events \
  gs://YOUR-BUCKET/events/*.json
```

| 格式 | 何時用 |
| --- | --- |
| `CSV` | 簡單、人類可讀，但慢且容易踩雷（quote / null） |
| `NEWLINE_DELIMITED_JSON` | 巢狀結構彈性高 |
| **`PARQUET`** / `AVRO` / `ORC` | **正式環境首選**，自帶 schema、壓縮、列存 |

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

> 需要近即時資料才用 streaming（額外費用）。批次 load 是免費的。

### 建 partitioned + clustered table

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

之後查詢一定要在 WHERE 帶上 partition 欄位才會被剪枝：

```sql
-- 好：只掃 1 天
SELECT * FROM my_dataset.events
WHERE DATE(event_ts) = '2026-05-01' AND user_id = 'u1';

-- 壞：掃整張表
SELECT * FROM my_dataset.events WHERE user_id = 'u1';
```

## 4. 控制成本的習慣

### 4.1 Dry run 看會掃多少

```bash
bq query --use_legacy_sql=false --dry_run '
SELECT user_id, COUNT(*)
FROM `my_dataset.events`
WHERE DATE(event_ts) = "2026-05-01"
GROUP BY user_id
'
# 會回 "This query will process X bytes."
```

或 SQL UI 上方會顯示「This query will process X」。

### 4.2 設 query 上限

```bash
bq query --maximum_bytes_billed=1000000000 ...   # 超過就不執行
```

### 4.3 Project / user 層級配額

Console → IAM & Admin → Quotas，搜 BigQuery，設 `Query usage per day per user`。

## 5. Materialized View / Scheduled Query

- **Materialized view**：預先聚合，BigQuery 自動維護增量。適合常重複的 group by 查詢。
- **Scheduled query**：定時跑一段 SQL（例如每天 02:00 把 raw 表彙總到 daily 表）。

```sql
CREATE MATERIALIZED VIEW my_dataset.events_daily AS
SELECT DATE(event_ts) AS d, user_id, COUNT(*) AS n
FROM my_dataset.events
GROUP BY d, user_id;
```

## 6. 與其他服務整合

| 場景 | 做法 |
| --- | --- |
| Pub/Sub → BigQuery | 建 BigQuery subscription（無 ETL） |
| GCS → BigQuery | `bq load` 或外部表（query 時讀，不複製進來） |
| BQ → Looker Studio | 直接連，做儀表板 |
| BQ ML | 用 SQL 訓模型（線性、樹模型、ARIMA、Embeddings） |

外部表範例：

```sql
CREATE EXTERNAL TABLE my_dataset.gcs_events
OPTIONS (
  format = 'PARQUET',
  uris = ['gs://YOUR-BUCKET/events/*.parquet']
);
```

## 7. 清理

```bash
bq rm -r -f -d PROJECT:my_dataset
```

## 8. 常見坑

- **`SELECT *` 很貴**：on-demand 計費依掃描量。永遠列出欄位。
- **沒設 partition / 沒命中 partition**：一查就掃全表。建表必設 partition，查詢必帶 partition 過濾。
- **JOIN 很大張的表很慢**：盡量在 JOIN 前先 filter / aggregate。`EXPLAIN` 看 stages。
- **Streaming insert 不能立刻 update**：streaming 進來的資料有約 90 分鐘的「streaming buffer」期間不能 UPDATE/DELETE。
- **時區**：`TIMESTAMP` 是 UTC，`DATETIME` 沒時區。報表跨日問題八成是這個。
- **DML 配額**：BigQuery 不是 OLTP，每張表每天 UPDATE/DELETE 次數有上限。需要頻繁更新就改 append + materialized view。
