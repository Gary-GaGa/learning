# 03 - Token claim：tenantId 與 Mapper（手動）

## 目標

把 Keycloak user attribute `tenantId` 映射到 access token claim `tenant_id`。

## 1) 建立測試使用者

在 `demo` realm：

1. Users → Create new user
2. Username：`alice`
3. Create
4. Credentials：設定密碼（臨時關閉 Temporary）

## 2) 設定使用者 attribute

在 `alice` 的 Attributes：

- Key：`tenantId`
- Value：`acme`

## 3) 建立 Client（給後端 API 使用）

Clients → Create client

- Client type: OpenID Connect
- Client ID: `api`

建議設定（學習用）：

- Client authentication: ON（讓它是 confidential client）
- Standard flow: OFF（API 不需要瀏覽器登入流程）
- Direct access grants: ON（僅學習用，正式環境不建議）

> 後續你也可以改成搭配前端用 Authorization Code + PKCE。

## 4) 建立 Mapper（把 attribute 放到 token）

做法 A（建議）：用 Client scope 管理 Mapper

1. Client scopes → Create client scope
   - Name：`tenant`
   - Type：Default
2. 進入該 client scope → Mappers → Create mapper
   - Mapper type：User Attribute
   - User Attribute：`tenantId`
   - Token Claim Name：`tenant_id`
   - Claim JSON Type：String
   - Add to access token：ON
   - Add to ID token：可選
3. 回到 Clients → `api` → Client scopes
   - 把 `tenant` 加到 Default client scopes

## 5) 驗證 token（學習用）

你可以先用 Direct access grants 拿 token（僅學習）：

- 先到 Clients → `api` → Credentials，記下 Client secret
- 用 curl 取得 token（下一章 Spring Boot 也會用）：

```bash
curl -s \
  -d 'grant_type=password' \
  -d 'client_id=api' \
  -d 'client_secret=...填入...' \
  -d 'username=alice' \
  -d 'password=...填入...' \
  http://localhost:8080/realms/demo/protocol/openid-connect/token | jq .
```

檢核：把 access token 丟到 jwt.io 或用 `jq` 解開（不要上傳正式環境 token），你應該看到 `tenant_id: "acme"`。

## 下一步

繼續到 [04 - Spring Boot 3 Resource Server 整合](04-spring-boot3-integration.md)。
