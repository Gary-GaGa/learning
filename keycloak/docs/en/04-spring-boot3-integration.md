# 04 - Spring Boot 3 Resource Server integration

## Goal

Validate Keycloak JWT with Spring Boot 3 + Spring Security, and implement:

- Read the claim `tenant_id`
- Map roles / scopes into Spring Authorities
- Enforce URL path `/t/{tenant}` must match token `tenant_id`

## Demo project

This repo includes a demo API:

- [spring-boot-demo/](../../spring-boot-demo/)

It contains:

- `SecurityFilterChain` with `oauth2ResourceServer().jwt()`
- `JwtAuthenticationConverter` for roles/scopes mapping
- A filter that extracts `{tenant}` from URL and validates it

## Keycloak prerequisites

- Realm: `demo`
- Client: `api`
- User: `alice`
- Token claim: `tenant_id`

## How to run

1. Start Keycloak (Chapter 01)
2. Set `alice` attribute `tenantId=acme` (Chapter 03)
3. In `spring-boot-demo/`:

- `mvn spring-boot:run`

4. Call APIs after getting a token:

- `GET http://localhost:8081/t/acme/me`
- `GET http://localhost:8081/t/acme/reports`

Next chapter covers scopes/roles and the hybrid fine-grained authorization approach.

## Next

Continue to [05 - Fine-grained authorization (hybrid: Keycloak + API)](05-fine-grained-hybrid-authorization.md).
