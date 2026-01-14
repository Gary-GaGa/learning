# Camunda Learning Workspace

這個資料夾以 Camunda 7 為主，從基礎概念到可以完成一個可執行的流程（BPMN）並透過 Tasklist/Cockpit 觀察與操作。

另外會額外提供幾個章節說明 Camunda 8 的差異與延伸（Zeebe、Job Worker、Operate/Tasklist、Connectors）。

## 快速開始（Camunda 7）

1) 啟動 Camunda 7（Docker Compose）

- 在 camunda/ 目錄下：
  - `docker compose up -d`
- Web apps：
  - http://localhost:8090

2) 登入

Camunda 7 內建 demo 使用者（預設）：

- 帳號：`demo`
- 密碼：`demo`

3) 部署一個最小流程

- 你可以直接把 [examples/hello-user-task.bpmn](examples/hello-user-task.bpmn) 上傳部署（教學 01/02 章會帶你走 UI）。

## 教學文件

- 中文（繁體）：[docs/zh-TW/README.md](docs/zh-TW/README.md)
- English: [docs/en/README.md](docs/en/README.md)

## 需求

- Docker Desktop

> 註：Camunda 7 與 Camunda 8 是不同世代的產品線；本資料夾先把 Camunda 7 的流程建構打穩，再看 Camunda 8 的差異會更有感。
