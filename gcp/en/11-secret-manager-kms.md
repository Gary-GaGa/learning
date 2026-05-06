# Secret Manager and Cloud KMS (Sensitive Data)

Two services that get confused often:

| Service | What you store | What you do with it |
| --- | --- | --- |
| **Secret Manager** | The **secret payload itself** (passwords, API keys, certs) | get / put |
| **Cloud KMS** | **Keys** (for crypto) | encrypt / decrypt / sign / verify (data does not stay in KMS) |

Quick rule: storing a "password string" → **Secret Manager**; encrypting your own data with a key, or doing CMEK → **KMS**.

## 1. Secret Manager

### 1.1 Concepts

```
Secret (container, e.g. db-password)
└── Version (v1, v2, v3 ...)        ← actual payload, max 64KB
```

- **Always pin a version, not `latest`** (`latest` follows the newest, so it can change behind you).
- Versions are immutable — you only `add`, `disable`, or `destroy`.

### 1.2 Hands-on

```bash
# Create a secret
gcloud secrets create db-password \
  --replication-policy=automatic   # or user-managed for specific regions

# Add a version
echo -n "S3cure!Pass" | gcloud secrets versions add db-password --data-file=-

# Read
gcloud secrets versions access latest --secret=db-password
gcloud secrets versions access 2 --secret=db-password
```

### 1.3 Grant access

```bash
gcloud secrets add-iam-policy-binding db-password \
  --member="serviceAccount:app-runner@PROJECT.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
```

**Grant `secretAccessor`, not `admin`** — admin can modify or delete the secret.

### 1.4 Service integrations

**Cloud Run**: mount as env var or file; resolved at start, no SDK code:

```bash
gcloud run services update hello \
  --update-secrets="DB_PASSWORD=db-password:latest" \
  --region=asia-east1
# Or as a file: --update-secrets=/etc/secrets/db=db-password:latest
```

**GKE**: use the [Secret Manager CSI driver](https://secrets-store-csi-driver.sigs.k8s.io/) to project secrets as files; or read in an init container via SDK. **Avoid** copying Secret Manager content into a K8s `Secret` (extra exposure surface).

**Python SDK**:

```python
# pip install google-cloud-secret-manager
from google.cloud import secretmanager
client = secretmanager.SecretManagerServiceClient()

name = "projects/PROJECT/secrets/db-password/versions/latest"
resp = client.access_secret_version(request={"name": name})
print(resp.payload.data.decode("utf-8"))
```

### 1.5 Rotation

Secret Manager doesn't auto-rotate the value, but it can fire **rotation notifications**:

```bash
gcloud secrets update db-password \
  --next-rotation-time="2026-08-01T00:00:00Z" \
  --rotation-period="90d" \
  --topic=projects/PROJECT/topics/secret-rotations
```

At the scheduled time, a Pub/Sub message fires; your worker generates the new value, runs `gcloud secrets versions add`, and signals services to reload.

## 2. Cloud KMS

### 2.1 Hierarchy

```
KeyRing (location-bound, e.g. asia-east1)
└── CryptoKey
    ├── Purpose: ENCRYPT_DECRYPT / ASYMMETRIC_SIGN / MAC ...
    └── Versions (1, 2, 3 ...)   ← actual key material
```

- **Location matters**: global / region / multi-region. For CMEK on a GCS bucket, the key must be in the same location as the bucket.
- Key material never leaves KMS. You only call `encrypt` / `decrypt`, sending plaintext / ciphertext through.

### 2.2 Protection levels

| Level | Description |
| --- | --- |
| **Software** (default) | Sufficient for most cases |
| **HSM** | FIPS 140-2 Level 3 HSM-backed; for high-compliance use; pricier |
| **External Key Manager (EKM)** | Key stays in your own HSM / external KMS; GCP only invokes it |

### 2.3 Create keyring + key

```bash
gcloud kms keyrings create app-keys --location=asia-east1

gcloud kms keys create app-data-key \
  --location=asia-east1 \
  --keyring=app-keys \
  --purpose=encryption \
  --rotation-period=90d \
  --next-rotation-time=$(date -u -d '+90 days' +%FT%TZ)
```

> When KMS auto-rotates, old versions remain usable for decrypting old ciphertext; new encryptions use the latest version.

### 2.4 Encrypt / decrypt small data (< 64KB)

```bash
# Encrypt
echo -n "very secret" | gcloud kms encrypt \
  --location=asia-east1 --keyring=app-keys --key=app-data-key \
  --plaintext-file=- --ciphertext-file=- \
  | base64 > out.b64

# Decrypt
base64 -d out.b64 | gcloud kms decrypt \
  --location=asia-east1 --keyring=app-keys --key=app-data-key \
  --ciphertext-file=- --plaintext-file=-
```

### 2.5 Envelope encryption (large data)

KMS limits each call to 64KB. Standard pattern for big payloads:

1. Generate a local **DEK** (data encryption key, AES-256)
2. Encrypt the data with the DEK
3. Wrap the DEK with the KMS KEK → wrapped DEK
4. Store ciphertext + wrapped DEK together

Decryption is the reverse. Use Tink or google-cloud-kms's envelope helpers — don't hand-roll this.

### 2.6 CMEK (Customer-Managed Encryption Keys)

Have GCP services (GCS, BigQuery, Cloud SQL, GKE PD…) encrypt underlying storage with **your** KMS key instead of Google's default key.

```bash
# Grant the service's SA permission to use the key
gcloud kms keys add-iam-policy-binding app-data-key \
  --location=asia-east1 --keyring=app-keys \
  --member="serviceAccount:service-PROJECT_NUMBER@gs-project-accounts.iam.gserviceaccount.com" \
  --role="roles/cloudkms.cryptoKeyEncrypterDecrypter"

# Apply CMEK to a GCS bucket
gcloud storage buckets update gs://YOUR-BUCKET \
  --default-encryption-key=projects/PROJECT/locations/asia-east1/keyRings/app-keys/cryptoKeys/app-data-key
```

**Upside**: you can **disable / destroy the key** to revoke access to all data encrypted with it (cryptographic erase).
**Cost**: service can't operate without the key; each crypto op makes a KMS call (small fee, but counts).

### 2.7 Signing / verifying

```bash
gcloud kms keys create app-sign \
  --location=asia-east1 --keyring=app-keys \
  --purpose=asymmetric-signing \
  --default-algorithm=ec-sign-p256-sha256

# Sign
gcloud kms asymmetric-sign --location=asia-east1 \
  --keyring=app-keys --key=app-sign --version=1 \
  --digest-algorithm=sha256 --input-file=msg.txt --signature-file=msg.sig

# Export public key for external verification
gcloud kms keys versions get-public-key 1 \
  --location=asia-east1 --keyring=app-keys --key=app-sign > pub.pem
```

## 3. Secret Manager vs KMS: which one?

| Scenario | Use |
| --- | --- |
| Store DB password, API key, TLS cert | **Secret Manager** |
| Encrypt fields in your own app (PII, tokens) | KMS (envelope encryption) |
| Manage encryption key for GCS / BQ / SQL storage | **KMS (CMEK)** |
| JWT / API request signing | **KMS (asymmetric signing)** |
| Storage with audit trail | Both audit-log automatically (Cloud Logging) |

## 4. Audit and observability

All operations on both services flow into **Cloud Audit Logs**:

```
# Who accessed a particular secret
protoPayload.serviceName="secretmanager.googleapis.com"
protoPayload.methodName="google.cloud.secretmanager.v1.SecretManagerService.AccessSecretVersion"
protoPayload.resourceName=~"projects/.*/secrets/db-password"
```

Set an alert for "production secret accessed at 3am".

## 5. Common pitfalls

- **Committing secrets to git**: use [pre-commit + gitleaks](https://github.com/gitleaks/gitleaks) and SCM-side secret scanning.
- **Using `latest` and caching it**: after rotation, old instances keep using the old value (sometimes intentional, often not). Hot-reload, or pin to a specific version and trigger deploys.
- **Secret baked into image as env**: anyone who pulls the image gets it. Cloud Run / GKE inject **at startup** — never bake into the image.
- **KMS key wrong location**: CMEK targets (bucket / instance) and key must share the same location (global keys usually aren't allowed).
- **Destroyed key is gone**: destroy is a 24h scheduled delete; you can restore within that window, otherwise it's permanent.
- **Over-permissioned**: granting `secretmanager.admin` lets people modify/delete the secret. Grant only `secretmanager.secretAccessor`.
- **No audit logs**: Secret / KMS **Data Access** logs are **off by default**. Console → IAM → Audit Logs → enable `Data Read`, otherwise "who read it" is invisible.
