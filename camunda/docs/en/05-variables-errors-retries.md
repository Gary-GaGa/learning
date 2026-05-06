# 05 - Variables, errors, and retries

## Goal

Use variables safely and understand BPMN Error vs Failure vs Incident.

## 1) Variables

Variables are runtime data. You can set/update them when:

- starting a process
- completing a user task / external task
- inspecting in Cockpit (runtime/history varies by UI)

Recommendations:

- prefer simple types (String/Number/Boolean)
- keep variable names stable across versions

## 2) BPMN Error vs Failure vs Incident

### BPMN Error (expected business error)

- modeled and handled (boundary error event)
- used for alternate business flows

### Failure (execution failure, retryable)

- common for transient external dependencies
- External Task workers can call `handleFailure` with retries/backoff

### Incident

- failure not handled by model/retry
- visible in Cockpit, requires intervention

## 3) External Task retries mindset

A typical strategy:

- start retries at 3
- decrement on each failure
- set a retry timeout (next time the job becomes fetchable)

## Checklist

- You can explain BPMN Error vs Incident
- You know workers control retries for External Tasks
- You understand variables are core process state

## Next

Continue to [06 - Versioning and migration mindset](06-versioning-and-migration.md).
