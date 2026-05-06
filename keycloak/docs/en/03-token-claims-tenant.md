# 03 - Token claims: tenantId & mappers (manual)

## Goal

Map the user attribute `tenantId` into the access token claim `tenant_id`.

## 1) Create a test user

In the `demo` realm:

1. Users → Create new user
2. Username: `alice`
3. Create
4. Credentials: set password (turn off Temporary)

## 2) Set the user attribute

In `alice` → Attributes:

- Key: `tenantId`
- Value: `acme`

## 3) Create a client (for backend API)

Clients → Create client

- Client type: OpenID Connect
- Client ID: `api`

Suggested for learning:

- Client authentication: ON (confidential client)
- Standard flow: OFF
- Direct access grants: ON (learning only; avoid in production)

## 4) Create a mapper (attribute → token)

Recommended: manage mappers via a Client scope.

1. Client scopes → Create client scope
   - Name: `tenant`
   - Type: Default
2. In that client scope → Mappers → Create mapper
   - Mapper type: User Attribute
   - User Attribute: `tenantId`
   - Token Claim Name: `tenant_id`
   - Claim JSON Type: String
   - Add to access token: ON
   - Add to ID token: optional
3. Clients → `api` → Client scopes
   - Add `tenant` to Default client scopes

## 5) Verify token (learning only)

Use Direct access grants to get a token (learning only):

- Clients → `api` → Credentials: copy Client secret

```bash
curl -s \
  -d 'grant_type=password' \
  -d 'client_id=api' \
  -d 'client_secret=...secret...' \
  -d 'username=alice' \
  -d 'password=...password...' \
  http://localhost:8080/realms/demo/protocol/openid-connect/token | jq .
```

Check the access token payload contains `tenant_id: "acme"`.

## Next

Continue to [04 - Spring Boot 3 Resource Server integration](04-spring-boot3-integration.md).
