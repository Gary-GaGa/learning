# 07 - Production 注意事項（簡版）

這章是「從學習到上線」的落差提醒。你現在先手動操作熟悉概念，等全套跑通再考慮自動化。

## 不要沿用學習用設定

- 不要在正式環境開啟 Direct access grants（password grant）
- 不要使用弱密碼/共用 admin 帳號

## 資料庫

- 正式環境請使用外部 Postgres（或你組織標準 DB）
- 規劃備份/還原與升級流程

## TLS 與反向代理

- 正式環境務必使用 TLS
- Keycloak 常見放在反向代理後（Nginx/Ingress），要注意 hostname 設定

## Realm 設定管理

- 建議用 Realm 匯出/匯入或 IaC（例如 Terraform）管理設定
- 對多租戶（單 Realm）要制定命名規範（groups/roles/scopes）

## Token 與安全

- 盡量縮短 access token 生命週期
- 避免把敏感資料塞進 token claim
- 多租戶強制規則：URL `{tenant}` 與 token `tenant_id` 一致

## 觀測性

- 需要 audit log、登入失敗監控、管理端操作追蹤
