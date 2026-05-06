# learning

學習筆記與可執行範例 / Learning notes and runnable demos.

每篇都用「**概念 → 指令 → 常見坑**」的結構，能單獨讀、雙語對照。
Each topic follows **concepts → commands → common pitfalls**, stands alone, bilingual.

## 主題 / Topics

| 主題 / Topic | 內容 / Content | 連結 / Link |
| --- | --- | --- |
| ☁️ **GCP** | 17 篇 + 端對端 demo：GKE、Cloud Run、Pub/Sub、BigQuery、Cloud SQL、Storage、IAM、Networking、CI/CD、Terraform … | [`gcp/`](./gcp/README.md) |
| 🔐 **Keycloak** | Realm/Client/Token、URL-path 多租戶、Token claim、Spring Boot 3 整合範例 | [`keycloak/`](./keycloak/README.md) |
| 🔁 **Camunda** | Camunda 7 BPMN 流程引擎、External Task、Tasklist/Cockpit；附 Camunda 8 差異 | [`camunda/`](./camunda/README.md) |

每個主題都附 Docker / Terraform / 程式碼讓你能跑得起來。
Every topic includes Docker / Terraform / source code you can actually run.

## 從哪裡開始 / Where to start

- 雲端基礎入門 → **GCP**：先讀 [`gcp/zh/00-overview.md`](./gcp/zh/00-overview.md) ｜ [`gcp/en/00-overview.md`](./gcp/en/00-overview.md)
- 身份驗證 / 授權 → **Keycloak**：[`keycloak/docs/zh-TW/`](./keycloak/docs/zh-TW/README.md) ｜ [`keycloak/docs/en/`](./keycloak/docs/en/README.md)
- 工作流程引擎 → **Camunda**：[`camunda/docs/zh-TW/`](./camunda/docs/zh-TW/README.md) ｜ [`camunda/docs/en/`](./camunda/docs/en/README.md)

## 原則 / Principles

- **可重現**：每個範例都有 docker-compose / terraform / 完整指令，能跑得起來。
- **雙語**：核心文件中英對照。
- **強調「常見坑」**：每篇都列出實作會踩到的具體問題，不只是 happy path。
