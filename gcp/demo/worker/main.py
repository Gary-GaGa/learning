"""Pub/Sub push subscriber.

Receives a Pub/Sub push and writes the order into Cloud SQL via the Cloud
SQL Python Connector.

Auth: the Cloud Run service is configured to allow only the
"pushinvoker" service account as roles/run.invoker. Cloud Run validates the
OIDC token before our code runs, so we don't re-verify it here.
"""
import base64
import json
import os

import sqlalchemy
from flask import Flask, jsonify, request
from google.cloud.sql.connector import Connector

PROJECT = os.environ["PROJECT_ID"]
INSTANCE = os.environ["DB_INSTANCE"]              # PROJECT:REGION:INSTANCE
DB_NAME = os.environ["DB_NAME"]
DB_USER = os.environ["DB_USER"]
DB_PASS = os.environ["DB_PASSWORD"]               # injected from Secret Manager

app = Flask(__name__)
connector = Connector()


def _make_engine() -> sqlalchemy.engine.Engine:
    def getconn():
        return connector.connect(
            INSTANCE, "pg8000", user=DB_USER, password=DB_PASS, db=DB_NAME
        )

    return sqlalchemy.create_engine(
        "postgresql+pg8000://", creator=getconn, pool_pre_ping=True
    )


_engine = _make_engine()


def _ensure_schema() -> None:
    with _engine.begin() as conn:
        conn.execute(sqlalchemy.text("""
            CREATE TABLE IF NOT EXISTS orders (
                order_id TEXT PRIMARY KEY,
                amount   NUMERIC,
                item     TEXT,
                invoice_uri TEXT,
                created_at TIMESTAMPTZ DEFAULT now()
            )
        """))


_ensure_schema()


@app.post("/pubsub")
def receive():
    envelope = request.get_json(silent=True) or {}
    msg = envelope.get("message", {})
    data_b64 = msg.get("data", "")
    payload = json.loads(base64.b64decode(data_b64).decode("utf-8")) if data_b64 else {}

    order_id = payload.get("order_id")
    if not order_id:
        # Don't retry malformed messages; ack to drop.
        return "", 204

    with _engine.begin() as conn:
        conn.execute(
            sqlalchemy.text("""
                INSERT INTO orders (order_id, amount, item, invoice_uri)
                VALUES (:order_id, :amount, :item, :invoice_uri)
                ON CONFLICT (order_id) DO NOTHING
            """),
            payload,
        )

    return "", 204


@app.get("/healthz")
def healthz():
    return "ok", 200


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "8080")))
