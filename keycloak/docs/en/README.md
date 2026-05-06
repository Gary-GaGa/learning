# Keycloak Tutorial (English)

This folder is a guided learning path that focuses on multi-tenancy, token claims, and Spring Boot 3 integration.

## Learning Path (recommended order)

00. [Core concepts (Realm / Client / User / Role / Group / Token)](00-core-concepts.md)
01. [Docker quickstart](01-quickstart-docker.md)
02. [Multi-tenancy (URL path, single realm)](02-multi-tenancy-url-path.md)
03. [Token claims: tenantId & mappers](03-token-claims-tenant.md)
04. [Spring Boot 3 Resource Server integration](04-spring-boot3-integration.md)
05. [Fine-grained authorization (hybrid: Keycloak + API)](05-fine-grained-hybrid-authorization.md)
06. [Debugging, checks, and tools](06-debugging-and-tools.md)
07. [Production notes (short)](07-production-notes.md)

## What you will achieve

- Run Keycloak + Postgres locally via Docker
- Manually configure realm/client/users/groups/roles
- Add `tenantId` into access token claims (e.g. `tenant_id`)
- Validate JWT in Spring Boot 3 and read `tenant_id`
- Enforce `{tenant}` from URL path to match token claim
- Use scope/roles for feature authorization; keep data-row checks in the API
