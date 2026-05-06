# 01 - Docker quickstart (Keycloak + Postgres)

## Goal

Run Keycloak locally via Docker, log into the Admin Console, and create a realm.

## Prerequisites

- Docker Desktop

## 1) Start

From the keycloak/ folder:

1. Copy env file:

- `cp .env.example .env`

2. Start services:

- `docker compose up -d`

3. Open Admin Console:

- http://localhost:8080

Login with `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` from `.env`.

## 2) Create a realm (manual)

1. Top-left dropdown → **Create realm**
2. Name: `demo`
3. Create

After creating `demo`, it will look “empty” at first (no clients/users/groups/roles yet). That’s expected; the next chapters walk you through creating them manually.

Note: This tutorial uses a single realm with URL-path based multi-tenancy.

## 3) Export / import realm (optional)

- Export: Realm settings → Action → Partial export
- Import: Create realm → Import

## 4) Troubleshooting

- Ports busy: ensure `8080` / `5432` are free.
- Reset data:
  - `docker compose down -v` (deletes Postgres volume)

## Next

Continue to [02 - Multi-tenancy (URL path, single realm)](02-multi-tenancy-url-path.md).
