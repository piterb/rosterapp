variable "project_id" {
  description = "GCP project ID"
  type        = string
}

variable "region" {
  description = "GCP region"
  type        = string
  default     = "REPLACE_WITH_REGION"
}

variable "artifact_repo_name" {
  description = "Artifact Registry repository name"
  type        = string
  default     = null
}

variable "cloud_run_service_name" {
  description = "Cloud Run service name"
  type        = string
  default     = null
}

variable "runtime_service_account_id" {
  description = "Cloud Run runtime service account ID"
  type        = string
  default     = "cloud-run-runtime"
}

variable "tf_admin_service_account_id" {
  description = "Terraform admin service account ID (used to grant actAs on runtime SA)"
  type        = string
  default     = "tf-admin"
}

variable "github_repo" {
  description = "GitHub repo in ORG/REPO format (used for WIF bindings)"
  type        = string
}

variable "wif_pool_id" {
  description = "Workload Identity Pool ID"
  type        = string
  default     = "github-pool"
}

variable "allow_unauthenticated" {
  description = "Allow public access to the Cloud Run service"
  type        = bool
  default     = true
}

variable "cloud_run_min_instances" {
  description = "Minimum Cloud Run instances"
  type        = number
  default     = 0
}

variable "cloud_run_max_instances" {
  description = "Maximum Cloud Run instances"
  type        = number
  default     = 1
}

variable "cloud_run_image" {
  description = "Container image URI for Cloud Run"
  type        = string
  default     = "gcr.io/cloudrun/hello"
}

variable "identity_google_client_id" {
  description = "OAuth client ID for Google Identity Platform Google provider"
  type        = string
}

variable "identity_google_client_secret" {
  description = "OAuth client secret for Google Identity Platform Google provider"
  type        = string
  sensitive   = true
}
