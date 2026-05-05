# Artifact Registry

GCP 的 **package / container image registry**，取代舊的 Container Registry（GCR）。**新專案不要再用 GCR**，Google 已宣布退役。

## 1. 跟 GCR 的差別

| 項目 | GCR（舊） | Artifact Registry（新） |
| --- | --- | --- |
| 支援格式 | 只有 Docker | Docker、Maven、npm、Python、Apt、Yum、Go、Helm、Generic |
| Repo 隔離 | 用 path 區分 | 真正的 repository 物件，可獨立設權限 |
| Region 控制 | 只能 multi-region | 細到 region |
| Vulnerability scan | 有 | 有，整合更深 |

## 2. 概念

```
Project
└── Location (region/multi-region)
    └── Repository (一個 repo 對應一個 format)
        └── Package / Image / Artifact
            └── Version / Tag
```

每個 repo **只能放一種 format**（Docker repo 就只放 image，Maven repo 就只放 jar）。

## 3. 建立 Docker repository

```bash
gcloud artifacts repositories create my-images \
  --repository-format=docker \
  --location=asia-east1 \
  --description="App images"
```

完整 image path 格式：

```
LOCATION-docker.pkg.dev/PROJECT/REPO/IMAGE:TAG
# e.g.
asia-east1-docker.pkg.dev/my-project/my-images/api:v1.2.3
```

## 4. Docker push / pull

### 設定 docker 認證

```bash
# 把 gcloud 當作 docker credential helper
gcloud auth configure-docker asia-east1-docker.pkg.dev
```

### Build 並 push

```bash
docker build -t asia-east1-docker.pkg.dev/PROJECT/my-images/api:v1 .
docker push  asia-east1-docker.pkg.dev/PROJECT/my-images/api:v1
```

### 在 GKE pull

GKE node 預設的 SA 需要 `roles/artifactregistry.reader`：

```bash
NODE_SA=$(gcloud iam service-accounts list \
  --filter="email~^.*-compute@" \
  --format="value(email)")

gcloud artifacts repositories add-iam-policy-binding my-images \
  --location=asia-east1 \
  --member="serviceAccount:$NODE_SA" \
  --role="roles/artifactregistry.reader"
```

## 5. 列出 / 清理

```bash
# 列 image
gcloud artifacts docker images list \
  asia-east1-docker.pkg.dev/PROJECT/my-images

# 列某個 image 的所有 tag
gcloud artifacts docker images list \
  asia-east1-docker.pkg.dev/PROJECT/my-images/api \
  --include-tags

# 刪一個 tag
gcloud artifacts docker images delete \
  asia-east1-docker.pkg.dev/PROJECT/my-images/api:v1
```

## 6. 自動清理（Cleanup policies）

不設清理規則，repo 會無限變大、變貴。可以設規則：

```bash
# policy.json
cat > policy.json <<'EOF'
[
  {
    "name": "keep-recent-prod",
    "action": {"type": "Keep"},
    "condition": {
      "tagState": "TAGGED",
      "tagPrefixes": ["v", "prod-"],
      "newerThan": "30d"
    }
  },
  {
    "name": "delete-untagged",
    "action": {"type": "Delete"},
    "condition": {
      "tagState": "UNTAGGED",
      "olderThan": "7d"
    }
  }
]
EOF

gcloud artifacts repositories set-cleanup-policies my-images \
  --location=asia-east1 \
  --policy=policy.json
```

> 先用 `--dry-run` 看會刪到什麼，沒問題再 apply。

## 7. Vulnerability Scanning

開啟後 Artifact Registry 會自動掃 image 並列出 CVE：

```bash
gcloud services enable containerscanning.googleapis.com

# 看某 image 的 vulnerabilities
gcloud artifacts docker images list-vulnerabilities \
  asia-east1-docker.pkg.dev/PROJECT/my-images/api:v1
```

可以搭 **Binary Authorization** 強制只准 deploy 沒 critical CVE 的 image 到 GKE / Cloud Run。

## 8. 其他格式範例

### Python

```bash
gcloud artifacts repositories create my-pypi \
  --repository-format=python --location=asia-east1

# 取得 publish URL
gcloud artifacts print-settings python --repository=my-pypi --location=asia-east1
# 把輸出貼到 ~/.pypirc，然後 twine upload
```

### Maven

```bash
gcloud artifacts repositories create my-maven \
  --repository-format=maven --location=asia-east1

gcloud artifacts print-settings mvn --repository=my-maven --location=asia-east1
# 貼到 pom.xml
```

## 9. 清理

```bash
gcloud artifacts repositories delete my-images --location=asia-east1
```

## 10. 常見坑

- **GKE 拉不到 image**：node 的 SA 沒 `artifactregistry.reader`。
- **本機 push 401**：忘了 `gcloud auth configure-docker LOCATION-docker.pkg.dev`。
- **跨 region 拉 image 慢**：repo 要跟 GKE / Cloud Run 同 region。
- **Repo 越長越大**：一定要設 cleanup policy，特別是 CI 會產出大量 untagged image。
- **不能改 format**：repo 一旦建立，format 不能改，要建新的。
