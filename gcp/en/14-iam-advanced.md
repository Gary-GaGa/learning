# IAM Advanced

[01-fundamentals](./01-fundamentals.md) covered the basic principal / role / resource model. This topic covers **production-grade controls**: conditional IAM, Org Policy, VPC-SC, custom roles, SA impersonation.

## 1. IAM Conditions (conditional bindings)

A normal binding is "permanent and unconditional." Conditions add **time / resource attribute** constraints.

```bash
# Alice can only access during weekday 08:00–20:00
gcloud projects add-iam-policy-binding PROJECT \
  --member="user:alice@example.com" \
  --role="roles/storage.objectAdmin" \
  --condition='expression=request.time.getHours("Asia/Taipei") >= 8 && request.time.getHours("Asia/Taipei") < 20,title=workhours'

# Only applies to buckets prefixed logs-
... --condition='expression=resource.name.startsWith("projects/_/buckets/logs-"),title=logs-only'
```

Available attributes (selected):

| Attribute | Example |
| --- | --- |
| `request.time` | Time windows |
| `resource.name` / `resource.type` | Restrict to specific resources |
| `resource.matchTag(...)` | GCP **Tags** (not labels) for tag-based control |
| `request.auth.claims.xxx` | OIDC claims (external identities) |

> Not every service supports conditions; check the [conditions support matrix](https://cloud.google.com/iam/docs/conditions-resource-attributes).

### Tags vs Labels

- **Labels**: pure tagging, used for billing / search.
- **Tags**: usable as targets for IAM Conditions and Org Policy; **have a permission model**.

```bash
# Create tag key/value at org level
gcloud resource-manager tags keys create env --parent=organizations/ORG_ID
gcloud resource-manager tags values create prod --parent=tagKeys/KEY_ID

# Bind tag to a project
gcloud resource-manager tags bindings create \
  --tag-value=tagValues/VALUE_ID \
  --parent=//cloudresourcemanager.googleapis.com/projects/PROJECT_NUMBER
```

Then IAM Conditions can use `resource.matchTag('ORG_ID/env', 'prod')`.

## 2. Custom Roles

When predefined roles are too broad or off-target:

```yaml
# role.yaml
title: "Bucket Lister"
description: "Only list buckets"
stage: GA
includedPermissions:
- storage.buckets.list
- storage.buckets.get
```

```bash
gcloud iam roles create bucketLister \
  --project=PROJECT --file=role.yaml
```

> Custom roles aren't shareable across projects (unless created at Org level). High maintenance burden — **try combining predefined roles first**.

## 3. Service Account Impersonation

Best practice replacement for SA keys: humans temporarily borrow an SA's identity.

```bash
# 1. Grant a user permission to impersonate the SA
gcloud iam service-accounts add-iam-policy-binding \
  deployer@PROJECT.iam.gserviceaccount.com \
  --member="user:alice@example.com" \
  --role="roles/iam.serviceAccountTokenCreator"

# 2. Alice runs commands as the SA (no key)
gcloud storage ls gs://prod-bucket \
  --impersonate-service-account=deployer@PROJECT.iam.gserviceaccount.com

# 3. Make it the default identity
gcloud config set auth/impersonate_service_account \
  deployer@PROJECT.iam.gserviceaccount.com
```

Benefits:

- No key file to generate or rotate
- Short-lived tokens (~1h)
- Full audit trail: "Alice did X via deployer SA"

## 4. Workload Identity Federation (WIF)

Lets **external identities** (GitHub Actions, AWS, Azure, OIDC) call GCP **without an SA key**.

GitHub Actions example:

```bash
# 1. Create Workload Identity Pool
gcloud iam workload-identity-pools create github-pool \
  --location=global --display-name="GitHub Actions"

# 2. Add OIDC provider for GitHub
gcloud iam workload-identity-pools providers create-oidc github \
  --location=global \
  --workload-identity-pool=github-pool \
  --display-name="GitHub OIDC" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-condition='assertion.repository_owner=="my-org"'

# 3. Bind a workflow to an SA
gcloud iam service-accounts add-iam-policy-binding \
  deployer@PROJECT.iam.gserviceaccount.com \
  --member="principalSet://iam.googleapis.com/projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/attribute.repository/my-org/my-repo" \
  --role="roles/iam.workloadIdentityUser"
```

GitHub Actions workflow:

```yaml
permissions:
  id-token: write
jobs:
  deploy:
    steps:
    - uses: google-github-actions/auth@v2
      with:
        workload_identity_provider: projects/NUM/locations/global/workloadIdentityPools/github-pool/providers/github
        service_account: deployer@PROJECT.iam.gserviceaccount.com
    - run: gcloud run deploy ...
```

Now **GHA needs zero SA keys in secrets**.

## 5. Org Policy

**Org-wide enforced rules** — child projects can't override.

| Common constraint | Purpose |
| --- | --- |
| `iam.disableServiceAccountKeyCreation` | Block SA key creation |
| `iam.allowedPolicyMemberDomains` | Only allow members from your domain |
| `compute.vmExternalIpAccess` | Block VM external IPs |
| `storage.publicAccessPrevention` | Block public GCS buckets |
| `compute.requireOsLogin` | Force OS Login |
| `compute.restrictSharedVpcSubnetworks` | Restrict allowed Shared VPC subnets |
| `gcp.resourceLocations` | Restrict regions resources can be created in |

```bash
# Example: block SA key creation org-wide
gcloud resource-manager org-policies set-policy \
  --organization=ORG_ID \
  policy.yaml
# policy.yaml:
# constraint: constraints/iam.disableServiceAccountKeyCreation
# booleanPolicy: { enforced: true }
```

## 6. VPC Service Controls (VPC-SC)

Wrap GCP APIs (GCS, BQ, Pub/Sub…) in a **service perimeter**:

```
Perimeter = { project A, project B }
Rules:
 - inside ↔ inside: allowed
 - inside → outside: blocked
 - outside → inside: blocked (unless Access Level matches)
```

Threat model: **insider or phished account exfiltrating data to their own project**.

```bash
# Create perimeter
gcloud access-context-manager perimeters create prod-perimeter \
  --policy=POLICY_ID \
  --title="Prod" \
  --resources=projects/PROJECT_NUMBER \
  --restricted-services=storage.googleapis.com,bigquery.googleapis.com
```

Advanced: **Access Levels** (IP / device / OS) let specific outside requests in.

> Misconfigured VPC-SC can **lock the whole org out**. Always `--dry-run` first and watch audit logs.

## 7. Audit Logs

Four kinds:

| Type | Default | Content |
| --- | --- | --- |
| Admin Activity | Always on, free | Writes (create/update/delete) |
| System Event | Always on, free | GCP-internal events |
| **Data Access** | **Off by default** (except BQ) | Reads (GCS get, Secret access) |
| Policy Denied | Always on | Requests blocked by IAM/Org Policy |

To answer "who read this secret" — Data Access logs **must be turned on first**.

```text
Console → IAM → Audit Logs → pick service → check Data Read / Data Write
```

## 8. Hygiene checklist

Minimum every GCP org should enforce:

- [ ] Block SA key creation (`iam.disableServiceAccountKeyCreation`)
- [ ] Block VM external IPs (`compute.vmExternalIpAccess`) + use IAP
- [ ] Block public GCS (`storage.publicAccessPrevention`)
- [ ] Restrict resource locations (`gcp.resourceLocations`)
- [ ] Enable Data Access audit logs on sensitive services
- [ ] All production access via SA impersonation or WIF (no SA keys)
- [ ] Critical services inside a VPC-SC perimeter

## 9. Common pitfalls

- **Condition doesn't block what you expected**: conditions are *additional* restrictions, not new permissions. An OR-typo is equivalent to no condition.
- **Service doesn't support conditions**: many newer APIs ignore them silently.
- **Custom role rot**: a custom role doesn't include new permissions a service adds, so new features break. Prefer predefined.
- **WIF attribute condition too loose**: forgetting `assertion.repository_owner` lets any GitHub repo mint tokens.
- **Org Policy locks you out**: e.g. blocking external IPs prevents legacy VMs from starting. Always dry-run.
- **VPC-SC self-lockout**: admins lose access (even console). **Always keep a break-glass account outside the perimeter.**
