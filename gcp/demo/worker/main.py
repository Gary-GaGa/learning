"""Pub/Sub push subscriber.

Receives a Pub/Sub push, verifies the OIDC token, then writes the order
into Cloud SQL using the Cloud SQL Python Connector.
"""
import base64
import json
import os
from typing import Optional

import sqlalchemy
from flask import Flask, jsonify, request
from google.auth.transport import requests as google_requests
from google.cloud.sql.connector import Connector
from google.oauth2 import id_token

PROJECT = os.environ["PROJECT_ID"]
INSTANCE = os.environ["DB_INSTANCE"]              # PROJECT:REGION:INSTANCE
DB_NAME = os.environ["DB_NAME"]
DB_USER = os.environ["DB_USER"]
DB_PASS = os.environ["DB_PASSWORD"]               # injected from Secret Manager
PUSH_AUDIENCE = os.environ["PUSH_AUDIENCE"]       # this service's URL

app = Flask(__name__)
connector = Connector()


def _connect() -> "sqlalchemy.engine.Connection":
    def getconn():
        return connector.connect(
            INSTANCE, "pg8000", user=DB_USER, password=DB_PASS, db=DB_NAME
        )

    engine = sqlalchemy.create_engine("postgresql+pg8000://", creator=getconn, pool_pre_ping=True)
    return engine


_engine = _connect()


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


def _verify_push_token(req) -> Optional[str]:
    auth = req.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        return "missing bearer token"
    token = auth.split(" ", 1)[1]
    try:
        id_token.verify_oauth2_token(token, google_requests.Request(), audience=PUSH_AUDIENCE)
    except Exception as e:
        return f"invalid token: {e}"
    return None


@app.post("/pubsub")
def receive():
    err = _verify_push_token(request)
    if err:
        return jsonify(error=err), 401

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
