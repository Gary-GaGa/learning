# Keycloak 教學（中文）

> Language: **中文** ｜ [English](../en/README.md)

聚焦在 **多租戶（URL path）+ Token claim + Spring Boot 3** 的學習路線，附可執行的 Docker Compose 與 Spring Boot 3 範例。

## 從這裡開始

🗺️ **第一次看？先讀 [00-overview.md](./00-overview.md)** —— 一張圖看完所有主題的關係、學習路徑、詞彙表。

🆘 **卡住了？看 [troubleshooting.md](./troubleshooting.md)** —— 401/403、token claim 沒出現、tenant 比對失敗等決策樹。

## 目錄

| 主題 | 內容 | 連結 |
| --- | --- | --- |
| 00 | 總覽（topic map / 學習路徑 / 詞彙表） | [00-overview.md](./00-overview.md) |
| 01 | 核心概念（Realm / Client / User / Role / Group / Token） | [01-core-concepts.md](./01-core-concepts.md) |
| 02 | Docker 快速開始（Keycloak + Postgres） | [02-quickstart-docker.md](./02-quickstart-docker.md) |
| 03 | 多租戶（URL path，單 Realm） | [03-multi-tenancy-url-path.md](./03-multi-tenancy-url-path.md) |
| 04 | Token claim：tenantId 與 Mapper | [04-token-claims-tenant.md](./04-token-claims-tenant.md) |
| 05 | Spring Boot 3 Resource Server 整合 | [05-spring-boot3-integration.md](./05-spring-boot3-integration.md) |
| 06 | 細粒度授權（混合：Keycloak + API） | [06-fine-grained-hybrid-authorization.md](./06-fine-grained-hybrid-authorization.md) |
| 07 | 除錯、檢核與工具 | [07-debugging-and-tools.md](./07-debugging-and-tools.md) |
| 08 | Production 注意事項（簡版） | [08-production-notes.md](./08-production-notes.md) |
| 🆘 | Troubleshooting：常見問題決策樹 | [troubleshooting.md](./troubleshooting.md) |

## 你會完成什麼

- 在本機用 Docker 跑 Keycloak + Postgres
- 用管理端手動建立 Realm / Client / User / Group / Role
- 把 `tenantId` 放進 access token claim（例如 `tenant_id`）
- 用 Spring Boot 3 驗證 JWT 並從 token 取出 `tenant_id`
- 將 URL path 的 `{tenant}` 與 token claim 比對（避免跨租戶越權）
- 用 scope/role 做功能面授權，資源層交給 API 端做資料檢查
