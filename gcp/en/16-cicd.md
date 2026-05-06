# CI/CD: Cloud Build and Cloud Deploy

Two separate but commonly-paired services:

| Service | Role |
| --- | --- |
| **Cloud Build** | **CI**: build images, run tests, push to Artifact Registry |
| **Cloud Deploy** | **CD**: promote images through dev → staging → prod with approvals and rollbacks |

> You can also do everything with **GitHub Actions + WIF** (see §6). GCP-only environments tend to prefer Cloud Build/Deploy for tighter integration; multi-cloud orgs lean on GHA.

## 1. Cloud Build

### 1.1 Concepts

```
Trigger (from GitHub / source push)
  └─► Build (a series of steps; each step is a container)
        └─► Artifacts (image / files) push to Artifact Registry / GCS
```

Each build step is a container — runs and passes outputs to the next step. Standard steps live under `gcr.io/cloud-builders/*`; you can use any public image.

### 1.2 `cloudbuild.yaml` example

```yaml
# cloudbuild.yaml
substitutions:
  _REGION: asia-east1
  _REPO: my-images
  _SERVICE: hello

steps:
- id: test
  name: python:3.12
  entrypoint: bash
  args: ['-c', 'pip install -r requirements.txt && pytest -q']

- id: build
  name: gcr.io/cloud-builders/docker
  args:
    - build
    - -t
    - ${_REGION}-docker.pkg.dev/$PROJECT_ID/${_REPO}/${_SERVICE}:$SHORT_SHA
    - .

- id: push
  name: gcr.io/cloud-builders/docker
  args: [push, '${_REGION}-docker.pkg.dev/$PROJECT_ID/${_REPO}/${_SERVICE}:$SHORT_SHA']

- id: deploy
  name: gcr.io/google.com/cloudsdktool/cloud-sdk:slim
  entrypoint: gcloud
  args:
    - run
    - deploy
    - ${_SERVICE}
    - --image=${_REGION}-docker.pkg.dev/$PROJECT_ID/${_REPO}/${_SERVICE}:$SHORT_SHA
    - --region=${_REGION}
    - --quiet

images:
  - '${_REGION}-docker.pkg.dev/$PROJECT_ID/${_REPO}/${_SERVICE}:$SHORT_SHA'

options:
  logging: CLOUD_LOGGING_ONLY
```

Useful built-in vars: `$PROJECT_ID`, `$BUILD_ID`, `$SHORT_SHA`, `$BRANCH_NAME`, `$TAG_NAME`.

### 1.3 Triggers

```bash
# Trigger on push to main of a GitHub repo
gcloud builds triggers create github \
  --name=hello-on-main \
  --repo-name=my-repo --repo-owner=my-org \
  --branch-pattern="^main$" \
  --build-config=cloudbuild.yaml
```

Manual:

```bash
gcloud builds submit --config=cloudbuild.yaml .
```

### 1.4 Which identity runs the build

The default `PROJECT_NUMBER@cloudbuild.gserviceaccount.com` **has very broad permissions and is hard to audit**. Recommended:

```bash
# Dedicated SA for builds
gcloud iam service-accounts create cb-runner

# Grant only what's needed (push to AR, deploy Run, access secrets)
gcloud projects add-iam-policy-binding PROJECT \
  --member=serviceAccount:cb-runner@PROJECT.iam.gserviceaccount.com \
  --role=roles/artifactregistry.writer
gcloud projects add-iam-policy-binding PROJECT \
  --member=serviceAccount:cb-runner@PROJECT.iam.gserviceaccount.com \
  --role=roles/run.developer

# Use it on the trigger
gcloud builds triggers update hello-on-main \
  --service-account=projects/PROJECT/serviceAccounts/cb-runner@PROJECT.iam.gserviceaccount.com
```

### 1.5 Pull secrets

```yaml
availableSecrets:
  secretManager:
  - versionName: projects/$PROJECT_ID/secrets/db-password/versions/latest
    env: 'DB_PASSWORD'

steps:
- name: alpine
  entrypoint: sh
  args: ['-c', 'echo length=${#DB_PASSWORD}']
  secretEnv: ['DB_PASSWORD']
```

### 1.6 Private pool / larger machines

By default builds run on shared infrastructure. To reach private resources (e.g. Cloud SQL Private IP) or use bigger VMs:

```bash
gcloud builds worker-pools create my-pool \
  --region=asia-east1 \
  --worker-machine-type=e2-standard-4 \
  --peered-network=projects/PROJECT/global/networks/prod-vpc
```

## 2. Cloud Deploy

Treats "phased deploy with approvals and rollback" as first-class concepts.

### 2.1 Model

```
DeliveryPipeline
├── Target dev
├── Target staging      ← optional require-approval
└── Target prod         ← optional require-approval

Release  (one immutable deployment package)
└── Rollout (execution to a specific target)
```

### 2.2 Configuration (Cloud Run target)

`clouddeploy.yaml`:

```yaml
apiVersion: deploy.cloud.google.com/v1
kind: DeliveryPipeline
metadata:
  name: hello-pipeline
serialPipeline:
  stages:
  - targetId: dev
  - targetId: staging
    profiles: [staging]
  - targetId: prod
    profiles: [prod]
    strategy:
      canary:
        runtimeConfig:
          cloudRun:
            automaticTrafficControl: true
        canaryDeployment:
          percentages: [25, 50]
          verify: false
---
apiVersion: deploy.cloud.google.com/v1
kind: Target
metadata: { name: dev }
run: { location: projects/PROJECT/locations/asia-east1 }
---
apiVersion: deploy.cloud.google.com/v1
kind: Target
metadata: { name: staging }
run: { location: projects/PROJECT/locations/asia-east1 }
---
apiVersion: deploy.cloud.google.com/v1
kind: Target
metadata: { name: prod }
requireApproval: true
run: { location: projects/PROJECT/locations/asia-east1 }
```

`skaffold.yaml` (tells Cloud Deploy how to render manifests):

```yaml
apiVersion: skaffold/v4beta7
kind: Config
profiles:
- name: staging
- name: prod
deploy:
  cloudrun: {}
manifests:
  rawYaml:
  - service.yaml
```

`service.yaml` (Cloud Run service template):

```yaml
apiVersion: serving.knative.dev/v1
kind: Service
metadata:
  name: hello
spec:
  template:
    spec:
      containers:
      - image: from-parameters
```

### 2.3 Deploy

```bash
gcloud deploy apply --file=clouddeploy.yaml --region=asia-east1

# As the last step in Cloud Build, create a release
gcloud deploy releases create rel-$SHORT_SHA \
  --delivery-pipeline=hello-pipeline \
  --region=asia-east1 \
  --images=hello=${IMAGE_URL}
```

`dev` rolls out automatically; `staging` / `prod` require Promote/Approve in Console or CLI.

### 2.4 Rollback

```bash
gcloud deploy rollouts ... # promote a previous release
```

## 3. GKE target

Same model, swap target type:

```yaml
apiVersion: deploy.cloud.google.com/v1
kind: Target
metadata: { name: prod-gke }
gke:
  cluster: projects/PROJECT/locations/asia-east1/clusters/prod-cluster
```

`skaffold.yaml` switches to a `kubectl` deployer with K8s manifests. Cloud Deploy supports gradual / canary / blue-green strategies.

## 4. Binary Authorization (gate the pipeline)

Require deploy targets to only accept images **signed by your build pipeline**:

```bash
gcloud container binauthz attestors create build-attestor \
  --attestation-authority-note=projects/PROJECT/notes/build \
  --attestation-authority-note-project=PROJECT
```

Policy: "prod cluster / Cloud Run requires a signature from build-attestor" → blocks any image that didn't go through the pipeline.

## 5. Observability & notifications

- Cloud Build logs flow to Cloud Logging (`resource.type=build`).
- Build status → Pub/Sub topic `cloud-builds` → Slack notifier.
- Cloud Deploy → Pub/Sub `clouddeploy-operations` / `clouddeploy-approvals`.

```bash
gcloud pubsub subscriptions create build-notify-slack \
  --topic=cloud-builds \
  --push-endpoint=https://hooks.slack.com/services/...
```

## 6. Alternative: GitHub Actions + WIF

Cloud Build is optional. See [14-iam-advanced.md §4 WIF](./14-iam-advanced.md#4-workload-identity-federation-wif). Workflow example:

```yaml
# .github/workflows/deploy.yml
name: deploy
on:
  push: { branches: [main] }
permissions:
  id-token: write
  contents: read
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    - uses: google-github-actions/auth@v2
      with:
        workload_identity_provider: projects/NUM/locations/global/workloadIdentityPools/github-pool/providers/github
        service_account: deployer@PROJECT.iam.gserviceaccount.com
    - uses: google-github-actions/setup-gcloud@v2
    - run: |
        gcloud auth configure-docker asia-east1-docker.pkg.dev
        docker build -t asia-east1-docker.pkg.dev/PROJECT/my-images/hello:$GITHUB_SHA .
        docker push asia-east1-docker.pkg.dev/PROJECT/my-images/hello:$GITHUB_SHA
        gcloud run deploy hello \
          --image=asia-east1-docker.pkg.dev/PROJECT/my-images/hello:$GITHUB_SHA \
          --region=asia-east1
```

## 7. Common pitfalls

- **Build SA missing perms**: deploy step lacks `run.developer`, push lacks `artifactregistry.writer`.
- **`$PROJECT_ID` with `_` prefix**: built-in vars have **no** underscore; `_VAR` is for user-defined substitutions.
- **Slow builds**: re-installing deps every run. Use [build cache](https://cloud.google.com/build/docs/optimize-builds/speeding-up-builds) or multi-stage Dockerfiles.
- **Cloud Deploy `Promote` stuck**: target's SA doesn't have permission to deploy that service.
- **Canary not effective**: automatic traffic control not enabled on Cloud Run, or service isn't `ingress=internal-and-cloud-load-balancing`.
- **WIF token missing**: forgot `permissions: { id-token: write }`, or `attribute-condition` is too strict.
