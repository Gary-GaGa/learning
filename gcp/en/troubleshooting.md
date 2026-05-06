# Troubleshooting: decision flows for common issues

"Why is this broken" is a beginner's most painful moment. This file turns 80% of the "stuck" situations I've seen into decision trees.

> Step zero: **always read the full error message**. GCP errors are usually precise but get truncated in the console's small panel. Pull the full one from Cloud Logging (filter `severity>=ERROR` plus your service).

## 1. Auth / permissions: 401, 403, PERMISSION_DENIED

The number-one beginner pain. First disambiguate **who** is doing **what**.

```mermaid
flowchart TD
  A[Got 403 / PERMISSION_DENIED] --> B{Who is calling<br/>the API?}
  B -->|Me, via gcloud| C[gcloud auth list<br/>confirm current account]
  B -->|My program / SDK| D[GOOGLE_APPLICATION_CREDENTIALS?<br/>set or not]
  B -->|Cloud Run / GKE Pod| E[Service account<br/>used by the workload]
  B -->|GitHub Actions / CI| F[Workload Identity Federation<br/>configuration]

  C --> G[gcloud projects get-iam-policy PROJECT<br/>see your roles]
  D --> H[Not set: rely on ADC<br/>run gcloud auth application-default login]
  E --> I[gcloud run services describe ...<br/>check spec.template.serviceAccount]
  F --> J[Inspect attribute-condition:<br/>repo / branch match?]

  G --> K{Role sufficient?}
  I --> K
  H --> K
  J --> K
  K -->|No| L[Add the right predefined role<br/>not roles/owner]
  K -->|Yes| M[Check IAM Conditions:<br/>any time / resource limits?]
  M --> N[Check the resource:<br/>same project?<br/>resource-level binding?]
```

### Quick reference: "I granted the role but still can't"

| Symptom | Cause |
| --- | --- |
| Just granted role, still 403 | IAM changes can take **up to 2 minutes** to propagate |
| Don't see your binding on the resource | It may live at project / folder / org level |
| Role is correct but service ignores it | The service may not support IAM Conditions, so adding one is equivalent to no grant |
| Cloud Run / Function 401 | Caller didn't send an ID token, or audience is wrong (must be the full service URL) |
| GKE Pod 403 | Workload Identity has 3 steps; you skipped one (cluster pool / GCP SA token / K8s SA annotation) |
| Service A → Service B 401 | A's SA needs `run.invoker` on B (not on A itself) |

## 2. Deploy fails / container won't start

```mermaid
flowchart TD
  A[Cloud Run / GKE deploy fails] --> B{Symptom}
  B -->|Image pull error| C[Image path correct?<br/>Node SA has artifactregistry.reader?]
  B -->|Container crash on start| D[gcloud run services logs read<br/>kubectl logs --previous]
  B -->|Pod stuck Pending| E[kubectl describe pod<br/>read events]
  B -->|Cloud Run revision failed repeatedly| F[Cloud Run expects listen on $PORT]

  C --> C1[gcloud auth configure-docker for local push<br/>Add reader role to node SA]
  D --> D1{Error message<br/>keywords}
  D1 -->|Address already in use| F
  D1 -->|connection refused| G[External dep unreachable:<br/>DB / API / Secret]
  D1 -->|out of memory / OOMKilled| H[--memory too small<br/>or fix leak / GC]
  D1 -->|missing env var| I[Check env / Secret Manager mount]

  E --> E1[Insufficient cpu/memory:<br/>requests too high or no node]
  E --> E2[Image pull failed: see C]
  E --> E3[FailedScheduling:<br/>nodeSelector / taint mismatch]

  F --> F1[Listen on 0.0.0.0:$PORT<br/>not a hardcoded 8080]
  G --> G1[Go to §3 networking]
```

## 3. Networking: can't reach, timeouts, 502

```mermaid
flowchart TD
  A[A can't reach B] --> B{Where is B?}
  B -->|Public internet| C[Does A have egress?<br/>External IP / Cloud NAT]
  B -->|GCP API<br/>GCS, Pub/Sub...| D[Does A have an external IP?]
  B -->|VPC Cloud SQL| E[Private IP or Public IP?]
  B -->|VPC GKE / GCE| F[Firewall / NetworkPolicy]

  C --> C1{NAT in place?}
  C1 -->|No| C2[Add Cloud NAT<br/>or assign external IP]
  C1 -->|Yes| C3[Check firewall egress<br/>not blocking outbound]

  D --> D1{External IP?}
  D1 -->|Yes| D2[googleapis.com should work directly]
  D1 -->|No| D3[Enable Private Google Access<br/>on the subnet]

  E --> E1{Private IP?}
  E1 -->|Yes| E2[Service Networking peering set up?<br/>Pod CIDR in authorized range?]
  E1 -->|No| E3[Use Auth Proxy / Connector]

  F --> F1[gcloud compute firewall-rules list<br/>match source/target]
  F --> F2[Network Intelligence Center<br/>Connectivity Tests]
```

### Diagnosing 502 from a Load Balancer

```mermaid
flowchart TD
  A[LB returns 502] --> B{Backend health?}
  B -->|UNHEALTHY| C[health check path correct?<br/>Firewall allows<br/>130.211.0.0/22 + 35.191.0.0/16?]
  B -->|HEALTHY| D[Backend latency high?]
  D -->|Yes| E[Backend timeout < LB timeout<br/>or downstream slow]
  D -->|No| F[Backend closes connection<br/>check backend logs]
```

## 4. Pub/Sub: messages missing or duplicated

```mermaid
flowchart TD
  A[Pub/Sub behavior weird] --> B{Symptom}
  B -->|Subscriber not receiving| C[Did the subscription exist<br/>before publish?]
  B -->|Same messages keep redelivered| D[Watch oldest_unacked_message_age<br/>metric]
  B -->|Messages disappeared| E[Retention expired?<br/>Default 7 days]
  B -->|Push sub 4xx| F[OIDC token / audience<br/>does endpoint verify it?]

  C --> C1[Pub/Sub does not retain messages<br/>before any sub exists<br/>create sub first, then publish]
  D --> D1{Acked within<br/>ack_deadline?}
  D1 -->|No| D2[Processing too long / crash<br/>raise ack_deadline<br/>or use streaming pull auto-extend]
  D1 -->|Yes| D3[Business logic not idempotent<br/>at-least-once = duplicates happen]
  E --> E1[Extend retention up to 31d<br/>or sink to GCS for backup]
  F --> F1[Push endpoint must return 2xx<br/>and verify X-Goog-... JWT]
```

## 5. Bill anomaly / sudden cost spike

```mermaid
flowchart TD
  A[Spike on the bill] --> B[Cloud Billing → Reports<br/>group by SKU]
  B --> C{Largest SKU?}
  C -->|BigQuery analysis| D[INFORMATION_SCHEMA.JOBS<br/>find biggest query]
  C -->|Network Inter-Region Egress| E[Cross-region resources<br/>align app/db/log/sink]
  C -->|Network Internet Egress| F[Outbound:<br/>external API / CDN miss / log to outside]
  C -->|Cloud SQL CPU| G[Idle instance still running / HA on<br/>connection storm]
  C -->|Compute / GKE| H[Forgotten VM / cluster]
  C -->|Logging Volume| I[No exclude filter<br/>debug logs streaming]

  D --> D1[Find query → add partition filter<br/>use maximum_bytes_billed]
  E --> E1[Move resources to same region<br/>or alert on egress]
  H --> H1[gcloud recommender:<br/>idle-instance recommender]
  I --> I1[Add exclusion filter<br/>e.g. health checks]
```

## 6. Terraform horror stories

```mermaid
flowchart TD
  A[Terraform error] --> B{Symptom}
  B -->|state lock stuck| C[gcloud storage cat the .tflock<br/>see the holder<br/>force-unlock only if truly idle]
  B -->|destroy fails| D[Dependency not clean:<br/>bucket has objects / subnet has VMs]
  B -->|API not enabled| E[Missing google_project_service resource]
  B -->|drift| F[Someone clicked in console<br/>terraform plan shows diff]
  B -->|provider upgrade broke| G[Roll back; read release notes]

  C --> C1[terraform force-unlock LOCK_ID<br/>ONLY if truly nobody is running]
  D --> D1[force_destroy=true<br/>or manually clean child resources first]
  F --> F1[terraform import the console change<br/>or plan-and-overwrite]
```

## 7. Universal first move: turn on Audit Logs

A lot of "why blocked" mysteries become obvious in Audit Logs:

```text
# Cloud Logging query
protoPayload.authorizationInfo.granted=false
resource.type="<service-type>"
```

If **Data Read / Data Write** events aren't showing up — they're off by default. Console → IAM → Audit Logs → enable.

## 8. Still stuck?

1. **Reproduce minimally**: use `curl` or a 5-line script to isolate from your own code.
2. **Check quotas**: many "can't create" errors are quota exhaustion. Console → IAM & Admin → Quotas.
3. **[Issue Tracker](https://issuetracker.google.com/)** for known issues.
4. **Official troubleshooting docs**: every service has one — e.g. [Cloud Run troubleshooting](https://cloud.google.com/run/docs/troubleshooting).
