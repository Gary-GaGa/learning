# 06 - Debugging, checks, and tools

Goal: have a repeatable checklist when you can’t get a token, claims are missing, or the API keeps returning 401/403.

## Quick checklist

1. Is Keycloak up?
   - Admin Console http://localhost:8080 loads
2. Are you in the right realm?
   - Confirm it’s `demo`
3. Is the client configured for your flow?
   - For learning: Direct access grants
4. Is the mapper effective?
   - Confirm `tenant_id` exists in the access token
5. Does the API validate the correct issuer?
   - API `issuer-uri` must be `http://localhost:8080/realms/demo`

## Decode a token locally

Do not paste production tokens into third-party sites. Decode JWT payload locally:

```bash
python - <<'PY'
import base64, json, os

token = os.environ.get('TOKEN')
if not token:
    raise SystemExit('Set TOKEN env var')

parts = token.split('.')
if len(parts) != 3:
    raise SystemExit('Not a JWT')

payload = parts[1]
payload += '=' * (-len(payload) % 4)
print(json.dumps(json.loads(base64.urlsafe_b64decode(payload).decode()), indent=2))
PY
```

You should see:

- `iss`, `aud`, `exp`
- `scope` (if present)
- `tenant_id` (key for this tutorial)

## Common 401 / 403 causes

### 401 Unauthorized (usually “not validated”)

- expired token
- wrong `issuer-uri`
- cannot fetch JWKs (Keycloak down/network)

### 403 Forbidden (usually “validated but not allowed”)

- missing scope/role
- URL `{tenant}` does not match token `tenant_id` (intentional in this tutorial)

## Recommended practices

- Log (without sensitive data): `sub`, `tenant_id`, `scope`, and rejection reason
- Implement tenant matching once as a shared filter/middleware, then build business authorization on top
