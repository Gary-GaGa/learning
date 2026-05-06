# Cloud Storage (GCS)

Object storage. Think of it as an **infinitely-large key-value store**: keys are paths, values are files. **Not a filesystem** — there are no real directories; `/` is just part of the key.

## 1. Core concepts

```
Bucket   ── globally-unique name, bound to a region/multi-region, default storage class
└── Object   ── one file + metadata, key has no length limit (can include /)
```

- **Bucket names are globally unique across all of GCP** (not just your project). Convention: `{org}-{project}-{purpose}`, e.g. `acme-data-lake-raw`.
- **Region matters**: cross-region reads/writes incur egress fees. Put buckets used by GKE in the same region as the cluster.

## 2. Storage classes

| Class | Use case | Min duration | Retrieval cost |
| --- | --- | --- | --- |
| `STANDARD` | Frequently accessed, hot data | None | Low |
| `NEARLINE` | Accessed ~once a month | 30 days | Medium |
| `COLDLINE` | Accessed ~once a quarter | 90 days | High |
| `ARCHIVE` | Accessed ~once a year, compliance backup | 365 days | Highest |

> Deleting cold data early triggers an "early deletion fee" — you're billed as if it stayed for the minimum duration.

Use **Object Lifecycle** to auto-transition: e.g. STANDARD → NEARLINE after 30 days, → ARCHIVE after a year.

## 3. Hands-on

> The new CLI is `gcloud storage` (replacing legacy `gsutil`). Commands below use the new one.

### Create a bucket

```bash
gcloud storage buckets create gs://YOUR-UNIQUE-BUCKET \
  --location=asia-east1 \
  --default-storage-class=STANDARD \
  --uniform-bucket-level-access     # IAM-only, no ACLs (recommended)
```

### Upload / download / list

```bash
# Upload
echo "hello gcs" > hello.txt
gcloud storage cp hello.txt gs://YOUR-UNIQUE-BUCKET/

# Upload a folder
gcloud storage cp -r ./data gs://YOUR-UNIQUE-BUCKET/data/

# List
gcloud storage ls gs://YOUR-UNIQUE-BUCKET/
gcloud storage ls -l gs://YOUR-UNIQUE-BUCKET/data/   # with size & mtime

# Download
gcloud storage cp gs://YOUR-UNIQUE-BUCKET/hello.txt ./

# Sync a folder (rsync-like)
gcloud storage rsync -r ./local-dir gs://YOUR-UNIQUE-BUCKET/dir/
```

### Grant permissions

```bash
# Read-only access for an SA on one bucket
gcloud storage buckets add-iam-policy-binding gs://YOUR-UNIQUE-BUCKET \
  --member="serviceAccount:gke-app@PROJECT.iam.gserviceaccount.com" \
  --role="roles/storage.objectViewer"

# Make public (careful! — only for public assets)
gcloud storage buckets add-iam-policy-binding gs://YOUR-UNIQUE-BUCKET \
  --member="allUsers" \
  --role="roles/storage.objectViewer"
```

### Signed URLs

Temporarily expose a private object to an external user:

```bash
gcloud storage sign-url gs://YOUR-UNIQUE-BUCKET/private.pdf \
  --duration=15m \
  --impersonate-service-account=signer@PROJECT.iam.gserviceaccount.com
```

The URL works for anyone for 15 minutes, then expires. Common pattern: front-end uses a signed URL to PUT directly, avoiding routing the file through your server.

### Versioning

Safety net against accidental delete/overwrite:

```bash
gcloud storage buckets update gs://YOUR-UNIQUE-BUCKET --versioning
```

Once on, deleted/overwritten objects become noncurrent versions — visible via `gcloud storage ls --all-versions`.

### Lifecycle rules

`lifecycle.json`:

```json
{
  "rule": [
    {
      "action": { "type": "SetStorageClass", "storageClass": "NEARLINE" },
      "condition": { "age": 30, "matchesStorageClass": ["STANDARD"] }
    },
    {
      "action": { "type": "Delete" },
      "condition": { "age": 365, "isLive": false }
    }
  ]
}
```

```bash
gcloud storage buckets update gs://YOUR-UNIQUE-BUCKET \
  --lifecycle-file=lifecycle.json
```

## 4. Programmatic access (Python)

```python
# pip install google-cloud-storage
from google.cloud import storage

client = storage.Client()                     # uses ADC
bucket = client.bucket("YOUR-UNIQUE-BUCKET")

# Write
blob = bucket.blob("greetings/hi.txt")
blob.upload_from_string("hello from python")

# Read
print(blob.download_as_text())

# List
for b in client.list_blobs("YOUR-UNIQUE-BUCKET", prefix="greetings/"):
    print(b.name, b.size, b.updated)
```

> When deployed to GKE with [Workload Identity](./02-gke.md#5-workload-identity-important), this code needs no key file.

## 5. Cleanup

```bash
# Bucket must be empty before deletion
gcloud storage rm -r gs://YOUR-UNIQUE-BUCKET/**
gcloud storage buckets delete gs://YOUR-UNIQUE-BUCKET
```

## 6. Common pitfalls

- **Bucket name taken**: globally unique — prefix with your org/project.
- **Cross-region transfers slow/expensive**: align bucket region with compute region.
- **Accidental public exposure**: enable `uniform-bucket-level-access` to avoid surprise ACL grants. Always check the **Public access** column in the console.
- **"Directory" operations**: GCS has no directories. `gs://b/a/b/c.txt` is one key. `/` is just a prefix for queries.
- **Many small objects = poor performance**: each PUT is a Class A operation. Bundle if possible (tar, parquet).
