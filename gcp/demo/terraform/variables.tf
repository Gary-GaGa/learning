variable "project" {
  type        = string
  description = "GCP project ID"
}

variable "region" {
  type        = string
  description = "Default region"
  default     = "asia-east1"
}

variable "name_prefix" {
  type        = string
  description = "Prefix for created resources"
  default     = "demo"
}
