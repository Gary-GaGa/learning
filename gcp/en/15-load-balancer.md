# HTTPS Load Balancer (Deep Dive)

09-vpc-networking listed LB types; this topic dives into the most common one — **Global External Application Load Balancer** (HTTPS LB): how it composes, how to add TLS, how to layer on WAF / IAP.

## 1. Anatomy

```
Forwarding Rule (public IP + port)
   │
   ▼
Target HTTPS Proxy   ← attaches the SSL Certificate
   │
   ▼
URL Map              ← routes by host / path
   │
   ▼
Backend Service      ← health checks, session affinity, timeouts
   │
   ▼
Backend (NEG / MIG / Bucket)
```

Five layered objects, each independently editable. Confusing on first contact, but each layer has a single job.

## 2. Backend types

| Type | Best for |
| --- | --- |
| **MIG** (Managed Instance Group) | Pool of GCE VMs |
| **Zonal NEG** | Explicit IP:port set |
| **Serverless NEG** | Cloud Run / Cloud Functions / App Engine |
| **Internet NEG** | External / your own endpoint (GCLB front-running it) |
| **Hybrid NEG** | On-prem VMs (over VPN/Interconnect) |
| **Backend Bucket** | GCS bucket (static site) |
| **PSC NEG** | Private Service Connect endpoint |

## 3. Full example: Cloud Run + custom domain + WAF

Goal: `https://api.example.com` → GCLB → Cloud Armor → Cloud Run.

### 3.1 Setup

```bash
PROJECT=my-project
REGION=asia-east1
SERVICE=hello                       # existing Cloud Run service
DOMAIN=api.example.com
```

### 3.2 Serverless NEG

```bash
gcloud compute network-endpoint-groups create hello-neg \
  --region=$REGION \
  --network-endpoint-type=serverless \
  --cloud-run-service=$SERVICE
```

### 3.3 Backend service

```bash
gcloud compute backend-services create hello-bs \
  --global \
  --load-balancing-scheme=EXTERNAL_MANAGED \
  --protocol=HTTPS

gcloud compute backend-services add-backend hello-bs \
  --global \
  --network-endpoint-group=hello-neg \
  --network-endpoint-group-region=$REGION
```

> Serverless NEGs **don't need** health checks.

### 3.4 URL Map

```bash
gcloud compute url-maps create hello-urlmap \
  --default-service=hello-bs

# Advanced: per-path routing to different backends
# gcloud compute url-maps add-path-matcher ...
```

### 3.5 Managed SSL cert + Target Proxy

```bash
gcloud compute ssl-certificates create hello-cert \
  --global \
  --domains=$DOMAIN

gcloud compute target-https-proxies create hello-proxy \
  --url-map=hello-urlmap \
  --ssl-certificates=hello-cert
```

### 3.6 Forwarding rule (public IP)

```bash
# Reserve a static IP
gcloud compute addresses create hello-ip --global

IP=$(gcloud compute addresses describe hello-ip --global --format="value(address)")
echo "Point an A record for $DOMAIN at $IP"

# Wire it up
gcloud compute forwarding-rules create hello-fr \
  --global \
  --target-https-proxy=hello-proxy \
  --ports=443 \
  --address=hello-ip
```

After DNS propagates, Google auto-validates and issues the SSL (**ACME-style; takes 10–60 minutes**).

```bash
gcloud compute ssl-certificates describe hello-cert --global \
  --format="value(managed.status,managed.domainStatus)"
# ACTIVE; DOMAIN: api.example.com=ACTIVE  ← wait until you see this
```

### 3.7 HTTP → HTTPS redirect

```bash
# Second forwarding rule on port 80 with URL map redirecting
gcloud compute url-maps create hello-redirect \
  --default-url-redirect-redirect-response-code=MOVED_PERMANENTLY_DEFAULT \
  --default-url-redirect-https-redirect

gcloud compute target-http-proxies create hello-http-proxy \
  --url-map=hello-redirect

gcloud compute forwarding-rules create hello-fr-http \
  --global --target-http-proxy=hello-http-proxy \
  --ports=80 --address=hello-ip
```

## 4. Cloud Armor (WAF)

```bash
gcloud compute security-policies create hello-armor \
  --description="WAF for hello"

# Block specific countries
gcloud compute security-policies rules create 1000 \
  --security-policy=hello-armor \
  --src-region-codes=CN,RU \
  --action=deny-403

# Apply OWASP preconfigured rules (XSS, SQLi)
gcloud compute security-policies rules create 2000 \
  --security-policy=hello-armor \
  --expression="evaluatePreconfiguredExpr('xss-v33-stable')" \
  --action=deny-403

# Rate limit: 100 req per 60s per IP
gcloud compute security-policies rules create 3000 \
  --security-policy=hello-armor \
  --src-ip-ranges='*' \
  --action=rate-based-ban \
  --rate-limit-threshold-count=100 \
  --rate-limit-threshold-interval-sec=60 \
  --conform-action=allow \
  --exceed-action=deny-429 \
  --enforce-on-key=IP \
  --ban-duration-sec=600

# Attach to backend service
gcloud compute backend-services update hello-bs --global \
  --security-policy=hello-armor
```

## 5. IAP (Identity-Aware Proxy)

Layered in front of the LB — turns "only logged-in Google users on the allowlist may enter" into a one-line config; backends don't implement auth.

```bash
# Enable IAP on the backend service
gcloud compute backend-services update hello-bs --global \
  --iap=enabled,oauth2-client-id=CLIENT_ID,oauth2-client-secret=CLIENT_SECRET

# Allowlist users
gcloud iap web add-iam-policy-binding \
  --resource-type=backend-services \
  --service=hello-bs \
  --member=user:alice@example.com \
  --role=roles/iap.httpsResourceAccessor
```

> Backends must verify the IAP-signed JWT (header `X-Goog-Iap-Jwt-Assertion`) — otherwise someone bypassing the LB could still reach them.

## 6. Observability

| Metric | Use |
| --- | --- |
| `loadbalancing.googleapis.com/https/request_count` | Traffic |
| Same metric + label `response_code_class` | 5xx ratio |
| `https/backend_latencies` | Backend latency distribution |
| `https/total_latencies` | End-to-end latency including LB |

LB access logs are **off by default** — enable on the backend service:

```bash
gcloud compute backend-services update hello-bs --global \
  --enable-logging --logging-sample-rate=1.0
```

## 7. Cleanup

```bash
gcloud compute forwarding-rules delete hello-fr hello-fr-http --global
gcloud compute target-https-proxies delete hello-proxy
gcloud compute target-http-proxies delete hello-http-proxy
gcloud compute url-maps delete hello-urlmap hello-redirect
gcloud compute backend-services delete hello-bs --global
gcloud compute network-endpoint-groups delete hello-neg --region=$REGION
gcloud compute ssl-certificates delete hello-cert --global
gcloud compute addresses delete hello-ip --global
```

> This is exactly why everyone manages LBs with Terraform — wrong delete order blocks dependencies.

## 8. Common pitfalls

- **SSL cert stuck PROVISIONING**: DNS A record wrong / TTL too long / not yet propagated. `dig $DOMAIN` to confirm.
- **502 from LB**: backend health check failing, backend timeout shorter than LB timeout, or backend closes connection. Check `backend_latencies` and backend logs.
- **Client IP shows LB IP**: Application LB is a proxy; real client IP is in `X-Forwarded-For` (first entry). Use Passthrough Network LB to preserve.
- **Cross-region traffic billed**: Global External Application LB **will** route traffic to the nearest region — make sure backends exist in those regions or you'll pay cross-region.
- **WAF blocks your own users**: Cloud Armor in `production` mode enforces immediately. Use `preview` mode for a few days, watch logs, then promote.
- **Forwarding rule must be EXTERNAL_MANAGED**: legacy `EXTERNAL` is Classic LB; new features (advanced traffic management) only work on EXTERNAL_MANAGED.
