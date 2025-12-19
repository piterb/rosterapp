data "google_project" "current" {
  project_id = var.project_id
}

locals {
  required_apis = [
    "run.googleapis.com",
    "artifactregistry.googleapis.com",
    "secretmanager.googleapis.com",
    "identitytoolkit.googleapis.com",
    "apikeys.googleapis.com",
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "serviceusage.googleapis.com",
  ]
  artifact_repo_name     = coalesce(var.artifact_repo_name, "${var.project_id}-artifact")
  cloud_run_service_name = coalesce(var.cloud_run_service_name, "${var.project_id}-service")
  tf_admin_sa_email      = "${var.tf_admin_service_account_id}@${var.project_id}.iam.gserviceaccount.com"
  wif_principal_set      = "principalSet://iam.googleapis.com/projects/${data.google_project.current.number}/locations/global/workloadIdentityPools/${var.wif_pool_id}/attribute.repository/${var.github_repo}"
}

resource "google_project_service" "required" {
  for_each = toset(local.required_apis)

  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}

resource "google_identity_platform_config" "default" {
  provider = google-beta
  project  = var.project_id

  sign_in {
    email {
      enabled           = true
      password_required = true
    }
  }

  depends_on = [google_project_service.required]
}

resource "google_identity_platform_default_supported_idp_config" "google" {
  provider      = google-beta
  idp_id        = "google.com"
  client_id     = var.identity_google_client_id
  client_secret = var.identity_google_client_secret
  enabled       = true

  depends_on = [google_project_service.required]
}

resource "google_apikeys_key" "identity_platform" {
  name         = "${var.project_id}-identity-platform-key"
  display_name = "identity-platform-api-key"
  project      = var.project_id

  restrictions {
    api_targets {
      service = "identitytoolkit.googleapis.com"
    }
  }

  depends_on = [google_project_service.required]
}

resource "google_service_account" "runtime" {
  project      = var.project_id
  account_id   = var.runtime_service_account_id
  display_name = "Cloud Run Runtime"
}

resource "google_service_account_iam_member" "runtime_act_as" {
  service_account_id = google_service_account.runtime.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${local.tf_admin_sa_email}"
}

resource "google_service_account_iam_member" "runtime_self_act_as" {
  service_account_id = google_service_account.runtime.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.runtime.email}"
}

resource "google_service_account_iam_member" "runtime_wif_user" {
  service_account_id = google_service_account.runtime.name
  role               = "roles/iam.workloadIdentityUser"
  member             = local.wif_principal_set
}

resource "google_service_account_iam_member" "runtime_wif_token_creator" {
  service_account_id = google_service_account.runtime.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = local.wif_principal_set
}

resource "google_service_account_iam_member" "runtime_wif_act_as" {
  service_account_id = google_service_account.runtime.name
  role               = "roles/iam.serviceAccountUser"
  member             = local.wif_principal_set
}

resource "google_project_iam_member" "runtime_secret_access" {
  project = var.project_id
  role    = "roles/secretmanager.secretAccessor"
  member  = "serviceAccount:${google_service_account.runtime.email}"
}

resource "google_project_iam_member" "runtime_secret_admin" {
  project = var.project_id
  role    = "roles/secretmanager.admin"
  member  = "serviceAccount:${google_service_account.runtime.email}"
}

resource "google_project_iam_member" "runtime_artifact_registry_writer" {
  project = var.project_id
  role    = "roles/artifactregistry.writer"
  member  = "serviceAccount:${google_service_account.runtime.email}"
}

resource "google_project_iam_member" "runtime_run_admin" {
  project = var.project_id
  role    = "roles/run.admin"
  member  = "serviceAccount:${google_service_account.runtime.email}"
}

# Grant the Terraform admin SA permissions to manage Identity Platform and API Keys.
resource "google_project_iam_member" "tf_admin_identity_platform" {
  project = var.project_id
  role    = "roles/identitytoolkit.admin"
  member  = "serviceAccount:${local.tf_admin_sa_email}"
}

resource "google_project_iam_member" "tf_admin_apikey_admin" {
  project = var.project_id
  role    = "roles/apikeys.admin"
  member  = "serviceAccount:${local.tf_admin_sa_email}"
}

resource "google_artifact_registry_repository" "docker" {
  project       = var.project_id
  location      = var.region
  repository_id = local.artifact_repo_name
  description   = "Docker repository"
  format        = "DOCKER"

  depends_on = [google_project_service.required]
}

resource "google_cloud_run_v2_service" "app" {
  name     = local.cloud_run_service_name
  location = var.region
  project  = var.project_id

  template {
    service_account = google_service_account.runtime.email

    containers {
      image = var.cloud_run_image
    }

    scaling {
      min_instance_count = var.cloud_run_min_instances
      max_instance_count = var.cloud_run_max_instances
    }
  }

  depends_on = [google_project_service.required]
}

resource "google_cloud_run_v2_service_iam_member" "public_invoker" {
  count    = var.allow_unauthenticated ? 1 : 0
  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.app.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}
