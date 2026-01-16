# Camunda 教學（中文，Camunda 7 為主）

這個資料夾是一條循序漸進的學習路線，先從 Camunda 7 打底，再在關鍵處對照 Camunda 8。

## 學習路線（建議順序）

00. [核心概念（引擎、流程定義、任務、變數）](00-core-concepts.md)
01. [Docker 快速開始（Camunda 7 Run）](01-quickstart-docker.md)
02. [建立並部署第一個流程（Hello User Task）](02-first-process-deploy.md)
03. [Tasklist 與表單/任務指派](03-tasklist-and-forms.md)
04. [External Task 模式（與 Worker）](04-external-task-pattern.md)
05. [流程變數、錯誤與重試](05-variables-errors-retries.md)
06. [部署版本、流程升級與 Migration 思路](06-versioning-and-migration.md)
07. [除錯與維運（Cockpit/Logs/Incidents）](07-debugging-and-ops.md)

## Camunda 8：差異與延伸

08. [Camunda 8 與 Camunda 7 的差異（架構/執行模式）](08-camunda8-differences.md)
09. [Camunda 8 延伸：Worker、Operate、Connectors、遷移建議](09-camunda8-extensions.md)

## 你會完成什麼

- 用 Docker **執行** Camunda 7（Webapps + Engine）
- 透過 Cockpit 部署 BPMN，並在 Tasklist 操作 user task
- 了解 External Task 與 Worker 的分工
- 具備流程版本控管與除錯的基本能力
