output "api_url" {
  description = "Public URL of the api service"
  value       = google_cloud_run_v2_service.api.uri
}

output "worker_url" {
  description = "URL of the worker (Pub/Sub push target)"
  value       = google_cloud_run_v2_service.worker.uri
}

output "image_repo" {
  description = "Artifact Registry path"
  value       = "${google_artifact_registry_repository.images.location}-docker.pkg.dev/${var.project}/${google_artifact_registry_repository.images.repository_id}"
}

output "invoice_bucket" {
  value = google_storage_bucket.invoices.name
}

output "db_connection_name" {
  value = google_sql_database_instance.pg.connection_name
}
