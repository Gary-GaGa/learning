# 00 — Overview: Where to start

For readers **opening GCP for the first time**. One diagram for how all the topics relate, plus a reading order that won't confuse you.

## 1. GCP service landscape (one picture)

```mermaid
flowchart TB
  subgraph foundation[Foundation]
    IAM[IAM / Org / Project<br/>permissions and billing]
    NET[VPC / Networking<br/>internal network]
    LOG[Logging / Monitoring<br/>see what happened]
    BILL[Billing / FinOps<br/>see what it cost]
  end

  subgraph compute[Compute]
    GCE[Compute Engine<br/>VM]
    GKE[GKE<br/>Kubernetes]
    RUN[Cloud Run<br/>serverless container]
  end

  subgraph data[Data]
    GCS[Cloud Storage<br/>objects]
    SQL[Cloud SQL<br/>relational DB]
    BQ[BigQuery<br/>data warehouse]
    PS[Pub/Sub<br/>message bus]
  end

  subgraph platform[Platform]
    AR[Artifact Registry<br/>image / package]
    SM[Secret Manager / KMS<br/>secrets / keys]
    LB[HTTPS LB / Cloud Armor<br/>public ingress]
  end

  subgraph delivery[Delivery]
    CB[Cloud Build / Deploy<br/>CI/CD]
    TF[Terraform<br/>IaC]
  end

  IAM --> compute
  IAM --> data
  NET --> compute
  NET --> SQL
  AR --> RUN & GKE
  SM --> RUN & GKE & GCE
  LB --> RUN & GKE & GCE
  compute --> GCS & SQL & BQ & PS
  PS --> RUN & GKE
  TF -.manages.-> compute & data & platform & delivery
  CB -.deploys.-> compute
  LOG & BILL -.observes.-> compute & data & platform
```

**How to read**: foundation (IAM, network, observability, billing) holds everything up; compute does work; data stores it; platform manages secrets and ingress; delivery automates it.

## 2. Topic number ↔ service ↔ when to read

```mermaid
flowchart LR
  s1["01 Fundamentals<br/>(IAM / project / gcloud)"] --> s3[03 Cloud Storage]
  s1 --> s4[04 Pub/Sub]
  s1 --> s12[12 Compute Engine]
  s3 --> s8[08 Artifact Registry]
  s4 --> s5[05 Cloud Run]
  s8 --> s5
  s8 --> s2[02 GKE]
  s5 --> s7[07 Cloud SQL]
  s5 --> s11[11 Secret / KMS]
  s2 --> s9[09 VPC]
  s12 --> s9
  s9 --> s15[15 HTTPS LB]
  s5 --> s10[10 Observability]
  s5 --> s6[06 BigQuery]
  s10 --> s13[13 Cost Mgmt]
  s11 --> s14[14 IAM Advanced]
  s5 --> s16[16 CI/CD]
  s16 --> s17[17 Terraform]
  s17 --> demo[demo/<br/>end-to-end]

  style demo fill:#fde68a,stroke:#b45309
```

> Numbers are a **suggested order**, not a strict dependency. Skip around if you need to. The yellow `demo/` assumes you've read the rest.

## 3. Three learning paths

Pick one; saves half the time:

### 🚀 Path A: "Ship a web service to GCP"

Shortest path to production:

```mermaid
flowchart LR
  A[01 Fundamentals] --> B[08 Artifact Registry]
  B --> C[05 Cloud Run]
  C --> D[11 Secret/KMS]
  D --> E[15 HTTPS LB]
  E --> F[10 Observability]
  F --> G[16 CI/CD]
  G --> H[17 Terraform]
```

You can skip GKE / GCE / Pub/Sub / BigQuery for now.

### 📊 Path B: "Build a data analytics platform"

```mermaid
flowchart LR
  A[01 Fundamentals] --> B[03 Cloud Storage]
  B --> C[04 Pub/Sub]
  C --> D[06 BigQuery]
  D --> E[10 Observability]
  E --> F[13 Cost Mgmt]
```

**13 Cost Mgmt is critical for BQ** — a single `SELECT *` can cost hundreds of dollars.

### ☸️ Path C: "I know K8s, moving to GKE"

```mermaid
flowchart LR
  A[01 Fundamentals] --> B[09 VPC]
  B --> C[08 Artifact Registry]
  C --> D[02 GKE]
  D --> E[14 IAM Advanced<br/>Workload Identity]
  E --> F[10 Observability]
  F --> G[15 HTTPS LB]
  G --> H[17 Terraform]
```

## 4. Glossary (the easily-confused)

| Term | What it is | Common misconception |
| --- | --- | --- |
| **Project** | Unit of billing / IAM / APIs (every resource belongs to one) | Confused with Folder / Org; Project ID (string) and Project Number (digits) both exist |
| **Service Account (SA)** | Identity for programs; an IAM principal | Not a "service config file"; **GCP SA email is different from K8s ServiceAccount** |
| **ADC** | Application Default Credentials | Not a file — the SDK's credential lookup order |
| **Workload Identity** | Lets GKE Pods / Cloud Run auto-fetch a GCP identity | Don't confuse with Workload Identity Federation (WIF, for external identities) |
| **Region / Zone** | Region = geographic area (asia-east1); Zone = a datacenter inside a region (asia-east1-a/b/c) | GCS bucket calls it "location" but uses region names |
| **Tag vs Label** | Label = pure tagging (billing/search); Tag = has IAM model, usable in Org Policy conditions | Both terms used loosely — easy to mix up |
| **GCS Class A vs B ops** | A = writes (PUT, LIST); B = reads (GET) | A is 10x+ pricier than B — many small uploads hurt |
| **VPC (GCP)** | A **global** object; only subnets are bound to a region | Different from AWS — one VPC can span regions |
| **Egress** | Outbound traffic | Cross-region or internet egress is the expensive kind; intra-region is mostly free |
| **CMEK / CSEK** | CMEK = customer-managed key (KMS); CSEK = customer-supplied key | CMEK covers 99% of cases; CSEK is rarely used |

## 5. Prerequisites

You **don't** need: GCP experience.

You **should** know:

- Basic Linux / shell (`cd`, `curl`, env vars)
- Basic networking (IP, DNS, TLS, HTTP status codes)
- Basic Docker (image, container, Dockerfile)
- Basic Git / GitHub

If you're new to K8s, skip the hands-on parts of 02-gke for now — concepts are still useful.

## 6. Three things to do before any hands-on

1. **Create a dedicated GCP project**: don't reuse a real project; experiments can delete things.
2. **Set budget alerts**: Console → Billing → Budgets. Tiered alerts at $10 / $20 / $50.
3. **Install gcloud + run two logins**:
   ```bash
   gcloud auth login                       # for CLI
   gcloud auth application-default login   # for SDKs / Terraform
   ```

Then start with [01-fundamentals](./01-fundamentals.md).

## 7. When you're stuck

- **Permission / command errors**: see the decision trees in [`troubleshooting.md`](./troubleshooting.md).
- **Concept questions**: every topic ends with "Common pitfalls" — usually the exact bug a beginner hits.
- **Official docs**: `https://cloud.google.com/<service>/docs` (e.g. `/run/docs`).
- **Search tip**: prefix `site:cloud.google.com` to filter out clickbait.
