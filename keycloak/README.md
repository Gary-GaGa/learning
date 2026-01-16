# Keycloak Learning Workspace

這個資料夾是一套從零開始的 Keycloak 學習與實作環境（以 Docker 為主），目標涵蓋：

- 核心概念：Realm / Client / User / Role / Group / Token
- 多租戶：單 Realm + URL path（`/t/{tenant}/...`）
- Token claim：把 `tenantId` 放進 access token，並在 API 端強制比對
- 細粒度授權：混合模式（Keycloak Authorization Services + API 資料層檢查）
- Java 整合：Spring Boot 3 + Spring Security（Resource Server）

## 快速開始

### 1) 啟動 Keycloak（Docker Compose）

- 複製環境變數檔：`cp .env.example .env`
- 啟動：`docker compose up -d`
- 管理端：http://localhost:8080

### 2) 執行範例 API（Spring Boot 3）

- 進入 `spring-boot-demo/`
- 執行：`mvn spring-boot:run`

## 範例

- `spring-boot-demo/`：示範多租戶 URL path、JWT 驗證、授權與資料層強制。

## 教學文件

- 中文（繁體）：[docs/zh-TW/README.md](docs/zh-TW/README.md)
- English: [docs/en/README.md](docs/en/README.md)

## 需求

- macOS + Docker Desktop
- Java 17+
- Maven（例如：`brew install maven`）

## 備註

本資料夾偏向學習與展示（demo）。正式環境相關建議請參考教學文件內的 Production 注意事項。