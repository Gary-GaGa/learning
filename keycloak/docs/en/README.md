# Keycloak Tutorial (English)

## Learning Path (recommended order)

0. [00 - Core concepts (Realm / Client / User / Role / Group / Token)](00-core-concepts.md)
1. [01 - Docker Quickstart](01-quickstart-docker.md)
2. [02 - Multi-tenancy (URL path, single realm)](02-multi-tenancy-url-path.md)
3. [03 - Token claims: tenantId & mappers](03-token-claims-tenant.md)
4. [04 - Spring Boot 3 Resource Server integration](04-spring-boot3-integration.md)
5. [05 - Fine-grained authorization (hybrid: Keycloak + API)](05-fine-grained-hybrid-authorization.md)
6. [06 - Debugging, checks, and tools](06-debugging-and-tools.md)
7. [07 - Production notes (short)](07-production-notes.md)

## What you will achieve

- Run Keycloak + Postgres locally via Docker
- Manually configure realm/client/users/groups/roles
- Add `tenantId` into access token claims (e.g. `tenant_id`)
- Validate JWT in Spring Boot 3 and read `tenant_id`
- Enforce `{tenant}` from URL path to match token claim
- Use scope/roles for feature authorization; keep data-row checks in the API
