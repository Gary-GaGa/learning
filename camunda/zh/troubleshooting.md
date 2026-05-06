# Troubleshooting：Camunda 常見問題決策樹

> 起手式：先到 **Cockpit**（http://localhost:8090/camunda/app/cockpit）看流程實例與 Incidents。看不到 incident、但流程沒動，多半是 user task 沒人 claim 或 external task worker 沒跑。

## 1. Camunda 起不來 / Web 進不去

```mermaid
flowchart TD
  A[docker compose up 後<br/>http://localhost:8090 連不到] --> B{docker ps 看 container<br/>是否 Running?}
  B -->|沒在跑| C[docker compose logs camunda<br/>看啟動錯誤]
  B -->|在跑但 health failing| D[等 30–60s; Camunda 7 啟動較慢]
  B -->|在跑且 healthy| E{port 8090 被占用?}
  E -->|是| E1[lsof -i :8090<br/>或在 docker-compose.yml 改 host port]
  E -->|否| F[檢查 firewall / VPN<br/>有些 VPN 會擋 docker bridge]

  C --> C1[常見訊息: OutOfMemory<br/>Docker Desktop 記憶體調大到 4GB+]
  C --> C2[常見: H2 file lock<br/>之前異常結束; docker compose down -v 清掉]
```

## 2. 部署 BPMN 失敗

```mermaid
flowchart TD
  A[Cockpit / REST 部署失敗] --> B{錯誤訊息?}
  B -->|"ENGINE-09005 Could not parse BPMN"| B1[BPMN XML 不合法<br/>用 Modeler 重新存檔; 不要手改 XML]
  B -->|"...id is not unique"| B2[同一個 process key 同一個 deployment 衝突<br/>把舊 deployment 砍掉或改 key]
  B -->|"Cannot deploy: ..."| B3[檔案太大 / 引擎權限<br/>看 application logs]
  B -->|"Form key 找不到"| B4[Embedded form 路徑不對<br/>或 form 沒一起部署]
```

## 3. 流程啟動但「卡住」

```mermaid
flowchart TD
  A[啟動了 instance, Cockpit 看流程沒進度] --> B{停在哪一個 activity?}
  B -->|User Task| C[到 Tasklist 看任務]
  B -->|Service Task external| D[檢查 worker]
  B -->|Service Task Java Delegate| E[application log 看有沒 exception]
  B -->|Timer / Intermediate Event| F[等時間到 / 檢查 timer expression]

  C --> C1{Tasklist 裡看到任務嗎?}
  C1 -->|看不到| C2[Filter 不對 / 不是 candidate user/group<br/>調整 filter 或 task assignment]
  C1 -->|看到但不能 complete| C3[表單欄位驗證失敗<br/>看 form data]

  D --> D1{Worker 在跑嗎?}
  D1 -->|沒| D2[啟動 worker:<br/>node worker.mjs]
  D1 -->|有| D3{topic 名稱對嗎?}
  D3 -->|不對| D4[BPMN 的 camunda:topic 跟 worker subscribe 名稱<br/>必須完全一致]
  D3 -->|對| D5{worker log 有 error?}
  D5 -->|有| D6[修 worker 的 bug<br/>complete / handleFailure 要呼叫到]
  D5 -->|沒| D7[lockDuration 太短<br/>worker 還沒 complete 就 lock 過期 → 重複]
```

## 4. External Task 一直變 Incident

```mermaid
flowchart TD
  A[Cockpit 看到 incident] --> B[點進去看 incident message]
  B --> C{retries 變 0?}
  C -->|是| D[worker 連續失敗 retries 次<br/>每次都呼 handleFailure 但沒重置 retries]
  C -->|否| E[檢查 worker code: handleFailure 第 4 個參數<br/>應傳剩餘 retries]

  D --> D1[Cockpit 上 increment retries<br/>讓它再試]
  D --> D2[或修好底層問題後再 increment]

  E --> E1[handleFailure 預設 retries 沒設 = 直接 0<br/>必須帶 retries = N]
```

> 範本：`handleFailure(taskId, workerId, "msg", retries=3, retryTimeout=60_000)`。**忘了設 retries** 是最常見原因。

## 5. Variables 行為怪

| 症狀 | 多半的原因 |
| --- | --- |
| Variable 取出來是 `null` | 流程到那一步時還沒設定；或 scope 不對（local vs global） |
| 改了 variable，下一步取到的還是舊值 | 用了 local scope 但 expect global，或反之 |
| Java Delegate 改 variable 沒生效 | 用了 `execution.getVariables()` 拿到 map 改值，但沒 `setVariable` 寫回去 |
| Object 序列化錯誤 | Camunda 7 預設用 Java 序列化；換 jar 版本就掛。改用 JSON 或 String |
| `org.camunda.bpm.engine.OptimisticLockingException` | 兩個地方同時改同一個 instance（worker 沒處理 race）；retry 即可 |

## 6. Incident 處理流程

```mermaid
flowchart TD
  A[Cockpit Incidents 列表] --> B[點 instance → 看錯誤詳情]
  B --> C{錯誤類型?}
  C -->|Failed external task| D[修 worker → cockpit increment retries]
  C -->|Failed Java job| E[修程式 → 重啟引擎或 increment retries]
  C -->|Failed timer| F[修 expression → cancel + restart instance]
  C -->|無明確錯誤| G[application log 找 exception<br/>用 instance id 過濾]

  D --> H{資料需要修嗎?}
  H -->|是| H1[Cockpit 改 variable<br/>再 increment retries]
  H -->|否| H2[直接 increment retries]
```

## 7. Tasklist 找不到我的任務

```mermaid
flowchart TD
  A[Tasklist 沒看到我預期的任務] --> B{User task 有 assignee 嗎?}
  B -->|沒設| C[預設沒人 → 看 candidate user / group]
  B -->|有設別人| D[只有那個 user 看得到; 改 assignment 或登入正確 user]

  C --> C1{Candidate group?}
  C1 -->|有| C2[登入帳號要在那個 group]
  C1 -->|沒有 candidate| C3[沒 assignee + 沒 candidate = 沒人能 claim<br/>BPMN 要設 task assignment]

  A --> E{Filter 對嗎?}
  E --> E1[Tasklist 預設 All Tasks; 換到 My Tasks 看不到 unassigned 的]
```

## 8. Camunda 8 (Zeebe) 的對應問題

如果你已經在用 Camunda 8：

| C7 症狀 → C8 對應 |
| --- |
| External task 沒被抓 → Job worker 沒 subscribe 到正確 type |
| Incident 在 Cockpit → Incident 在 **Operate**，需要先 resolve incident 才能讓流程繼續 |
| Engine REST → Zeebe gRPC（用 zbctl 或 client lib），沒有同形式的 REST endpoint |
| H2 / Postgres → 沒有 RDBMS；資料在 Zeebe partitions + Elastic |

## 9. 仍找不到原因

1. **看 Cockpit 的 instance 詳情頁**：Activity Instance Tree 顯示走過哪些 activity
2. **打開 history**：Cockpit 的 History 標籤可以看執行軌跡
3. **`docker compose logs -f camunda`** 過濾 instance id
4. **External task worker 加詳細 log**：印出 task id、topic、variables、結果
5. **官方 [Forum](https://forum.camunda.io/)** — Camunda 社群活躍
