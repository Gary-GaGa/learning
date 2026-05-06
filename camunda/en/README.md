# Camunda Tutorial (English, Camunda 7 first)

> Language: [中文](../zh/README.md) ｜ **English**

From BPMN core concepts to a runnable process (Camunda 7 first), then a comparison with Camunda 8 and migration notes.

## Start here

🗺️ **First time? Read [00-overview.md](./00-overview.md)** — BPMN / engine / worker model, learning paths, Camunda 7 vs 8 cheat sheet, glossary.

🆘 **Stuck? See [troubleshooting.md](./troubleshooting.md)** — decision trees for "process won't start", stuck user task, External Task not picked up, incidents, and more.

## Contents

| # | Topic | Link |
| --- | --- | --- |
| 00 | Overview (topic map / paths / glossary) | [00-overview.md](./00-overview.md) |
| 01 | Core concepts (Camunda 7) | [01-core-concepts.md](./01-core-concepts.md) |
| 02 | Docker quickstart (Camunda 7 Run) | [02-quickstart-docker.md](./02-quickstart-docker.md) |
| 03 | Build & deploy your first process (Hello User Task) | [03-first-process-deploy.md](./03-first-process-deploy.md) |
| 04 | Tasklist and forms / task assignment | [04-tasklist-and-forms.md](./04-tasklist-and-forms.md) |
| 05 | External Task pattern (and workers) | [05-external-task-pattern.md](./05-external-task-pattern.md) |
| 06 | Variables, errors, and retries | [06-variables-errors-retries.md](./06-variables-errors-retries.md) |
| 07 | Versioning and migration mindset | [07-versioning-and-migration.md](./07-versioning-and-migration.md) |
| 08 | Debugging & operations (Cockpit / logs / incidents) | [08-debugging-and-ops.md](./08-debugging-and-ops.md) |
| 09 | Camunda 8 vs 7 (architecture / execution model) | [09-camunda8-differences.md](./09-camunda8-differences.md) |
| 10 | Camunda 8 extensions: workers, Operate, Connectors, migration tips | [10-camunda8-extensions.md](./10-camunda8-extensions.md) |
| 🆘 | Troubleshooting: decision trees for common issues | [troubleshooting.md](./troubleshooting.md) |

## What you will achieve

- Run Camunda 7 locally via Docker (engine + webapps)
- Deploy BPMN via Cockpit and work user tasks in Tasklist
- Run an External Task with a Node.js worker
- Understand variables, errors, retries, and incidents
- Compare to Camunda 8 (Zeebe / Job Worker / Operate)
