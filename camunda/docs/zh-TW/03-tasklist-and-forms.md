# 03 - Tasklist 與表單/任務指派

## 目標

理解 user task 的指派模型（candidate groups/users）與 Tasklist 的操作。

## 1) candidate groups 是什麼

在 BPMN 的 user task 上，你可以設定：

- 候選群組（candidate groups）
- 候選使用者（candidate users）

範例流程 [hello-user-task.bpmn](../../examples/hello-user-task.bpmn) 裡有：

- `camunda:candidateGroups="demo"`

意思是：屬於 `demo` 群組的使用者，可以在 Tasklist 看到這個任務並認領（claim）。

## 2) Tasklist 基本操作

- **Claim**：把任務認領給自己（避免多人同時處理）
- **Complete**：完成任務，流程往下走
- **Unclaim**（若 UI 支援）：放回候選狀態

## 3) 表單（Forms）在 Camunda 7 的位置

Camunda 7 常見表單做法：

- 由前端/你的系統自己做表單（最常見）
- 用 `formKey` 指向你自己的 UI
- 或用 Camunda 7 的內嵌表單（embedded form）（較舊模式）

這份教學先把「流程建構 + 任務處理」跑通，表單部分先以概念為主。

## 4) 你可以做的小練習

1. 在 Tasklist 完成任務時，加一個變數（如果 UI 有提供）：
   - `approved=true`
2. 到 Cockpit 看該流程實例的變數（History/Runtime 依 UI）

## 檢核點

- 你知道 candidate groups 影響誰看得到任務
- 你知道 Claim 與 Complete 的差異
- 你知道表單通常是你的系統負責，流程只保存狀態與資料

## 下一步

繼續到 [04 - External Task 模式（與 Worker）](04-external-task-pattern.md)。
