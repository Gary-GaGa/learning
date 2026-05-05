# CI/CD：Cloud Build 與 Cloud Deploy

兩個獨立但常一起用的服務：

| 服務 | 角色 |
| --- | --- |
| **Cloud Build** | **CI**：build image、跑測試、push 到 Artifact Registry |
| **Cloud Deploy** | **CD**：把 image 依序推到 dev → staging → prod，含批准、回滾 |

> 也可以全用 **GitHub Actions + WIF**（後面 §6）。GCP-only 環境用 Cloud Build/Deploy 比較整合；多 cloud 環境 GHA 通用。

## 1. Cloud Build

### 1.1 概念

```
Trigger（從 GitHub / source push）
  └─► Build（一連串 step，每 step 是一個容器）
        └─► Artifacts（image / 檔案）push 到 Artifact Registry / GCS
```

每個 build step 是一個 container，跑完傳結果給下一步。標準 step 在 `gcr.io/cloud-builders/*`，也可以用任何 public image。

### 1.2 `cloudbuild.yaml` 範例

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

內建變數常用：`$PROJECT_ID`、`$BUILD_ID`、`$SHORT_SHA`、`$BRANCH_NAME`、`$TAG_NAME`。

### 1.3 觸發

```bash
# 從 GitHub repo push 到 main 觸發
gcloud builds triggers create github \
  --name=hello-on-main \
  --repo-name=my-repo --repo-owner=my-org \
  --branch-pattern="^main$" \
  --build-config=cloudbuild.yaml
```

或者手動：

```bash
gcloud builds submit --config=cloudbuild.yaml .
```

### 1.4 Build 用什麼身份

預設用 `PROJECT_NUMBER@cloudbuild.gserviceaccount.com`，**但這個 SA 預設權限很大且不易控管**。建議：

```bash
# 自建一個 SA 給 build 用
gcloud iam service-accounts create cb-runner

# 給它要的權限（push to AR、deploy Run、access Secret）
gcloud projects add-iam-policy-binding PROJECT \
  --member=serviceAccount:cb-runner@PROJECT.iam.gserviceaccount.com \
  --role=roles/artifactregistry.writer
gcloud projects add-iam-policy-binding PROJECT \
  --member=serviceAccount:cb-runner@PROJECT.iam.gserviceaccount.com \
  --role=roles/run.developer

# Trigger 指定用這個 SA
gcloud builds triggers update hello-on-main \
  --service-account=projects/PROJECT/serviceAccounts/cb-runner@PROJECT.iam.gserviceaccount.com
```

### 1.5 取秘密

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

### 1.6 用 private pool / 大 VM

預設 build 跑在 Google 共用環境。要連 VPC 內部資源（例如 private Cloud SQL）或要更大 VM：

```bash
gcloud builds worker-pools create my-pool \
  --region=asia-east1 \
  --worker-machine-type=e2-standard-4 \
  --peered-network=projects/PROJECT/global/networks/prod-vpc
```

## 2. Cloud Deploy

把「分階段部署 + 批准 + 回滾」變成 first-class concept。

### 2.1 模型

```
DeliveryPipeline
├── Target dev
├── Target staging      ← 可加 require-approval
└── Target prod         ← 可加 require-approval

Release（一次 deployment 的 immutable 包）
└── Rollout（推到某個 target 的執行）
```

### 2.2 設定（Cloud Run target）

`clouddeploy.yaml`：

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

`skaffold.yaml`（讓 Cloud Deploy 知道怎麼 render manifest）：

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

`service.yaml`（Cloud Run service 模板）：

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

### 2.3 部署

```bash
gcloud deploy apply --file=clouddeploy.yaml --region=asia-east1

# 在 Cloud Build 最後一步建 release
gcloud deploy releases create rel-$SHORT_SHA \
  --delivery-pipeline=hello-pipeline \
  --region=asia-east1 \
  --images=hello=${IMAGE_URL}
```

之後 dev 自動 rollout；staging / prod 需要在 Console 或 CLI 按「Promote / Approve」。

### 2.4 回滾

```bash
gcloud deploy rollouts ... # 直接 promote 上一個 release 即可
```

## 3. GKE Target

跟 Cloud Run 同模型，把 target 改成 GKE：

```yaml
apiVersion: deploy.cloud.google.com/v1
kind: Target
metadata: { name: prod-gke }
gke:
  cluster: projects/PROJECT/locations/asia-east1/clusters/prod-cluster
```

`skaffold.yaml` 改用 `kubectl` deployer，並提供 K8s manifests。Cloud Deploy 支援漸進式 / canary / blue-green。

## 4. Binary Authorization（綁進管線）

要求只准 deploy「**在我們 build pipeline 裡簽過名**」的 image：

```bash
gcloud container binauthz attestors create build-attestor \
  --attestation-authority-note=projects/PROJECT/notes/build \
  --attestation-authority-note-project=PROJECT
```

policy 設成「prod cluster / Cloud Run 必須有 build-attestor 的簽章」→ 任何沒走 pipeline 的 image 都被擋。

## 5. 觀測 & 通知

- Cloud Build logs 自動進 Cloud Logging（`resource.type=build`）
- Build 失敗 → Pub/Sub `cloud-builds` topic → Slack notifier
- Cloud Deploy → Pub/Sub `clouddeploy-operations` / `clouddeploy-approvals`

```bash
gcloud pubsub subscriptions create build-notify-slack \
  --topic=cloud-builds \
  --push-endpoint=https://hooks.slack.com/services/...
```

## 6. 替代方案：GitHub Actions + WIF

不用 Cloud Build 也能做。詳見 [14-iam-advanced.md §4 WIF](./14-iam-advanced.md#4-workload-identity-federationwif)，這裡列工作流：

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

## 7. 常見坑

- **Build SA 權限不足**：deploy 步驟拿不到 `run.developer`、push image 拿不到 `artifactregistry.writer`。
- **`$PROJECT_ID` 在 substitution 用了 `_` 前綴**：底線開頭是使用者變數，內建變數**沒**底線。
- **Build 慢**：每次都 `pip install` / `npm install`。用 [build cache](https://cloud.google.com/build/docs/optimize-builds/speeding-up-builds) 或 multistage Dockerfile。
- **Cloud Deploy `Promote` 卡住**：targets 的 SA 沒 deploy 該服務的權限。
- **Canary 沒生效**：Cloud Run 自動 traffic control 沒開、或服務不是 ingress=internal-and-cloud-load-balancing。
- **WIF token 抓不到**：忘了加 `permissions: { id-token: write }` 或 `attribute-condition` 太嚴格。
