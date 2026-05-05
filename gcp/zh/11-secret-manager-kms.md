# Secret Manager 與 Cloud KMS（敏感資料）

兩個常被搞混的服務：

| 服務 | 你存什麼 | 你做什麼操作 |
| --- | --- | --- |
| **Secret Manager** | **整段秘密**本身（密碼、API key、cert） | get / put |
| **Cloud KMS** | **金鑰**（加密用） | encrypt / decrypt / sign / verify（資料不留在 KMS） |

簡單記：要存「密碼字串」用 **Secret Manager**；要「拿金鑰加密自己的資料」或做 CMEK，用 **KMS**。

## 1. Secret Manager

### 1.1 概念

```
Secret (容器，e.g. db-password)
└── Version (v1, v2, v3 ...)        ← 真正的 payload，最大 64KB
```

- **永遠用 version 而不是 latest**（latest 跟著最新版跑，會在你不知情時換掉）。
- Versions 不可改，只能 `add` 或 `disable/destroy`。

### 1.2 動手做

```bash
# 建 secret
gcloud secrets create db-password \
  --replication-policy=automatic   # 或 user-managed 指定 region

# 加 version
echo -n "S3cure!Pass" | gcloud secrets versions add db-password --data-file=-

# 讀
gcloud secrets versions access latest --secret=db-password
gcloud secrets versions access 2 --secret=db-password
```

### 1.3 給服務存取權

```bash
gcloud secrets add-iam-policy-binding db-password \
  --member="serviceAccount:app-runner@PROJECT.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
```

**只給 accessor，不要給 admin**——admin 可以改/刪 secret。

### 1.4 在各服務的整合

**Cloud Run**：直接掛成 env 或檔案，啟動時自動讀（毋需 SDK 程式碼）：

```bash
gcloud run services update hello \
  --update-secrets="DB_PASSWORD=db-password:latest" \
  --region=asia-east1
# 或掛成檔案：--update-secrets=/etc/secrets/db=db-password:latest
```

**GKE**：用 [Secret Manager CSI driver](https://secrets-store-csi-driver.sigs.k8s.io/) 把 secret 投影成檔案；或直接在 init container 用 SDK 讀。**避免**把 Secret Manager 內容複製到 K8s `Secret`（多一層暴露面）。

**Python SDK**：

```python
# pip install google-cloud-secret-manager
from google.cloud import secretmanager
client = secretmanager.SecretManagerServiceClient()

name = "projects/PROJECT/secrets/db-password/versions/latest"
resp = client.access_secret_version(request={"name": name})
print(resp.payload.data.decode("utf-8"))
```

### 1.5 輪換（Rotation）

Secret Manager 不會自動換密碼，但能設**輪換通知**：

```bash
gcloud secrets update db-password \
  --next-rotation-time="2026-08-01T00:00:00Z" \
  --rotation-period="90d" \
  --topic=projects/PROJECT/topics/secret-rotations
```

到時間時推訊息到 Pub/Sub topic，由你自己的 worker 去產生新版、`gcloud secrets versions add` 再通知服務 reload。

## 2. Cloud KMS

### 2.1 概念階層

```
KeyRing (location 綁定，e.g. asia-east1)
└── CryptoKey
    ├── Purpose: ENCRYPT_DECRYPT / ASYMMETRIC_SIGN / MAC ...
    └── Versions (1, 2, 3 ...)   ← 實際的金鑰素材
```

- **Location 重要**：global / region / multi-region。要做 CMEK 給 GCS，金鑰必須跟 bucket 同 location。
- Key material 永遠不會出 KMS。你只能呼叫 `encrypt` / `decrypt`，把 plaintext / ciphertext 傳進來。

### 2.2 兩種等級

| 等級 | 說明 |
| --- | --- |
| **Software**（標準） | 多數情況夠用 |
| **HSM** | 金鑰存在 FIPS 140-2 Level 3 HSM 內，合規要求高才用，較貴 |
| **External Key Manager (EKM)** | 金鑰留在你自己的 HSM / 外部 KMS，GCP 只是調用 |

### 2.3 建 keyring + key

```bash
gcloud kms keyrings create app-keys --location=asia-east1

gcloud kms keys create app-data-key \
  --location=asia-east1 \
  --keyring=app-keys \
  --purpose=encryption \
  --rotation-period=90d \
  --next-rotation-time=$(date -u -d '+90 days' +%FT%TZ)
```

> KMS 自動輪換時，舊版本仍可用來 decrypt 舊 ciphertext，新加密走最新版。

### 2.4 加解密小資料（< 64KB）

```bash
# 加密
echo -n "very secret" | gcloud kms encrypt \
  --location=asia-east1 --keyring=app-keys --key=app-data-key \
  --plaintext-file=- --ciphertext-file=- \
  | base64 > out.b64

# 解密
base64 -d out.b64 | gcloud kms decrypt \
  --location=asia-east1 --keyring=app-keys --key=app-data-key \
  --ciphertext-file=- --plaintext-file=-
```

### 2.5 Envelope encryption（大資料）

KMS 一次最多 64KB，大檔的標準做法：

1. 本地產一把 **DEK**（data encryption key，AES-256）
2. 用 DEK 加密大檔
3. 把 DEK 用 KMS 的 KEK 加密 → 得到 wrapped DEK
4. 把 ciphertext + wrapped DEK 一起存

解密時反向操作。Tink / google-cloud-kms client library 有現成的 envelope helper，不要自己 hand-roll。

### 2.6 CMEK（Customer-Managed Encryption Keys）

讓 GCP 服務（GCS、BigQuery、Cloud SQL、GKE PD…）用你的 KMS key 來加密底層儲存，而不是 Google 預設的金鑰。

```bash
# 給服務的 SA 用金鑰的權限
gcloud kms keys add-iam-policy-binding app-data-key \
  --location=asia-east1 --keyring=app-keys \
  --member="serviceAccount:service-PROJECT_NUMBER@gs-project-accounts.iam.gserviceaccount.com" \
  --role="roles/cloudkms.cryptoKeyEncrypterDecrypter"

# GCS bucket 套 CMEK
gcloud storage buckets update gs://YOUR-BUCKET \
  --default-encryption-key=projects/PROJECT/locations/asia-east1/keyRings/app-keys/cryptoKeys/app-data-key
```

**好處**：你可以 **disable / destroy 金鑰** = 立刻撤回所有用該 key 加密過的資料的存取（cryptographic erase）。
**代價**：服務不能在沒 key 時運作；每筆操作多一次 KMS call（會計費，但通常便宜）。

### 2.7 簽章 / 驗章

```bash
gcloud kms keys create app-sign \
  --location=asia-east1 --keyring=app-keys \
  --purpose=asymmetric-signing \
  --default-algorithm=ec-sign-p256-sha256

# 簽
gcloud kms asymmetric-sign --location=asia-east1 \
  --keyring=app-keys --key=app-sign --version=1 \
  --digest-algorithm=sha256 --input-file=msg.txt --signature-file=msg.sig

# 拿 public key 在外部驗
gcloud kms keys versions get-public-key 1 \
  --location=asia-east1 --keyring=app-keys --key=app-sign > pub.pem
```

## 3. Secret Manager vs KMS：怎麼選

| 場景 | 用什麼 |
| --- | --- |
| 存 DB 密碼、API key、TLS cert | **Secret Manager** |
| 加密自家應用的欄位（PII、token） | KMS（envelope encryption） |
| GCS / BQ / SQL 底層加密金鑰自管 | **KMS（CMEK）** |
| JWT / API request 簽章 | **KMS（asymmetric signing）** |
| 又要存又要 audit 存取 | 兩個都要 audit log（自動進 Cloud Logging） |

## 4. 觀測與審計

兩者所有操作都會自動寫進 **Cloud Audit Logs**：

```
# 看誰存取了某個 secret
protoPayload.serviceName="secretmanager.googleapis.com"
protoPayload.methodName="google.cloud.secretmanager.v1.SecretManagerService.AccessSecretVersion"
protoPayload.resourceName=~"projects/.*/secrets/db-password"
```

設告警：「半夜有人 access 生產環境 secret」。

## 5. 常見坑

- **把 secret commit 進 git**：用 [pre-commit hook + gitleaks](https://github.com/gitleaks/gitleaks)、SCM 端的 secret scanning。
- **用 latest 抓 secret 又快取**：版本一旦轉滾，舊 instance 仍在用舊密碼（可能是想要的，也可能不是）。應用要能 hot-reload，或用具體 version 並做 deploy 觸發。
- **Secret 設成 env var 寫進 image**：image 可被 pull 就洩漏。Cloud Run / GKE 都是**啟動時**才注入，不要 bake 進 image。
- **KMS key 跨 region 會被擋**：CMEK 對象（bucket / instance）跟 key 必須**同 location**（global key 大多不能用）。
- **Destroyed key 不能救**：destroy 是延遲 24h 後真刪，期間可 restore，過了就回不來。
- **Permission 太大**：給 `secretmanager.admin` 等於把該 secret 給人改/刪。一律只給 `secretmanager.secretAccessor`。
- **沒 audit log**：Secret/KMS 的 Data Access logs 預設是**關閉**的。Console → IAM → Audit Logs，把 `Data Read` 開起來，否則查不到「誰讀過」。
