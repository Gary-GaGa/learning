terraform {
  required_version = ">= 1.6"
  required_providers {
    google = { source = "hashicorp/google", version = "~> 5.0" }
    random = { source = "hashicorp/random", version = "~> 3.5" }
  }
}

provider "google" {
  project = var.project
  region  = var.region
}

# ---------------------------------------------------------------------
# APIs
# ---------------------------------------------------------------------
locals {
  apis = [
    "run.googleapis.com",
    "pubsub.googleapis.com",
    "storage.googleapis.com",
    "sqladmin.googleapis.com",
    "secretmanager.googleapis.com",
    "artifactregistry.googleapis.com",
    "iam.googleapis.com",
  ]
}

resource "google_project_service" "enabled" {
  for_each           = toset(local.apis)
  service            = each.value
  disable_on_destroy = false
}

# ---------------------------------------------------------------------
# Artifact Registry (image storage)
# ---------------------------------------------------------------------
resource "google_artifact_registry_repository" "images" {
  location      = var.region
  repository_id = var.name_prefix
  format        = "DOCKER"
  description   = "Demo images"
  depends_on    = [google_project_service.enabled]
}

# ---------------------------------------------------------------------
# GCS bucket (invoices)
# ---------------------------------------------------------------------
resource "google_storage_bucket" "invoices" {
  name                        = "${var.project}-${var.name_prefix}-invoices"
  location                    = var.region
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"
  force_destroy               = true # demo only
  depends_on                  = [google_project_service.enabled]
}

# ---------------------------------------------------------------------
# Pub/Sub topic
# ---------------------------------------------------------------------
resource "google_pubsub_topic" "orders" {
  name       = "${var.name_prefix}-orders"
  depends_on = [google_project_service.enabled]
}

# ---------------------------------------------------------------------
# Cloud SQL (Postgres) — for demo, public IP. Production should use Private IP.
# ---------------------------------------------------------------------
resource "random_password" "db" {
  length  = 24
  special = false
}

resource "google_secret_manager_secret" "db_password" {
  secret_id = "${var.name_prefix}-db-password"
  replication {
    auto {}
  }
  depends_on = [google_project_service.enabled]
}

resource "google_secret_manager_secret_version" "db_password" {
  secret      = google_secret_manager_secret.db_password.id
  secret_data = random_password.db.result
}

resource "google_sql_database_instance" "pg" {
  name             = "${var.name_prefix}-pg"
  database_version = "POSTGRES_15"
  region           = var.region

  settings {
    tier              = "db-custom-1-3840"
    availability_type = "ZONAL" # demo
    disk_size         = 10
    disk_type         = "PD_SSD"

    backup_configuration {
      enabled = true
    }

    ip_configuration {
      ipv4_enabled = true
      # For demo only — restrict via authorized networks or use Private IP in prod.
    }
  }

  deletion_protection = false # demo
  depends_on          = [google_project_service.enabled]
}

resource "google_sql_database" "orders" {
  name     = "orders"
  instance = google_sql_database_instance.pg.name
}

resource "google_sql_user" "app" {
  name     = "app"
  instance = google_sql_database_instance.pg.name
  password = random_password.db.result
}

# ---------------------------------------------------------------------
# Service accounts (one per service, minimum perms)
# ---------------------------------------------------------------------
resource "google_service_account" "api" {
  account_id   = "${var.name_prefix}-api"
  display_name = "Demo api runtime SA"
}

resource "google_service_account" "worker" {
  account_id   = "${var.name_prefix}-worker"
  display_name = "Demo worker runtime SA"
}

resource "google_service_account" "pushinvoker" {
  account_id   = "${var.name_prefix}-push"
  display_name = "Pub/Sub push invoker"
}

# api: write GCS, publish Pub/Sub
resource "google_storage_bucket_iam_member" "api_can_write" {
  bucket = google_storage_bucket.invoices.name
  role   = "roles/storage.objectCreator"
  member = "serviceAccount:${google_service_account.api.email}"
}

resource "google_pubsub_topic_iam_member" "api_can_publish" {
  topic  = google_pubsub_topic.orders.name
  role   = "roles/pubsub.publisher"
  member = "serviceAccount:${google_service_account.api.email}"
}

# worker: read secret, connect Cloud SQL
resource "google_secret_manager_secret_iam_member" "worker_can_read_secret" {
  secret_id = google_secret_manager_secret.db_password.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.worker.email}"
}

resource "google_project_iam_member" "worker_cloudsql_client" {
  project = var.project
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.worker.email}"
}

# ---------------------------------------------------------------------
# Cloud Run services (initially deployed with placeholder image; deploy.sh
# replaces the image after building)
# ---------------------------------------------------------------------
locals {
  placeholder_image = "gcr.io/cloudrun/hello"
}

resource "google_cloud_run_v2_service" "api" {
  name     = "${var.name_prefix}-api"
  location = var.region

  template {
    service_account = google_service_account.api.email

    containers {
      image = local.placeholder_image
      env {
        name  = "PROJECT_ID"
        value = var.project
      }
      env {
        name  = "INVOICE_BUCKET"
        value = google_storage_bucket.invoices.name
      }
      env {
        name  = "ORDERS_TOPIC"
        value = google_pubsub_topic.orders.name
      }
    }
  }

  depends_on = [
    google_storage_bucket_iam_member.api_can_write,
    google_pubsub_topic_iam_member.api_can_publish,
  ]
}

resource "google_cloud_run_v2_service_iam_member" "api_public" {
  name     = google_cloud_run_v2_service.api.name
  location = google_cloud_run_v2_service.api.location
  role     = "roles/run.invoker"
  member   = "allUsers"
}

resource "google_cloud_run_v2_service" "worker" {
  name     = "${var.name_prefix}-worker"
  location = var.region

  template {
    service_account = google_service_account.worker.email

    containers {
      image = local.placeholder_image

      env {
        name  = "PROJECT_ID"
        value = var.project
      }
      env {
        name  = "DB_INSTANCE"
        value = google_sql_database_instance.pg.connection_name
      }
      env {
        name  = "DB_NAME"
        value = google_sql_database.orders.name
      }
      env {
        name  = "DB_USER"
        value = google_sql_user.app.name
      }
      env {
        name = "DB_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.db_password.secret_id
            version = "latest"
          }
        }
      }
      env {
        name  = "PUSH_AUDIENCE"
        value = "" # filled in via lifecycle below; see note in README
      }
    }
  }

  depends_on = [
    google_secret_manager_secret_version.db_password,
    google_secret_manager_secret_iam_member.worker_can_read_secret,
    google_project_iam_member.worker_cloudsql_client,
  ]
}

# Allow the push SA to invoke the worker (and only the push SA)
resource "google_cloud_run_v2_service_iam_member" "worker_push_invoker" {
  name     = google_cloud_run_v2_service.worker.name
  location = google_cloud_run_v2_service.worker.location
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.pushinvoker.email}"
}

# ---------------------------------------------------------------------
# Pub/Sub push subscription with OIDC auth → worker
# ---------------------------------------------------------------------
resource "google_pubsub_subscription" "orders" {
  name  = "${var.name_prefix}-orders-sub"
  topic = google_pubsub_topic.orders.name

  ack_deadline_seconds = 30

  push_config {
    push_endpoint = "${google_cloud_run_v2_service.worker.uri}/pubsub"

    oidc_token {
      service_account_email = google_service_account.pushinvoker.email
      audience              = google_cloud_run_v2_service.worker.uri
    }
  }

  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "600s"
  }
}
