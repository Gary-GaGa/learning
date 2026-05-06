# Troubleshooting：Keycloak 常見問題決策樹

身份驗證錯誤的訊息常常很模糊（"401" / "Forbidden"）。這篇用決策樹幫你快速定位。

> 起手式：**永遠先解 token**。看 token 內容你 50% 的問題就明朗了。`06-debugging-and-tools` 有解 JWT 的 Python 一行命令；或用 `jq` / 線上 jwt.io（**不要丟正式 token**）。

## 1. API 回 401 Unauthorized

```mermaid
flowchart TD
  A[呼叫 API 收到 401] --> B{Token 有送出嗎?}
  B -->|否| B1[client 沒帶 Authorization: Bearer ...]
  B -->|是| C{Token 過期了嗎?}
  C -->|是| C1[拿新 token<br/>access token 預設只有 5–60 分鐘]
  C -->|否| D{API 端 issuer-uri<br/>對得上 token 的 iss?}
  D -->|不一致| D1[Spring Boot application.yml<br/>spring.security.oauth2.resourceserver.jwt.issuer-uri<br/>必須完全等於 token 的 iss]
  D -->|一致| E{API 能連到 Keycloak<br/>JWKs endpoint?}
  E -->|不能| E1[Keycloak 沒起來 / 網路擋住<br/>curl issuer-uri/.well-known/openid-configuration]
  E -->|能| F{Token 簽章驗證失敗?}
  F --> F1[realm 重建過、key 換了<br/>API 重啟讓它重新拉 JWKs]
```

### 速查

| 訊息 | 多半的原因 |
| --- | --- |
| `Bearer error="invalid_token", error_description="An error occurred while attempting to decode the Jwt: Signed JWT rejected: Invalid signature"` | issuer 對但 JWKs 過期 / realm 重建。重啟 API |
| `An error occurred while attempting to decode the Jwt: Jwt expired at ...` | Token 過期，重拿 |
| `Couldn't retrieve remote JWK set: ...` | Keycloak 沒起來、issuer-uri 配錯、容器網路名稱沒對齊 |

## 2. API 回 403 Forbidden

403 表示驗過了，但**不允許**。多半是 tenant / scope / role 不夠。

```mermaid
flowchart TD
  A[API 回 403] --> B{在 log 看到哪一段?}
  B -->|tenant mismatch| C[URL /t/X/... 裡的 X<br/>跟 token tenant_id 不一樣]
  B -->|missing scope/role| D[token 內沒有需要的 SCOPE_xxx<br/>或 ROLE_xxx]
  B -->|看不到細節| E[API 沒打 log；先到 SecurityConfig<br/>加上 access denied handler]

  C --> C1[檢查 user 的 tenantId attribute<br/>跟 URL 應該一致]
  C --> C2[Mapper 沒套上去:<br/>04 章的 client scope 加到 client?]

  D --> D1{Mapper 是不是 client scope mapper?}
  D1 -->|是| D2[client scope 要設成 Default<br/>且加到 client 的 Default scopes]
  D1 -->|否| D3[Realm role / Client role mapper<br/>要明確加到 token]
  D --> D4[role 名稱 prefix 對嗎?<br/>SCOPE_ 是 scope 字串<br/>ROLE_ 是 realm/client role<br/>JwtAuthenticationConverter 設定有對嗎]
```

## 3. Token 裡沒有 `tenant_id` claim

```mermaid
flowchart TD
  A["jq / jwt.io 解開 token<br/>沒看到 tenant_id"] --> B{User 有 attribute<br/>tenantId 嗎?}
  B -->|沒有| B1[Users → alice → Attributes<br/>加上 key=tenantId, value=acme]
  B -->|有| C{Mapper 設定對嗎?}
  C --> C1[Mapper type: User Attribute<br/>User Attribute: tenantId<br/>Token Claim Name: tenant_id<br/>Add to access token: ON]
  C --> D{Mapper 在哪裡?}
  D -->|在 Client 的 Mappers| D1[Client → api → Client scopes / Mappers<br/>OK]
  D -->|在 Client scope| D2[Client scope 必須加到<br/>Client 的 Default 或 Optional scopes]
  D2 --> D3{Default 還是 Optional?}
  D3 -->|Default| D4[會自動進 token]
  D3 -->|Optional| D5[需要在拿 token 時帶 scope=tenant<br/>否則不會進去]
```

## 4. Spring Boot 啟動失敗 / 一直連不上

```mermaid
flowchart TD
  A[Spring Boot 起不來] --> B{錯誤訊息?}
  B -->|"Failed to introspect Configuration"| B1[issuer-uri 配錯<br/>或 Keycloak 還沒起來]
  B -->|"Connection refused"| B2{連 localhost 還是<br/>連 keycloak:8080?}
  B2 -->|本機跑 Spring Boot, Keycloak 在 docker| B3[issuer-uri: http://localhost:8080/realms/demo]
  B2 -->|兩個都在 docker compose 裡| B4[issuer-uri: http://keycloak:8080/realms/demo]
  B -->|"Caused by: PortInUse"| B5[port 8081 已被佔用<br/>改 server.port]
  B -->|JWKs SSL 問題| B6[正式環境用 https; 本機用 http<br/>不要混]
```

> **Issuer 兩端必須一字不差**——包含結尾有沒有 `/`、是 `http` 或 `https`、host 名稱大小寫。

## 5. 拿 token 時 401（grant_type 階段就失敗）

```mermaid
flowchart TD
  A[POST /protocol/openid-connect/token 收到 401] --> B{Client authentication 開了嗎?}
  B -->|否| B1[沒開 = 不需 client_secret<br/>但 Direct access grants 仍要開]
  B -->|是 confidential| B2{有帶 client_secret 嗎?}
  B2 -->|沒| B3[401 invalid_client]
  B2 -->|有| C{Direct access grants<br/>flow 開了嗎?}
  C -->|沒開| C1[Clients → api → Capability config<br/>勾 Direct access grants]
  C -->|開了| D{User credentials 對嗎?}
  D --> D1[user 在哪個 realm?<br/>Token URL 要是 realms/demo 不是 master]
```

## 6. CORS / 前端登入失敗

| 症狀 | 原因 |
| --- | --- |
| 瀏覽器 console `CORS error` | Client 的 `Web origins` 沒設好。設 `+`（自動帶 redirect URIs） 或加上你的前端 origin |
| Redirect 後 `Invalid redirect URI` | `Valid redirect URIs` 不含實際的 redirect URL；要加上完整 URL（含 path） |
| 登入後 access token 沒拿到 | 用了 implicit flow（已淘汰）。改用 Authorization Code + PKCE |

## 7. Realm / Key 重建後一切都壞掉

當你 `docker compose down -v` 清了 Postgres volume，realm 連同 signing key 都消失。重建後：

- 之前發出的 token 全部變成 invalid signature
- API 第一次驗 token 時拉 JWKs 會拿到新公鑰，**但已快取舊的就會失敗**
- 重啟 API、重新登入拿新 token

## 8. 仍找不到原因

1. 用 `06-debugging-and-tools` 的 Python 解 token，**逐欄位檢查**：`iss`、`aud`、`exp`、`scope`、`tenant_id`
2. 在 Keycloak 開 admin event log（Realm settings → Events → Admin events）
3. 在 Spring Boot 把 `logging.level.org.springframework.security=DEBUG` 開起來看 filter chain 跑到哪
4. 用 `curl -v` 觀察整個 token endpoint 流程
