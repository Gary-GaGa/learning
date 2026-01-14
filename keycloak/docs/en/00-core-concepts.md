# 00 - Core concepts: Realm / Client / User / Role / Group / Token

This chapter helps you map Keycloak terminology to what you actually need to build: authentication + authorization for an API.

## Realm

- **What it is**: an isolated security domain inside Keycloak.
- **What you use it for**:
  - Store users, clients, roles, groups, and settings.
  - Realms are isolated from each other (no sharing of users/settings).
- **In this tutorial**: we use a single realm: `demo`.

## Diagram: how the pieces connect (this tutorial)

```mermaid
flowchart TD
  subgraph R[Realm: demo]
    U[User: alice\nattribute: tenantId=acme]
    G[Group: /tenants/acme]
    C[Client: api]
    M[Mapper\nUser attribute tenantId -> claim tenant_id]
    RO[Roles/Scopes\n(reports:read, reports:write)]
  end

  U --> G
  C --> M
  M --> T[Access Token (JWT)\nclaim: tenant_id]
  RO --> T

  T --> API[Spring Boot 3 API\nURL: /t/{tenant}/...]
  API --> CHECK{Check}
  CHECK -->|tenant_id == {tenant}| OK[Continue]
  CHECK -->|mismatch| DENY[403 Forbidden]
```

## Client

- **What it is**: an application/service that uses Keycloak to authenticate and/or obtain tokens.
- **Typical shapes**:
  - **Backend API**: treated as a Resource Server; key task is validating access tokens.
  - **Frontend**: usually uses Authorization Code + PKCE.
  - **Machine-to-machine**: often uses Client Credentials.
- **In this tutorial**: a client named `api` (used for learning, scopes/roles, and token issuance).

## User

- **What it is**: a principal that can log in and obtain tokens.
- **What you use it for**:
  - Assign roles, add to groups, set attributes.
  - Use mappers to project attributes into token claims.
- **In this tutorial**: user `alice` with attribute `tenantId=acme`.

## Group

- **What it is**: a hierarchical way to organize users.
- **What you use it for**:
  - Easier administration (e.g., departments, tenants).
  - Assign roles to groups to manage permissions at scale.
- **In this tutorial (multi-tenancy)**:
  - Use a tenant group hierarchy like `/tenants/acme`.
  - Mainly to make tenant membership obvious in the Admin Console.

## Role

- **What it is**: a named permission marker.
- **Common scopes**:
  - **Realm role**: global within the realm.
  - **Client role**: specific to a client.
- **What you use it for**:
  - RBAC (Role-Based Access Control).
  - Feature-level authorization.
- **Role vs scope (intuition)**:
  - role: “who you are / what kind of user you are”
  - scope: “what actions you’re allowed to do”

## Token

Keycloak issues tokens via OIDC/OAuth2. The common ones are:

### Access Token

- **Purpose**: sent to your backend API; the API validates it.
- **Content**: typically a JWT with claims (e.g. `sub`, `iss`, `aud`, `exp`, `scope`, roles).
- **In this tutorial**: includes `tenant_id` claim.

### ID Token

- **Purpose**: primarily for “login” flows (frontend identity).
- **In this tutorial**: not required for backend API authorization.

### Refresh Token

- **Purpose**: used to get a new access token.
- **Note**: typically used by long-lived clients like frontends.

## Claim / Scope

- **Claim**: a field inside the token.
  - Key claim here: `tenant_id`
- **Scope**: allowed action scopes.
  - We’ll use scopes like `reports:read` and `reports:write`.

## Minimal mental model for this tutorial

1. In realm `demo`, create client `api`
2. Set `alice` attribute `tenantId=acme`
3. Use a mapper to add `tenant_id` into the access token
4. Tenant APIs use `/t/{tenant}/...`
5. API enforces `{tenant}` == token `tenant_id`
6. Use scope/roles for feature authorization, and enforce record-level checks in the API/data layer
