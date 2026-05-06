# 01 - Docker 快速開始（Camunda 7 Run）

## 目標

用 Docker **執行** Camunda 7，能登入 webapps。

## 1) 啟動

在 [camunda/docker-compose.yml](../../docker-compose.yml) 所在目錄：

- `docker compose up -d`

開啟：

- Welcome： http://localhost:8090/camunda/app/welcome/default/#!/welcome
- Tasklist： http://localhost:8090/camunda/app/tasklist/default/
- Cockpit： http://localhost:8090/camunda/app/cockpit/default/
- Admin： http://localhost:8090/camunda/app/admin/default/

## 2) 登入

預設 demo 使用者：

- 帳號：`demo`
- 密碼：`demo`

## 3) 停止與重置

- 停止：`docker compose down`

這個快速開始使用 embedded H2 資料庫（容器內）。

- 想要完整重置：
  - `docker compose down` 後再 `docker compose up -d`（通常就夠）

> 之後如果要更貼近正式環境，再加上 Postgres 服務與持久化 volume。

## 4) 健康檢查（選用）

- Engine REST： http://localhost:8090/engine-rest/engine

若看到 JSON（engine name 等），代表引擎可用。

## 下一步

繼續到 [02 - 建立並部署第一個流程（Hello User Task）](02-first-process-deploy.md)。
