# Infrastructure Runbook (Terraform + GitHub OIDC)

This repo uses a two-phase Terraform model on GCP:

- **Bootstrap (local, human-run)**: create the trust root (WIF/OIDC), Terraform admin service account, and the GCS state bucket.
- **Main (GitHub Actions)**: provision app infra (APIs, Artifact Registry, Cloud Run, Secret Manager IAM).

No long-lived GCP keys are required; GitHub Actions uses OIDC.

## Why bootstrap exists
Bootstrap establishes the minimum, trusted foundation so GitHub Actions can safely assume a Terraform admin role without JSON keys.

## Repo structure
- `infra/bootstrap/`: local-only Terraform for trust root
- `infra/main/`: GitHub Actions Terraform for app infra
- `.github/workflows/infra.yml`: manual apply/destroy
- `.github/workflows/deploy.yml`: deploy app on push

## Manual steps checklist
1) Create or choose a GCP project.
2) Create a bootstrap service account in the project (used to run the bootstrap locally):
   - GCP Console -> IAM & Admin -> Service Accounts -> Create Service Account
   - Name: `bootstrap-operator`
   - Description: `Bootstrap operator for Terraform`
   - Click **Create and Continue**
   - Assign roles (on this project only):
     - Project IAM Admin
     - Service Account Admin
     - Workload Identity Pool Admin
     - Service Usage Admin
     - Storage Admin
   - Click **Done**
   - Allow your user to impersonate this service account:
     - IAM & Admin -> Service Accounts -> `bootstrap-operator` -> Permissions -> Grant access
     - Principal: your Google user (email)
     - Role: Service Account Token Creator
     - Save
   - Enable required APIs (if not already enabled):
     - IAM Service Account Credentials API
     - IAM API
     - Cloud Resource Manager API
     - Service Usage API
     - Security Token Service API
3) Local auth on your machine (impersonate the bootstrap service account):
   - `gcloud auth login`
   - `gcloud auth application-default login --impersonate-service-account bootstrap-operator@PROJECT_ID.iam.gserviceaccount.com`
4) Run bootstrap (local):
   ```bash
   cd infra/bootstrap
   terraform init
   terraform apply \
     -var "project_id=REPLACE_WITH_PROJECT_ID" \
     -var "region=REPLACE_WITH_REGION" \
     -var "github_repo=ORG/REPO"
   ```
   - Optional: override the state bucket name with `-var "tf_state_bucket_name=YOUR_BUCKET_NAME"` (default is `<project_id>-tf-state`).
5) Copy bootstrap outputs into GitHub Environment `prod` (as **Variables**). Output names match the variable names:
   - `GCP_PROJECT_ID`
   - `GCP_REGION`
   - `GCP_WIF_PROVIDER`
   - `GCP_TF_SA_EMAIL`
   - `TF_STATE_BUCKET`
6) Configure `infra/main/backend.tf` from `backend.tf.example`.
7) Run GitHub Actions → **Infra** workflow → `apply` (manual + approval).
8) Push to `main` to deploy the application.

## Decommission
1) GitHub Actions → **Infra** workflow → `destroy`.
2) Local bootstrap destroy:
   ```bash
   cd infra/bootstrap
   terraform destroy -var "project_id=REPLACE_WITH_PROJECT_ID" -var "region=REPLACE_WITH_REGION" -var "github_repo=ORG/REPO" -var "tf_state_bucket_name=REPLACE_WITH_TF_STATE_BUCKET"
   ```

## GitHub Environment configuration (prod)
Required **Variables**:
- `GCP_PROJECT_ID`
- `GCP_REGION`
- `GCP_WIF_PROVIDER`
- `GCP_TF_SA_EMAIL`
- `TF_STATE_BUCKET`
- `ARTIFACT_REPO`
- `CLOUD_RUN_SERVICE`
- `GCP_DEPLOY_SA_EMAIL`
- `SPRING_DATASOURCE_USERNAME` (plain env value)
- `SPRING_DATASOURCE_PASSWORD` (GitHub Secret; value = Secret Manager secret name, required for deploy)
- `SPRING_DATASOURCE_URL` (plain env value)
- `SPRING_PROFILES_ACTIVE` (optional)

Required **Secrets**:
- None for OIDC; all sensitive values should be in Secret Manager.

## Branch protection guidance (recommended)
- Require PR reviews for `main` (at least 1 reviewer).
- Require status checks: `CI` (and `Infra` plan if you add it).
- Restrict who can push to `main`.
- Require signed commits (optional).

## Notes
- `infra/bootstrap` should not be run in CI.
- `infra/main` uses remote state in GCS.
- The deploy workflow uses Secret Manager secret names, not values.
