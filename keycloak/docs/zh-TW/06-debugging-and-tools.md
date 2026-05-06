# 06 - 除錯、檢核與工具

## 目標

當你「拿不到 token / token 沒有你想要的 claim / API 一直 401/403」時，有一套固定的檢核流程。

## 快速檢核清單

1. Keycloak 服務正常嗎？
   - 管理端 http://localhost:8080 能打開
2. 你在哪個 Realm？
   - 確認右上角/左上角顯示 `demo`
3. 你的 Client 設定對嗎？
   - `api` 是否開啟你用到的 flow（學習用：Direct access grants）
4. Mapper 是否生效？
   - `tenant_id` 是否真的出現在 access token
5. API 驗證的 `issuer` 是否一致？
   - API 端 `issuer-uri` 必須對上 `http://localhost:8080/realms/demo`

## 解 token（本機）

JWT 是三段 base64url。你可以用本機方式解 payload（不要把正式環境 token 丟到第三方網站）：

```bash
python3 - <<'PY'
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

使用方式：

- `TOKEN='eyJ...' python3 ...`

你應該能看到：

- `iss`（發行者）
- `aud`（受眾，可能是字串或陣列）
- `exp`（過期時間）
- `scope`（若有）
- `tenant_id`（本教學關鍵）

## 常見 401 / 403 的原因

### 401 Unauthorized（通常是「沒驗過」）

- token 過期
- API `issuer-uri` 配錯 realm
- API 端驗簽拿不到 JWKs（Keycloak 沒起來/網路問題）

### 403 Forbidden（通常是「驗過但不允許」）

- scope/role 不足
- URL path 的 `{tenant}` 與 `tenant_id` 不一致（本教學刻意要求）

## 建議加入的實作習慣

- 在 API log 裡記錄（不含敏感資料）：`sub`、`tenant_id`、`scope`、拒絕原因
- 先把租戶比對做成一個共用 filter/middleware，再做業務授權

## 下一步

繼續到 [07 - Production 注意事項（簡版）](07-production-notes.md)。
