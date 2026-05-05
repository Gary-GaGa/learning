# Pub/Sub

GCP's fully-managed message queue / event bus, similar to Kafka but serverless. Key properties: **at-least-once delivery**, **globally available**, **autoscales to millions of QPS**.

## 1. Core concepts

```
Publisher  ──publish──►  Topic  ──fan-out──►  Subscription A ──pull/push──►  Subscriber A
                                          ╰──►  Subscription B ──pull/push──►  Subscriber B
```

| Term | Meaning |
| --- | --- |
| **Topic** | Named message stream; publishers write here. |
| **Subscription** | A consumer instance attached to a topic. Each subscription receives a **full copy** of the topic's messages. |
| **Message** | `data` (bytes) + `attributes` (small KV) + `messageId` + `publishTime`. |
| **Ack deadline** | Subscriber must ack within this time, or Pub/Sub redelivers. |
| **Dead Letter Topic (DLT)** | Messages that fail too many redeliveries land here, preventing poison-message stalls. |

> **One topic, many subscriptions = fan-out.** E.g. `orders` topic has `billing-sub` and `analytics-sub` — both consume the same stream independently.

## 2. Push vs Pull

| Mode | Best for | Drawbacks |
| --- | --- | --- |
| **Pull** | Your own workers, batch processing, backpressure | Needs a long-running client |
| **Push** | Cloud Run / Cloud Functions / external HTTPS endpoints | Can overwhelm downstream during spikes; needs auth |

Push delivers a JSON POST to your endpoint:

```json
{
  "message": {
    "data": "base64-encoded-payload",
    "attributes": { "key": "value" },
    "messageId": "...",
    "publishTime": "2026-05-05T08:00:00Z"
  },
  "subscription": "projects/PROJECT/subscriptions/SUB_NAME"
}
```

Returning `2xx` acks; non-2xx triggers redelivery.

## 3. Hands-on

### Create topic and subscription

```bash
gcloud pubsub topics create orders

# Pull subscription (most common)
gcloud pubsub subscriptions create orders-billing \
  --topic=orders \
  --ack-deadline=30 \
  --message-retention-duration=7d

# Push subscription (to a Cloud Run service)
gcloud pubsub subscriptions create orders-notify \
  --topic=orders \
  --push-endpoint=https://my-service-xxxxx.a.run.app/pubsub \
  --push-auth-service-account=pubsub-invoker@PROJECT.iam.gserviceaccount.com
```

### Add a Dead Letter Topic

```bash
gcloud pubsub topics create orders-dlt

gcloud pubsub subscriptions update orders-billing \
  --dead-letter-topic=orders-dlt \
  --max-delivery-attempts=5
```

> Don't forget to grant the Pub/Sub service SA `pubsub.publisher` on the DLT — otherwise DLT writes silently fail.

### Publish / consume from CLI

```bash
# Publish
gcloud pubsub topics publish orders \
  --message='{"order_id":"A001","amount":120}' \
  --attribute="region=tw,priority=high"

# Pull one (auto-ack)
gcloud pubsub subscriptions pull orders-billing --auto-ack --limit=5
```

## 4. Code example (Python)

```python
# pip install google-cloud-pubsub
from google.cloud import pubsub_v1
import json

PROJECT = "YOUR_PROJECT"

# ---- Publisher ----
publisher = pubsub_v1.PublisherClient()
topic_path = publisher.topic_path(PROJECT, "orders")

future = publisher.publish(
    topic_path,
    data=json.dumps({"order_id": "A001", "amount": 120}).encode("utf-8"),
    region="tw",                          # attributes
    priority="high",
)
print("messageId:", future.result(timeout=30))


# ---- Subscriber (Pull, streaming) ----
subscriber = pubsub_v1.SubscriberClient()
sub_path = subscriber.subscription_path(PROJECT, "orders-billing")

def handle(message):
    try:
        payload = json.loads(message.data)
        print("got order:", payload, "attrs:", dict(message.attributes))
        # ... actual processing
        message.ack()
    except Exception as e:
        print("processing failed:", e)
        message.nack()                    # immediate redelivery; or skip ack and let deadline expire

streaming = subscriber.subscribe(sub_path, callback=handle)
print("listening...")
try:
    streaming.result()                    # blocks
except KeyboardInterrupt:
    streaming.cancel()
```

> The streaming pull client extends ack deadlines automatically (lease management) — you don't have to manage it.

## 5. Message ordering

Pub/Sub doesn't guarantee order by default. To enable:

1. Create the topic with `--message-ordering`.
2. Publish with an `ordering_key` (e.g. `user_id`).
3. Subscription needs `--enable-message-ordering`.

```bash
gcloud pubsub topics create user-events --message-ordering
```

Caveat: with ordering, messages with the same key are processed serially by **one** subscriber → throughput drops.

## 6. Schema (catch producer/consumer mismatch)

Register an Avro / Protobuf schema and Pub/Sub validates messages:

```bash
gcloud pubsub schemas create order-schema \
  --type=AVRO \
  --definition-file=order.avsc

gcloud pubsub topics create orders \
  --schema=order-schema \
  --message-encoding=JSON
```

Out-of-schema messages get rejected at publish time — bugs caught at producer instead of consumer.

## 7. Integrations

| Scenario | Approach |
| --- | --- |
| GCS upload event | `gcloud storage buckets notifications create gs://BUCKET --topic=...` (publishes directly to Pub/Sub) |
| Cloud Run receives events | Push subscription + OIDC auth |
| GKE worker | Pull subscription + Workload Identity |
| Load to BigQuery | **BigQuery subscription** — no ETL code |
| Cross-service events | Publish to a topic, multiple subscriptions consume independently |

## 8. Cleanup

```bash
gcloud pubsub subscriptions delete orders-billing orders-notify
gcloud pubsub topics delete orders orders-dlt
```

## 9. Common pitfalls

- **Duplicate messages**: at-least-once means duplicates **will** happen. **Subscribers must be idempotent** (dedupe by `messageId` or business key).
- **Messages keep getting redelivered**: subscriber didn't ack (crashed, or processing exceeded ack deadline). Watch `subscription/oldest_unacked_message_age`.
- **Push subscription 4xx**: endpoint not authenticated / not returning 200. Use OIDC token verification.
- **Messages lost when no subscription exists**: messages published **before** any subscription exists are not retained. Create the subscription first.
- **Long delays**: default retention is 7 days, max 31. After that, messages drop.
- **Large messages**: max 10MB per message. Cheaper pattern: drop the payload to GCS, publish only the GCS path.
