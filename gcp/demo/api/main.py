"""Order ingestion service.

POST /orders {"order_id": "...", "amount": ..., "item": "..."}
- Writes invoice JSON to GCS
- Publishes event to Pub/Sub
"""
import json
import os
from datetime import datetime, timezone

from flask import Flask, jsonify, request
from google.cloud import pubsub_v1, storage

PROJECT = os.environ["PROJECT_ID"]
BUCKET = os.environ["INVOICE_BUCKET"]
TOPIC = os.environ["ORDERS_TOPIC"]

app = Flask(__name__)
storage_client = storage.Client()
publisher = pubsub_v1.PublisherClient()
topic_path = publisher.topic_path(PROJECT, TOPIC)


@app.post("/orders")
def create_order():
    payload = request.get_json(silent=True) or {}
    order_id = payload.get("order_id")
    if not order_id:
        return jsonify(error="order_id required"), 400

    invoice = {
        **payload,
        "received_at": datetime.now(timezone.utc).isoformat(),
    }

    blob_path = f"invoices/{order_id}.json"
    storage_client.bucket(BUCKET).blob(blob_path).upload_from_string(
        json.dumps(invoice), content_type="application/json"
    )
    invoice_uri = f"gs://{BUCKET}/{blob_path}"

    publisher.publish(
        topic_path,
        data=json.dumps({**payload, "invoice_uri": invoice_uri}).encode("utf-8"),
        order_id=order_id,
    ).result(timeout=10)

    return jsonify(status="accepted", invoice=invoice_uri), 202


@app.get("/healthz")
def healthz():
    return "ok", 200


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "8080")))
