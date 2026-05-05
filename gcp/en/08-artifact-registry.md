# Artifact Registry

GCP's **package / container image registry**, replacing the old Container Registry (GCR). **For new projects, don't use GCR** — Google has announced its retirement.

## 1. vs GCR

| Aspect | GCR (old) | Artifact Registry (new) |
| --- | --- | --- |
| Formats | Docker only | Docker, Maven, npm, Python, Apt, Yum, Go, Helm, Generic |
| Repo isolation | Path-based | Real repository objects with independent IAM |
| Region control | Multi-region only | Per-region |
| Vulnerability scanning | Yes | Yes, deeper integration |

## 2. Concepts

```
Project
└── Location (region/multi-region)
    └── Repository (each repo has one format)
        └── Package / Image / Artifact
            └── Version / Tag
```

Each repo holds **one format only** (a Docker repo holds only images, a Maven repo holds only jars).

## 3. Create a Docker repository

```bash
gcloud artifacts repositories create my-images \
  --repository-format=docker \
  --location=asia-east1 \
  --description="App images"
```

Full image path format:

```
LOCATION-docker.pkg.dev/PROJECT/REPO/IMAGE:TAG
# e.g.
asia-east1-docker.pkg.dev/my-project/my-images/api:v1.2.3
```

## 4. Docker push / pull

### Configure docker auth

```bash
# Use gcloud as a docker credential helper
gcloud auth configure-docker asia-east1-docker.pkg.dev
```

### Build and push

```bash
docker build -t asia-east1-docker.pkg.dev/PROJECT/my-images/api:v1 .
docker push  asia-east1-docker.pkg.dev/PROJECT/my-images/api:v1
```

### Pull from GKE

The default node SA needs `roles/artifactregistry.reader`:

```bash
NODE_SA=$(gcloud iam service-accounts list \
  --filter="email~^.*-compute@" \
  --format="value(email)")

gcloud artifacts repositories add-iam-policy-binding my-images \
  --location=asia-east1 \
  --member="serviceAccount:$NODE_SA" \
  --role="roles/artifactregistry.reader"
```

## 5. List / clean up

```bash
# List images
gcloud artifacts docker images list \
  asia-east1-docker.pkg.dev/PROJECT/my-images

# List all tags for an image
gcloud artifacts docker images list \
  asia-east1-docker.pkg.dev/PROJECT/my-images/api \
  --include-tags

# Delete a tag
gcloud artifacts docker images delete \
  asia-east1-docker.pkg.dev/PROJECT/my-images/api:v1
```

## 6. Cleanup policies (auto-delete)

Without cleanup rules, repos grow indefinitely and get expensive. Set policies:

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

> Use `--dry-run` first to see what would be deleted, then apply.

## 7. Vulnerability scanning

Once enabled, Artifact Registry auto-scans images and lists CVEs:

```bash
gcloud services enable containerscanning.googleapis.com

# View vulnerabilities for an image
gcloud artifacts docker images list-vulnerabilities \
  asia-east1-docker.pkg.dev/PROJECT/my-images/api:v1
```

Pair with **Binary Authorization** to enforce that GKE / Cloud Run only deploy images without critical CVEs.

## 8. Other formats

### Python

```bash
gcloud artifacts repositories create my-pypi \
  --repository-format=python --location=asia-east1

# Print publish settings
gcloud artifacts print-settings python --repository=my-pypi --location=asia-east1
# Paste output into ~/.pypirc, then twine upload
```

### Maven

```bash
gcloud artifacts repositories create my-maven \
  --repository-format=maven --location=asia-east1

gcloud artifacts print-settings mvn --repository=my-maven --location=asia-east1
# Paste into pom.xml
```

## 9. Cleanup

```bash
gcloud artifacts repositories delete my-images --location=asia-east1
```

## 10. Common pitfalls

- **GKE can't pull image**: node SA missing `artifactregistry.reader`.
- **Local push 401**: forgot `gcloud auth configure-docker LOCATION-docker.pkg.dev`.
- **Cross-region pulls slow**: keep repo in the same region as GKE / Cloud Run.
- **Repo grows forever**: always set cleanup policies — CI generates lots of untagged images.
- **Can't change format**: a repo's format is fixed at creation. Make a new repo if needed.
