# 00 - 核心概念：Realm / Client / User / Role / Group / Token

這章的目標是讓你能把 Keycloak 的「名詞」對應到「你要做的權限系統」。你不需要一次背完，但要知道每個東西拿來做什麼。

## Realm（領域）

- **它是什麼**：Keycloak 內的一個隔離空間（像一個獨立的身分與授權域）。
- **你會用它做什麼**：
  - 放你的使用者、Client、Role、Group、設定。
  - 不同 Realm 彼此隔離（使用者、設定不共享）。
- **本教學怎麼用**：用一個 Realm：`demo`。

## 流程圖：物件之間的關係（本教學）

```mermaid
flowchart TD
  subgraph R[Realm: demo]
    U["User: alice<br/>attribute: tenantId=acme"]
    G[Group: /tenants/acme]
    C[Client: api]
    M["Mapper<br/>User attribute tenantId -> claim tenant_id"]
    RO["Roles/Scopes<br/>(reports:read, reports:write)"]
  end

  U --> G
  C --> M
  M --> T["Access Token (JWT)<br/>claim: tenant_id"]
  RO --> T

  T --> API["Spring Boot 3 API<br/>URL: /t/{tenant}/..."]
  API --> CHECK{Check}
  CHECK -->|tenant_id == {tenant}| OK[Continue]
  CHECK -->|mismatch| DENY[403 Forbidden]
```

## Client（應用程式/服務）

- **它是什麼**：代表「會使用 Keycloak 做登入/拿 token」的應用或服務。
- **常見類型（概念上）**：
  - **後端 API**：通常把 API 視為 Resource Server，重點是「驗證 token」。
  - **前端**：通常需要瀏覽器登入流程（Authorization Code + PKCE）。
  - **機器對機器**：常見用 Client Credentials。
- **本教學怎麼用**：建立一個 Client：`api`，用來發 token（學習用）與承載 scopes/roles。

## User（使用者）

- **它是什麼**：能登入與取得 token 的主體。
- **你會用它做什麼**：
  - 指派 role、加入 group、設定 attributes。
  - 透過 Mapper 把 attributes 放進 token claim。
- **本教學怎麼用**：建立 `alice`，並設定 attribute：`tenantId=acme`。

## Group（群組）

- **它是什麼**：用來管理使用者的集合，可有階層結構。
- **你會用它做什麼**：
  - 批次把使用者歸類（例如依部門、租戶）。
  - 可以讓 role 指派更好管理（把 role 給 group）。
- **本教學怎麼用（多租戶）**：
  - 建議 group 階層：`/tenants/acme`、`/tenants/umbrella`。
  - 主要讓你在管理端一眼看懂「這個人屬於哪個租戶」。

## Role（角色）

- **它是什麼**：一種「能力/權限」的標記。
- **兩種常見範圍**：
  - **Realm role**：在整個 Realm 內通用。
  - **Client role**：綁定在特定 Client（例如 `api`）上。
- **你會用它做什麼**：
  - 做 RBAC（Role-Based Access Control）。
  - 常用在功能面授權（例如 `report_admin`）。
- **與 scope 的差異（直覺版）**：
  - role：偏「你是誰/你是哪一種人」。
  - scope：偏「你可以做什麼操作」。

## Token（權杖）

Keycloak 在 OIDC/OAuth2 中會發 token。最常用的是：

### Access Token

- **用途**：呼叫後端 API 時帶著它（API 端驗證它）。
- **內容**：通常是 JWT，裡面包含 claims（例如 `sub`、`iss`、`aud`、`exp`、`scope`、roles 等）。
- **本教學重點**：會包含 `tenant_id` claim（由 `tenantId` attribute 映射而來）。

### ID Token

- **用途**：偏向「登入」情境給前端使用，用來描述使用者身分。
- **本教學**：後端 API 情境通常不需要依賴它。

### Refresh Token

- **用途**：用來換新的 access token。
- **注意**：通常只給需要長期維持登入的 client（例如前端）。

## Claim / Scope（你會一直看到的兩個字）

- **Claim**：token 內的一個欄位。
  - 本教學關鍵 claim：`tenant_id`
- **Scope**：代表允許的操作範圍。
  - 本教學會用類似：`reports:read`、`reports:write`

## 這些概念如何串起來（本教學的最小心智模型）

1. 你在 Realm `demo` 裡建立 Client `api`
2. 你在 `alice` 身上設定 `tenantId=acme`
3. 你用 Mapper 把 `tenantId` 放進 access token 的 claim：`tenant_id`
4. 你的 API URL 使用 `/t/{tenant}/...`
5. API 端驗證：`{tenant}` 必須等於 token 的 `tenant_id`
6. 再用 scope/role 決定可不可以讀/寫，並在資料層確認資料真的屬於該租戶
