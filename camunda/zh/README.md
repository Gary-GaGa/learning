# Camunda 教學（中文，Camunda 7 為主）

> Language: **中文** ｜ [English](../en/README.md)

從 BPMN 核心概念到「能執行流程」的完整路線（Camunda 7 為主），最後補 Camunda 8 的差異與遷移。

## 從這裡開始

🗺️ **第一次看？先讀 [00-overview.md](./00-overview.md)** —— BPMN/引擎/Worker 模型、學習路徑、Camunda 7 vs 8 速覽、詞彙表。

🆘 **卡住了？看 [troubleshooting.md](./troubleshooting.md)** —— 流程啟動失敗、卡 user task、External Task 沒被抓、Incident 等決策樹。

## 目錄

| 主題 | 內容 | 連結 |
| --- | --- | --- |
| 00 | 總覽（topic map / 學習路徑 / 詞彙表） | [00-overview.md](./00-overview.md) |
| 01 | 核心概念（Camunda 7） | [01-core-concepts.md](./01-core-concepts.md) |
| 02 | Docker 快速開始（Camunda 7 Run） | [02-quickstart-docker.md](./02-quickstart-docker.md) |
| 03 | 建立並部署第一個流程（Hello User Task） | [03-first-process-deploy.md](./03-first-process-deploy.md) |
| 04 | Tasklist 與表單／任務指派 | [04-tasklist-and-forms.md](./04-tasklist-and-forms.md) |
| 05 | External Task 模式（與 Worker） | [05-external-task-pattern.md](./05-external-task-pattern.md) |
| 06 | 流程變數、錯誤與重試 | [06-variables-errors-retries.md](./06-variables-errors-retries.md) |
| 07 | 部署版本、流程升級與 Migration 思路 | [07-versioning-and-migration.md](./07-versioning-and-migration.md) |
| 08 | 除錯與維運（Cockpit／Logs／Incidents） | [08-debugging-and-ops.md](./08-debugging-and-ops.md) |
| 09 | Camunda 8 與 Camunda 7 的差異 | [09-camunda8-differences.md](./09-camunda8-differences.md) |
| 10 | Camunda 8 延伸：Worker、Operate、Connectors、遷移建議 | [10-camunda8-extensions.md](./10-camunda8-extensions.md) |
| 🆘 | Troubleshooting：常見問題決策樹 | [troubleshooting.md](./troubleshooting.md) |

## 你會完成什麼

- 在本機用 Docker 跑 Camunda 7 Run（Tasklist / Cockpit / Admin）
- 部署 BPMN 流程定義並啟動實例
- 用 Tasklist 完成 user task
- 用 External Task + Node.js worker 跑自動化步驟
- 理解流程變數、錯誤、重試與 Incident
- 能對照 Camunda 8 的差異（Zeebe / Job Worker / Operate）
