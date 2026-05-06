# Keycloak Tutorial (English)

> Language: [中文](../zh/README.md) ｜ **English**

A focused learning path on **multi-tenancy (URL path) + Token claims + Spring Boot 3**, with runnable Docker Compose and Spring Boot 3 examples.

## Start here

🗺️ **First time? Read [00-overview.md](./00-overview.md)** — one picture for how all topics connect, learning paths, glossary.

🆘 **Stuck? See [troubleshooting.md](./troubleshooting.md)** — decision trees for 401/403, missing token claim, tenant mismatch, and more.

## Contents

| # | Topic | Link |
| --- | --- | --- |
| 00 | Overview (topic map / paths / glossary) | [00-overview.md](./00-overview.md) |
| 01 | Core concepts (Realm / Client / User / Role / Group / Token) | [01-core-concepts.md](./01-core-concepts.md) |
| 02 | Docker quickstart (Keycloak + Postgres) | [02-quickstart-docker.md](./02-quickstart-docker.md) |
| 03 | Multi-tenancy (URL path, single realm) | [03-multi-tenancy-url-path.md](./03-multi-tenancy-url-path.md) |
| 04 | Token claims: tenantId & mappers | [04-token-claims-tenant.md](./04-token-claims-tenant.md) |
| 05 | Spring Boot 3 Resource Server integration | [05-spring-boot3-integration.md](./05-spring-boot3-integration.md) |
| 06 | Fine-grained authorization (hybrid: Keycloak + API) | [06-fine-grained-hybrid-authorization.md](./06-fine-grained-hybrid-authorization.md) |
| 07 | Debugging, checks, and tools | [07-debugging-and-tools.md](./07-debugging-and-tools.md) |
| 08 | Production notes (short) | [08-production-notes.md](./08-production-notes.md) |
| 🆘 | Troubleshooting: decision trees for common issues | [troubleshooting.md](./troubleshooting.md) |

## What you will achieve

- Run Keycloak + Postgres locally via Docker
- Manually configure realm / client / users / groups / roles
- Add `tenantId` into access token claims (e.g. `tenant_id`)
- Validate JWT in Spring Boot 3 and read `tenant_id`
- Enforce that `{tenant}` from the URL path matches the token claim
- Use scope/roles for feature-level authorization; keep data-row checks in the API
