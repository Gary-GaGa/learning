# Keycloak Learning Workspace

這個資料夾是一套從零開始的 Keycloak 學習與實作環境（Docker 優先），目標是讓你能完成：
- 基礎概念：Realm / Client / User / Role / Group / Token
- 多租戶（單 Realm，多租戶用 URL path：`/t/{tenant}/...`）
- 將 `tenantId` 放進 token claim 並在 API 端強制比對
- 細粒度授權（混合模式）：Keycloak Authorization Services + API 端資料層檢查
- Java 整合：Spring Boot 3 + Spring Security（Resource Server）

## 快速開始

1) 啟動 Keycloak

- 複製環境變數檔：
  - `cp .env.example .env`
- 啟動：
  - `docker compose up -d`
- 管理端：
  - http://localhost:8080

2) 跑範例 API（Spring Boot 3）

- 進入：`spring-boot-demo/`
- `mvn spring-boot:run`

## 教學文件

- 中文（繁體）：[docs/zh-TW/README.md](docs/zh-TW/README.md)
- English: [docs/en/README.md](docs/en/README.md)

## 需求

- macOS + Docker Desktop
- Java 17+
- Maven（例如：`brew install maven`）

> 註：本資料夾偏向學習與展示（demo）。正式環境請看文件中的 Production 注意事項。