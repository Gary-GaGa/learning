# Troubleshooting: decision trees for Keycloak

Auth errors are notoriously vague ("401" / "Forbidden"). These trees help you locate the cause fast.

> First move: **always decode the token**. Looking at it solves 50% of issues. `06-debugging-and-tools` has a one-liner Python decoder. Or use `jq` / jwt.io (**never paste production tokens**).

## 1. API returns 401 Unauthorized

```mermaid
flowchart TD
  A[API returns 401] --> B{Was a token sent?}
  B -->|No| B1[Client missing Authorization: Bearer ... header]
  B -->|Yes| C{Token expired?}
  C -->|Yes| C1[Refresh the token<br/>access tokens default to 5–60 min]
  C -->|No| D{Does API issuer-uri match<br/>token's iss?}
  D -->|Mismatch| D1[Spring Boot application.yml<br/>spring.security.oauth2.resourceserver.jwt.issuer-uri<br/>must equal the iss in the token, byte for byte]
  D -->|Match| E{Can API reach<br/>JWKs endpoint?}
  E -->|No| E1[Keycloak down / network blocked<br/>curl issuer-uri/.well-known/openid-configuration]
  E -->|Yes| F{Signature verification fails?}
  F --> F1[Realm rebuilt and keys rotated<br/>Restart API to refetch JWKs]
```

### Quick lookup

| Message | Usual cause |
| --- | --- |
| `Bearer error="invalid_token", "Signed JWT rejected: Invalid signature"` | Issuer fine but JWKs cache stale / realm rebuilt. Restart API |
| `Jwt expired at ...` | Token expired, fetch a new one |
| `Couldn't retrieve remote JWK set: ...` | Keycloak down, wrong issuer-uri, or container hostnames not aligned |

## 2. API returns 403 Forbidden

403 means token is valid but **not allowed**. Usually tenant / scope / role insufficient.

```mermaid
flowchart TD
  A[API returns 403] --> B{What does the log say?}
  B -->|tenant mismatch| C[X in URL /t/X/...<br/>doesn't equal token's tenant_id]
  B -->|missing scope/role| D[Token lacks the required SCOPE_xxx<br/>or ROLE_xxx]
  B -->|nothing logged| E[Add an access denied handler<br/>in SecurityConfig to surface details]

  C --> C1[Check user's tenantId attribute<br/>matches the URL]
  C --> C2[Mapper not applied:<br/>did chapter 04 client scope get added to client?]

  D --> D1{Is mapper a client scope mapper?}
  D1 -->|Yes| D2[Client scope must be Default type<br/>and added to client's Default scopes]
  D1 -->|No| D3[Realm role / Client role mappers<br/>must be added explicitly]
  D --> D4[Role name prefix correct?<br/>SCOPE_ for scope strings<br/>ROLE_ for realm/client roles<br/>JwtAuthenticationConverter wired correctly?]
```

## 3. Token is missing the `tenant_id` claim

```mermaid
flowchart TD
  A["Decoded token has no tenant_id"] --> B{Does user have<br/>tenantId attribute?}
  B -->|No| B1[Users → alice → Attributes<br/>add key=tenantId value=acme]
  B -->|Yes| C{Is mapper configured correctly?}
  C --> C1[Mapper type: User Attribute<br/>User Attribute: tenantId<br/>Token Claim Name: tenant_id<br/>Add to access token: ON]
  C --> D{Where is the mapper attached?}
  D -->|On Client's own Mappers| D1[Client → api → Client scopes / Mappers<br/>OK]
  D -->|On a Client scope| D2[The client scope must be added to<br/>the client's Default or Optional scopes]
  D2 --> D3{Default or Optional?}
  D3 -->|Default| D4[Always in token]
  D3 -->|Optional| D5[Caller must request scope=tenant<br/>or it won't be included]
```

## 4. Spring Boot won't start / can't connect

```mermaid
flowchart TD
  A[Spring Boot fails to start] --> B{Error message?}
  B -->|"Failed to introspect Configuration"| B1[Wrong issuer-uri<br/>or Keycloak not yet up]
  B -->|"Connection refused"| B2{localhost or<br/>keycloak:8080?}
  B2 -->|Spring Boot on host, Keycloak in docker| B3[issuer-uri: http://localhost:8080/realms/demo]
  B2 -->|Both in docker compose| B4[issuer-uri: http://keycloak:8080/realms/demo]
  B -->|"PortInUse"| B5[Port 8081 already in use<br/>change server.port]
  B -->|JWKs SSL error| B6[Use https in prod, http in local<br/>don't mix]
```

> **Issuer must match exactly** — including trailing slash, http vs https, hostname casing.

## 5. 401 at the token endpoint (grant fails)

```mermaid
flowchart TD
  A[POST /protocol/openid-connect/token returns 401] --> B{Client authentication on?}
  B -->|No| B1[No secret needed<br/>but still need Direct access grants enabled]
  B -->|Yes confidential| B2{client_secret sent?}
  B2 -->|No| B3[401 invalid_client]
  B2 -->|Yes| C{Direct access grants flow on?}
  C -->|No| C1[Clients → api → Capability config<br/>tick Direct access grants]
  C -->|Yes| D{User credentials correct?}
  D --> D1[Which realm is the user in?<br/>Token URL must be realms/demo not master]
```

## 6. CORS / browser login fails

| Symptom | Cause |
| --- | --- |
| Browser console `CORS error` | Client's `Web origins` not set. Use `+` (mirror redirect URIs) or list your frontend origin |
| `Invalid redirect URI` after redirect | `Valid redirect URIs` doesn't include the actual URL. Add the full URL (with path) |
| No access token after login | Used implicit flow (deprecated). Switch to Authorization Code + PKCE |

## 7. Realm / keys rebuilt and everything broke

`docker compose down -v` wipes the Postgres volume — realm and signing keys gone. After rebuilding:

- All previously-issued tokens become invalid-signature
- API may have cached old JWKs
- Restart the API and re-issue tokens

## 8. Still stuck

1. Decode the token from `06-debugging-and-tools` and **inspect every field**: `iss`, `aud`, `exp`, `scope`, `tenant_id`
2. Enable Keycloak admin event log (Realm settings → Events → Admin events)
3. In Spring Boot, set `logging.level.org.springframework.security=DEBUG` to see filter chain decisions
4. Use `curl -v` to watch the token endpoint flow byte by byte
