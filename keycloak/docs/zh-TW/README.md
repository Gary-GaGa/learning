# Keycloak 教學（中文）

這個資料夾是一條循序漸進的學習路線，聚焦在多租戶、token claim 與 Spring Boot 3 整合。

## 學習路線（建議順序）

00. [核心概念（Realm / Client / User / Role / Group / Token）](00-core-concepts.md)
01. [Docker 快速開始](01-quickstart-docker.md)
02. [多租戶（URL path，單 Realm）](02-multi-tenancy-url-path.md)
03. [Token claim：tenantId 與 Mapper](03-token-claims-tenant.md)
04. [Spring Boot 3 Resource Server 整合](04-spring-boot3-integration.md)
05. [細粒度授權（混合：Keycloak + API）](05-fine-grained-hybrid-authorization.md)
06. [除錯、檢核與工具](06-debugging-and-tools.md)
07. [Production 注意事項（簡版）](07-production-notes.md)

## 你會完成什麼

- 在本機用 Docker 執行 Keycloak + Postgres
- 用管理端手動建立 Realm/Client/User/Group/Role
- 把 `tenantId` 放進 access token claim（例如 `tenant_id`）
- 用 Spring Boot 3 驗證 JWT 並從 token 取出 `tenant_id`
- 將 URL path 的 `{tenant}` 與 token claim 比對（避免跨租戶越權）
- 用 scope/role 做功能面授權；資源層交給 API 端做資料檢查
