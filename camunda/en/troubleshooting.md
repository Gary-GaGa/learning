# Troubleshooting: decision trees for Camunda

> First move: open **Cockpit** (http://localhost:8090/camunda/app/cockpit) to inspect process instances and incidents. If there's no incident but the process isn't moving, it's usually a user task no one claimed or an external task worker that isn't running.

## 1. Camunda won't start / web UI unreachable

```mermaid
flowchart TD
  A[After docker compose up<br/>http://localhost:8090 unreachable] --> B{docker ps shows<br/>container Running?}
  B -->|Not running| C[docker compose logs camunda<br/>see startup errors]
  B -->|Running but health failing| D[Wait 30–60s; Camunda 7 boots slowly]
  B -->|Running and healthy| E{Port 8090 in use?}
  E -->|Yes| E1[lsof -i :8090<br/>or change host port in compose file]
  E -->|No| F[Check firewall / VPN<br/>some VPNs block docker bridge]

  C --> C1[Common: OutOfMemory<br/>Bump Docker Desktop RAM to 4GB+]
  C --> C2[Common: H2 file lock<br/>previous crash; docker compose down -v]
```

## 2. BPMN deploy fails

```mermaid
flowchart TD
  A[Deploy via Cockpit / REST fails] --> B{Error message?}
  B -->|"ENGINE-09005 Could not parse BPMN"| B1[Invalid BPMN XML<br/>resave from Modeler; don't hand-edit]
  B -->|"...id is not unique"| B2[Same process key conflict in deployment<br/>delete old deployment or change key]
  B -->|"Cannot deploy: ..."| B3[File too large / engine permission<br/>check application logs]
  B -->|"Form key not found"| B4[Embedded form path wrong<br/>or form not bundled in deployment]
```

## 3. Process started but "stuck"

```mermaid
flowchart TD
  A[Instance started but no progress in Cockpit] --> B{Stuck on which activity?}
  B -->|User Task| C[Check Tasklist]
  B -->|Service Task external| D[Check worker]
  B -->|Service Task Java Delegate| E[App log for exceptions]
  B -->|Timer / Intermediate Event| F[Wait for time / inspect timer expression]

  C --> C1{Task visible in Tasklist?}
  C1 -->|No| C2[Filter wrong / not candidate user/group<br/>adjust filter or task assignment]
  C1 -->|Yes but cannot complete| C3[Form validation fails<br/>check form data]

  D --> D1{Worker running?}
  D1 -->|No| D2[Start worker:<br/>node worker.mjs]
  D1 -->|Yes| D3{Topic name matches?}
  D3 -->|Mismatch| D4[BPMN's camunda:topic must EXACTLY match<br/>worker subscription name]
  D3 -->|Matches| D5{Worker log shows errors?}
  D5 -->|Yes| D6[Fix worker bug<br/>must call complete / handleFailure]
  D5 -->|No| D7[lockDuration too short<br/>worker hasn't completed yet → lock expires → redelivery]
```

## 4. External Task keeps becoming an Incident

```mermaid
flowchart TD
  A[Cockpit shows incident] --> B[Click → read incident message]
  B --> C{retries == 0?}
  C -->|Yes| D[Worker failed retries times consecutively<br/>each handleFailure didn't pass remaining retries]
  C -->|No| E[Inspect worker code: handleFailure 4th arg<br/>must pass remaining retries]

  D --> D1[Increment retries in Cockpit<br/>let it try again]
  D --> D2[Or fix root cause first, then increment]

  E --> E1[handleFailure default retries = 0<br/>you MUST set retries = N]
```

> Template: `handleFailure(taskId, workerId, "msg", retries=3, retryTimeout=60_000)`. **Forgetting retries** is the #1 cause.

## 5. Weird variable behavior

| Symptom | Usual cause |
| --- | --- |
| Variable reads as `null` | Not yet set at this step, or wrong scope (local vs global) |
| Updated a variable but next step still sees the old value | Used local scope when global expected, or vice versa |
| Java Delegate changes don't persist | Mutated map from `execution.getVariables()` without calling `setVariable` |
| Object serialization error | Camunda 7 defaults to Java serialization; jar mismatch breaks it. Use JSON or String |
| `OptimisticLockingException` | Concurrent modification of same instance (worker race). Just retry |

## 6. Incident handling flow

```mermaid
flowchart TD
  A[Cockpit incidents list] --> B[Click instance → read details]
  B --> C{Error type?}
  C -->|Failed external task| D[Fix worker → increment retries]
  C -->|Failed Java job| E[Fix code → restart engine or increment retries]
  C -->|Failed timer| F[Fix expression → cancel + restart instance]
  C -->|No clear error| G[App log for stack trace<br/>filter by instance id]

  D --> H{Variable change needed?}
  H -->|Yes| H1[Edit variable in Cockpit<br/>then increment retries]
  H -->|No| H2[Just increment retries]
```

## 7. Tasklist doesn't show my task

```mermaid
flowchart TD
  A[My expected task not in Tasklist] --> B{User task has assignee?}
  B -->|None| C[Default no one — check candidate user / group]
  B -->|Someone else| D[Only that user can see it; reassign or log in as that user]

  C --> C1{Candidate group set?}
  C1 -->|Yes| C2[Logged-in user must be a member]
  C1 -->|No candidate| C3[No assignee + no candidate = nobody can claim<br/>BPMN must specify task assignment]

  A --> E{Filter correct?}
  E --> E1[Tasklist defaults to All Tasks<br/>switching to My Tasks hides unassigned]
```

## 8. Camunda 8 (Zeebe) equivalents

If you've moved to Camunda 8:

| C7 symptom → C8 equivalent |
| --- |
| External task not picked up → Job worker not subscribed to the correct type |
| Incident in Cockpit → Incident in **Operate**; you must resolve before flow continues |
| Engine REST → Zeebe gRPC (use zbctl or client lib); no equivalent REST endpoint |
| H2 / Postgres → No RDBMS; data lives in Zeebe partitions + Elastic |

## 9. Still stuck

1. **Cockpit instance detail**: Activity Instance Tree shows the path taken
2. **Open history**: Cockpit's History tab shows the full execution trail
3. **`docker compose logs -f camunda`** filtered by instance id
4. **Beef up worker logging**: print task id, topic, variables, result
5. **[Camunda Forum](https://forum.camunda.io/)** — active community
