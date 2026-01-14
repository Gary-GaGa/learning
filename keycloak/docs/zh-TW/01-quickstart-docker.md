# 01 - Docker 快速開始（Keycloak + Postgres）

目標：在本機用 Docker **執行** Keycloak 管理端，並能登入、建立 Realm。

## 前置需求

- Docker Desktop

## 1) 啟動

在 keycloak/ 目錄：

1. 複製環境變數檔：

- `cp .env.example .env`

2. 啟動服務：

- `docker compose up -d`

3. 打開管理端：

- http://localhost:8080

用 `.env` 的 `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` 登入。

## 2) 建立 Realm（手動）

1. 左上角下拉選單 → **Create realm**（建立 realm）
2. Name：`demo`
3. Create

建立完後 `demo` 一開始是空的（還沒有你的 Client/User/Group/Role），這是正常的；下一章開始會帶你逐步手動建立。

> 小提醒：Keycloak 的「多租戶」可以用多 Realm 或單 Realm。這套教學採「單 Realm，多租戶用 URL path」。

## 3) 匯出 / 匯入 Realm（選用）

學習階段建議你用手動建立熟悉 UI，但也可以：

- 匯出：管理端 → Realm settings → Action → Partial export
- 匯入：管理端 → Create realm → Import

## 4) 常見問題

- Port 被佔用：確認本機沒有其他服務使用 `8080` / `5432`
- 重置資料：
  - `docker compose down -v`（會清掉 Postgres volume）
