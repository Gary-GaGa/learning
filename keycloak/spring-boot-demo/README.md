# Spring Boot 3 Demo API

這是 Keycloak 教學用的後端 API 範例（Resource Server）。

## 功能

- JWT 驗證（Spring Security OAuth2 Resource Server）
- 多租戶：URL path `/t/{tenant}/...`
- 強制 `{tenant}` 必須等於 token claim `tenant_id`
- 範例資源：reports
  - `reports:read` → 可讀
  - `reports:write` → 可寫
- 混合授權：
  - Keycloak（scope/role）做功能面
  - API 端做資料層（tenant/擁有者）檢查

## 執行

- 需求：Java 17+、Maven

```bash
mvn spring-boot:run
```

預設 port：8081

## 端點

- `GET /actuator/health`（不需登入）
- `GET /t/{tenant}/me`（需 token；回傳解析結果）
- `GET /t/{tenant}/reports`（需要 `reports:read`）
- `POST /t/{tenant}/reports`（需要 `reports:write`）

## 設定

預設 issuer：`http://localhost:8080/realms/demo`

你可以用環境變數覆蓋：

- `ISSUER_URI=http://localhost:8080/realms/demo`
