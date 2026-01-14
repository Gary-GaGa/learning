# 07 - 除錯與維運（Cockpit/Logs/Incidents）

目標：當流程卡住或失敗時，你知道去哪裡看、怎麼定位問題。

## 1) 先看 Cockpit

你通常會在 Cockpit 做：

- 看流程定義與版本
- 看流程實例目前卡在哪個活動
- 看 incidents（事故）與錯誤訊息

## 2) 常見問題地圖

### 問題：流程卡在 user task

- 去 Tasklist 看任務是否：
  - 沒人 claim
  - 被分配到你看不到的群組/使用者

### 問題：流程卡在 External Task

- Worker 沒有在跑
- topic 名稱不一致
- Worker 失敗後沒有 retries，最後變成 incident

### 問題：出現 Incident

- 到 Cockpit 看錯誤內容
- 回頭看 worker / 服務 log
- 修正後再決定：
  - 重試
  - 修改資料/變數
  - 調整流程模型

## 3) 看容器 logs（Docker）

在 camunda/ 目錄：

```bash
docker compose logs -f camunda
```

## 4) 最小維運建議（學習版）

- 把「流程 key + instance id」當成追蹤主鍵
- External Task Worker 要有：
  - 失敗告警
  - retries/backoff
  - 可觀測的 log（不要只印 exception）

## 檢核點

- 你知道流程卡住先看 Cockpit + Tasklist
- 你知道 External Task 卡住多半是 worker/topic 問題
- 你知道 incident 是需要介入處理的訊號
