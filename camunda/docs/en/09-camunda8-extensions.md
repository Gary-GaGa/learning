# 09 - Camunda 8 extensions: workers, Operate, Connectors, migration tips

Goal: build a mental map of what to learn next in Camunda 8 and how to evaluate migration from 7.

## 1) Job Workers (Camunda 8)

Think of it as the “next-gen” approach to External Tasks.

Key conceptual differences:

- not REST-based fetchAndLock
- uses Zeebe job mechanism and worker SDKs

## 2) Operate

- focuses on observability and troubleshooting
- helps inspect instances and operational state

## 3) Connectors

- standardizes common integrations (HTTP/events/data)
- aims to reduce custom glue code and improve governance

## 4) Migration evaluation (7 → 8)

Start with:

1. Do you truly need Camunda 8 scalability/throughput?
2. Can you afford multi-component deployment and ops?
3. What dominates your processes?
   - user tasks
   - or heavy automation (workers)

## 5) Minimal migration strategy (learning)

- pick one low-risk process as a PoC
- move integrations into workers (reduce engine coupling)
- establish observability/alerting early

## Checklist

- You understand the role of Job Workers in Camunda 8
- You know why Operate/Connectors matter
- You approach migration as a cost/benefit decision
