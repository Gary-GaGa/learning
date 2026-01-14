# 01 - Docker quickstart (Camunda 7 Run)

Goal: run Camunda 7 via Docker and log into the webapps.

## 1) Start

From the folder that contains [camunda/docker-compose.yml](../../docker-compose.yml):

- `docker compose up -d`

Open:

- Welcome: http://localhost:8090/camunda/app/welcome/default/#!/welcome
- Tasklist: http://localhost:8090/camunda/app/tasklist/default/
- Cockpit: http://localhost:8090/camunda/app/cockpit/default/
- Admin: http://localhost:8090/camunda/app/admin/default/

## 2) Login

Default demo user:

- username: `demo`
- password: `demo`

## 3) Stop and reset

- Stop: `docker compose down`

This quickstart uses an embedded H2 database (inside the container).

If you want a clean state, bring it down and start again.

## 4) Optional health check

- Engine REST: http://localhost:8090/engine-rest/engine

If you see JSON output, the engine is up.
