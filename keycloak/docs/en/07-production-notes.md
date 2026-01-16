# 07 - Production notes (short)

## Goal

Gap-check between learning configs and production.

## Do not reuse learning settings

- Do not enable Direct access grants (password grant) in production
- Do not use weak/shared admin credentials

## Database

- Use an external Postgres (or your org standard DB)
- Plan backup/restore and upgrade procedures

## TLS and reverse proxy

- Use TLS in production
- When running behind a reverse proxy/ingress, configure hostnames correctly

## Manage realm configuration

- Use realm export/import or IaC (e.g., Terraform)
- For single-realm multi-tenancy, enforce naming conventions (groups/roles/scopes)

## Tokens and security

- Keep access tokens short-lived
- Avoid placing sensitive data into token claims
- Multi-tenancy rule: URL `{tenant}` must match token `tenant_id`

## Observability

- Audit logs, failed login monitoring, admin actions tracking
