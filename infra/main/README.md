# Main Terraform (GitHub Actions)

Purpose: provision application infrastructure (APIs, Artifact Registry, Cloud Run, runtime IAM).

## Configure remote state
Copy the example backend and update the bucket:
```bash
cp backend.tf.example backend.tf
# edit bucket name to match bootstrap output
```

## Run locally (optional)
```bash
cd infra/main
terraform init -backend-config="bucket=REPLACE_WITH_TF_STATE_BUCKET" -backend-config="prefix=terraform/main"
terraform plan \
  -var "project_id=REPLACE_WITH_PROJECT_ID" \
  -var "region=REPLACE_WITH_REGION" \
  -var "github_repo=ORG/REPO" \
  -var "artifact_repo_name=REPLACE_WITH_ARTIFACT_REPO" \
  -var "cloud_run_service_name=REPLACE_WITH_SERVICE_NAME" \
  -var "cloud_run_image=REPLACE_WITH_IMAGE_URI"

If you omit `artifact_repo_name` or `cloud_run_service_name`, they default to:
- `<project_id>-artifact`
- `<project_id>-service`

If you omit `cloud_run_image`, the service is created with `gcr.io/cloudrun/hello` and the deploy workflow will replace it.

Public access
- `allow_unauthenticated` defaults to `true`, so Cloud Run gets `allUsers` with `roles/run.invoker`.
```

## GitHub Actions usage
The `infra.yml` workflow runs `terraform plan` and `apply|destroy` with OIDC auth. Set these GitHub Environment (prod) variables:
- `GCP_PROJECT_ID`
- `GCP_REGION`
- `GCP_WIF_PROVIDER`
- `GCP_TF_SA_EMAIL`
- `TF_STATE_BUCKET`

## Notes
- Cloud Run is created with a placeholder image; deploy workflow updates it.
- A dedicated deploy service account can be added later for tighter separation.
