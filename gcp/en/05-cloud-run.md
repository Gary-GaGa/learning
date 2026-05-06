# Cloud Run

Run containers serverlessly. Hand it an HTTP server container and Cloud Run handles autoscaling (including scale-to-zero), TLS, and gives you a URL. **No clusters, no nodes to manage.**

## 1. vs GKE / Functions

| Service | What you provide | What you manage | Best for |
| --- | --- | --- | --- |
| Cloud Functions | A function | Almost nothing | Simple webhooks, single entry point |
| **Cloud Run** | Any container (HTTP server) | Image + env + config | Most web services / APIs |
| GKE | Full K8s manifests | Cluster + workloads | Complex systems, K8s ecosystem |

> Rule of thumb: **"Can I use Cloud Run?" is the default question. Only reach for GKE if the answer is no.**

## 2. Two service types

| Type | Description |
| --- | --- |
| **Service** | Long-running HTTP server, autoscaled, has a URL. Most common. |
| **Job** | One-shot execution, exits when done, no HTTP. Good for batch / scheduled tasks (with Cloud Scheduler). |

## 3. Deploy a Service

### From source (Buildpacks auto-build)

```bash
# In a directory containing package.json / requirements.txt / go.mod
gcloud run deploy hello \
  --source=. \
  --region=asia-east1 \
  --allow-unauthenticated
```

### From a prebuilt image

```bash
gcloud run deploy hello \
  --image=asia-east1-docker.pkg.dev/PROJECT/repo/hello:v1 \
  --region=asia-east1 \
  --allow-unauthenticated
```

You'll get a `https://hello-xxxx.a.run.app` URL.

## 4. Important settings

```bash
gcloud run services update hello \
  --region=asia-east1 \
  --memory=512Mi \
  --cpu=1 \
  --concurrency=80 \              # requests per container instance
  --min-instances=0 \             # scale to zero (cold starts apply)
  --max-instances=20 \
  --timeout=60s \
  --service-account=app-runner@PROJECT.iam.gserviceaccount.com \
  --set-env-vars="LOG_LEVEL=info,FEATURE_X=true"
```

| Flag | Rule of thumb |
| --- | --- |
| `--concurrency` | I/O bound: 80 (default). CPU bound: 1–10. |
| `--min-instances` | Worried about cold starts: 1. Cost-sensitive: 0. |
| `--cpu-boost` | 2x CPU during cold start to speed up boot. |
| `--cpu-throttling` / `--no-cpu-throttling` | Default: CPU only while handling requests. Use `no-throttling` for background work. |

## 5. Authentication

Two layers:

### A. Who can invoke the service

```bash
# Public
gcloud run services add-iam-policy-binding hello --region=asia-east1 \
  --member=allUsers --role=roles/run.invoker

# Restricted to a specific SA (common for service-to-service)
gcloud run services add-iam-policy-binding hello --region=asia-east1 \
  --member=serviceAccount:caller@PROJECT.iam.gserviceaccount.com \
  --role=roles/run.invoker
```

Caller must send an ID token:

```bash
TOKEN=$(gcloud auth print-identity-token)
curl -H "Authorization: Bearer $TOKEN" https://hello-xxxx.a.run.app
```

### B. Identity used by the service itself

`--service-account=...` sets the runtime SA. Inside the container, the Google SDK auto-fetches tokens — no key file needed.

## 6. Traffic splitting (Canary)

```bash
# Deploy without taking traffic
gcloud run deploy hello --image=...:v2 --region=asia-east1 --no-traffic --tag=v2

# Send 5% of traffic to v2
gcloud run services update-traffic hello --region=asia-east1 \
  --to-tags=v2=5
```

`v2=5` means 5%. Once you're satisfied, `--to-tags=v2=100`.

## 7. Job example (one-shot batch)

```bash
gcloud run jobs create import-data \
  --image=asia-east1-docker.pkg.dev/PROJECT/repo/importer:v1 \
  --region=asia-east1 \
  --tasks=10 \                    # 10 tasks running in parallel
  --task-timeout=10m \
  --max-retries=3

gcloud run jobs execute import-data --region=asia-east1
```

Each task sees env vars `CLOUD_RUN_TASK_INDEX` (0–9) and `CLOUD_RUN_TASK_COUNT`. Use them to shard work.

## 8. Observability

- **Logs**: `gcloud run services logs read hello --region=asia-east1 --limit=50`
- **Metrics**: Cloud Run page in console shows request count / latency / instance count.
- **Tracing**: Use OpenTelemetry inside the container; traces flow into Cloud Trace.

## 9. Cleanup

```bash
gcloud run services delete hello --region=asia-east1
```

> Cloud Run scales to zero with no charge (unless `--min-instances` is set). **Forgetting to delete is less painful**, but still tidy up after experiments.

## 10. Common pitfalls

- **Container fails to start**: must listen on `$PORT` (Cloud Run injects it), not hardcoded 8080.
- **Slow cold starts**: image too large or heavy init → shrink image, use `--cpu-boost`, set `--min-instances=1`.
- **Background work doesn't run**: by default CPU is only allocated during requests. Add `--no-cpu-throttling`.
- **OOM kills**: Cloud Run restarts on OOM but only logs `Memory limit exceeded`. Right-size `--memory`.
- **401 errors**: caller didn't send an ID token, or audience is wrong. Audience must be the service URL (with `https://`).
