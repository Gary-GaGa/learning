# 09 - Camunda 8 延伸：Worker、Operate、Connectors、遷移建議

## 目標

用「你已經會的 Camunda 7」做出 Camunda 8 的延伸圖譜：你需要學什麼、如何評估遷移。

## 1) Job Worker（Camunda 8）

你可以把它想成：

- Camunda 7 External Task 的「新版思路」

差異要點（概念層）：

- 不再是用 Engine REST 的 fetchAndLock
- 而是透過 Zeebe 的 job 機制與 client/worker SDK

## 2) Operate

- 用於觀測流程實例與排查問題
- 更偏「可觀測性與操作」而非「直接改引擎內部狀態」

## 3) Connectors

- 用於常見整合（HTTP、事件、資料連接等）
- 目標是把「整合」標準化與配置化

## 4) 遷移評估建議（從 7 → 8）

先從這幾個問題開始：

1. 你的吞吐與延展性需求是否真的需要 8？
2. 你是否接受多元件帶來的部署/維運成本？
3. 你現有流程裡：
   - user task 為主？
   - 還是大量自動化整合（worker）？

## 5) 最小遷移策略（學習版）

- 先挑一條「邏輯簡單、風險低」的流程做 PoC
- 優先把自動化整合抽成 worker（降低引擎耦合）
- 觀測性與告警先建立起來（Operate/指標/日志）

## 檢核點

- 你知道 Camunda 8 的 worker 與 7 的 External Task 概念接近
- 你知道 Operate/Connectors 是 8 的重點元件
- 你知道遷移要先評估維運成本與需求
