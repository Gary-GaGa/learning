# 07 - Debugging & operations (Cockpit/logs/incidents)

## Goal

Know where to look when a process is stuck or failing.

## 1) Start with Cockpit

Typical uses:

- inspect definitions and versions
- see where an instance is waiting
- inspect incidents and error details

## 2) Common failure map

### Stuck at a user task

- check Tasklist:
  - unclaimed
  - assigned to a group/user you can’t see

### Stuck at an External Task

- no worker running
- topic mismatch
- worker failures with no retries → incident

### Incidents

- read incident details in Cockpit
- inspect worker/service logs
- after fixing, decide:
  - retry
  - adjust data/variables
  - adjust BPMN model

## 3) Docker logs

From the camunda/ folder:

```bash
docker compose logs -f camunda
```

## 4) Minimal ops tips (learning)

- treat (process key + instance id) as your tracing key
- External Task workers should have:
  - alerting on failures
  - retries/backoff
  - useful logs

## Checklist

- You know Cockpit + Tasklist are first stops
- You know External Task issues are often worker/topic
- You understand incidents require intervention

## Next

Continue to [08 - Camunda 8 vs Camunda 7 (architecture/execution model)](08-camunda8-differences.md).
