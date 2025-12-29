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
- `SPRING_DATASOURCE_PASSWORD_SECRET_NAME` (GitHub Variable; Secret Manager name, default `spring-datasource-password`)
- `SPRING_DATASOURCE_URL` (plain env value)
- `SPRING_PROFILES_ACTIVE` (optional)
- `AUTH_ISSUER_URIS` (comma-separated issuers, e.g., `https://securetoken.google.com/<project_id>,https://accounts.google.com`)
- `CORS_ALLOWED_ORIGINS` (comma-separated origins, e.g., `http://localhost:3000,https://app.example.com`)
- `MULTIPART_MAX_FILE_SIZE` (max size per uploaded file, e.g., `5242880` for 5 MB)
- `MULTIPART_MAX_REQUEST_SIZE` (max total request size, e.g., `5242880` for 5 MB)
- `OPENAI_API_KEY` (OpenAI key for roster OCR/parse)
- `OPENAI_BASE_URL` (optional, default `https://api.openai.com/v1`)
- `OPENAI_OCR_MODEL` (default `gpt-4.1`)
- `OPENAI_PARSE_MODEL` (default `gpt-5.1`)
- `ROSTER_LOCAL_TZ` (local timezone for ICS descriptions, default `Europe/Berlin`)
- `ROSTER_CALENDAR_NAME` (calendar name for ICS PRODID, default `Roster`)
- `IDENTITY_GOOGLE_CLIENT_ID` (OAuth client ID for Google IdP)

Required **Secrets**:
- `SPRING_DATASOURCE_PASSWORD` (DB password; synced into Secret Manager at deploy)
- `IDENTITY_GOOGLE_CLIENT_SECRET` (OAuth client secret for Google IdP)

## Branch protection guidance (recommended)
- Require PR reviews for `main` (at least 1 reviewer).
- Require status checks: `CI` (and `Infra` plan if you add it).
- Restrict who can push to `main`.
- Require signed commits (optional).

## Notes
- `infra/bootstrap` should not be run in CI.
- `infra/main` uses remote state in GCS.
- The deploy workflow uses Secret Manager secret names, not values.

## Authentication (choose one)

### Option A: Google Identity Platform
- Terraform (main) enables Identity Platform, turns on Email/Password sign-in, and wires Google IdP (client ID/secret from GitHub vars/secrets).
- Terraform outputs `IDENTITY_PLATFORM_ISSUER` (e.g., `https://securetoken.google.com/<project_id>`) and `IDENTITY_PLATFORM_API_KEY` (sensitive) for Identity Toolkit REST flows.
- App expects `AUTH_ISSUER_URIS` (set in GitHub vars) and will read comma-separated issuers. Recommended: `https://securetoken.google.com/<project_id>,https://accounts.google.com`.

### Google OAuth client (for Google IdP + Postman)
1) GCP: APIs & Services → Credentials → **Create OAuth client** → type **Web application**.
2) Fill in:
   - Name: e.g., `rosterapp-backend-google-idp` (so it’s clear it serves the backend)
   - Authorized redirect URIs (for testing):
     - `https://oauth.pstmn.io/v1/callback` (Postman)
     - `http://localhost:8080/login/oauth2/code/google` (if you ever test local Spring OAuth flow)

## Manual OpenAI regression test
There is a manual integration test that calls the real OpenAI API and compares the OCR/parse output
against a golden JSON file in `src/test/resources/fixtures/roster-openai/roster_expected.json`. It is
disabled by default to avoid costs and CI flakiness.

Run it manually:

```bash
./scripts/run-test-openai-int.sh
```

Notes:
- Uses `src/test/resources/fixtures/roster-openai/roster_input.jpg` as input.
- Expected JSON: `src/test/resources/fixtures/roster-openai/roster_expected.json`.
- Expected ICS: `src/test/resources/fixtures/roster-openai/roster_expected.ics`.
- Fixed model versions: `gpt-4.1-2025-04-14` (OCR) and `gpt-5.1-2025-11-13` (parse).
- To bypass OpenAI prompt cache for a fresh run, set `OPENAI_ENABLE_CACHE=false`.
- The script reads `OPENAI_API_KEY` from `.env` if present.
     - `http://localhost:3000` or another local front/BFF callback if you have one
   - Authorized JavaScript origins: leave empty unless you have an SPA on a specific domain.
3) Create → copy **Client ID** into `IDENTITY_GOOGLE_CLIENT_ID` (GitHub variable) and **Client Secret** into `IDENTITY_GOOGLE_CLIENT_SECRET` (GitHub Secret).
4) When you have a frontend/BFF on a GCP domain, add its redirect URI (and JS origin if SPA) to this same client.

### OAuth consent screen (required for the Google client)
1) GCP: APIs & Services → OAuth consent screen.
2) User type: External (typical) → Create.
3) Fill in:
   - App name (e.g., `RosterApp Auth`)
   - User support email
   - Developer contact email
   - (Optional) App domain/homepage/privacy/terms – can stay empty for Testing.
4) Authorized domains: add the domains you will use (e.g., `localhost`, `oauth.pstmn.io`, `<project_id>.cloud.goog`, `run.app`, or your custom).
5) Save & Continue; scopes can stay default (openid/profile/email).
6) Test users: add accounts if you keep the app in **Testing**; for **Production** publish it and expect Google review if you add custom domains.

### Postman (Authorization Code + PKCE)
- Grant Type: Authorization Code (With PKCE), Code Challenge Method: S256.
- Auth URL: `https://accounts.google.com/o/oauth2/v2/auth`
- Token URL: `https://oauth2.googleapis.com/token`
- Client ID/Secret: from Identity Platform Google IdP (above).
- Redirect URI: `https://oauth.pstmn.io/v1/callback`
- Scopes: `openid profile email`
- After obtaining tokens, use the **ID token** as `Authorization: Bearer <id_token>` to call `/api/me`.
- For email/password users, create them via Identity Platform API using the `IDENTITY_PLATFORM_API_KEY`, then use the returned ID token.

### Example calls
- Health (public): `curl -i https://<cloud-run-host>/actuator/health`
- WhoAmI: `curl -i -H "Authorization: Bearer <ID_TOKEN>" https://<cloud-run-host>/api/me`

### Troubleshooting
- 401 with issuer mismatch: ensure `AUTH_ISSUER_URIS` includes the issuer of your token.
- Audience mismatch: ID token must target your client/project; use the same client ID configured in Identity Platform.
- Opaque access token: use the ID token (JWT) from the Authorization Code + PKCE flow.
- Terraform apply 403 on Identity Platform/API Keys: ensure the Terraform admin SA has `roles/identitytoolkit.admin`, `roles/identityplatform.admin`, `roles/firebase.admin`, and `roles/serviceusage.apiKeysAdmin` (granted in main Terraform). If creation still fails, re-run apply once bindings/APIs propagate.

### Test strategy
- CI uses `spring-security-test` with mock JWTs; no real Google tokens are required for tests.
- Tests run against a transient PostgreSQL container via Testcontainers (Docker required in CI/local).
- `/actuator/health` stays public; `/api/**` requires a Bearer JWT.

### Option B: Auth0
- App expects `AUTH_ISSUER_URIS` and will read comma-separated issuers. For Auth0 use your tenant (or custom) issuer, e.g. `https://<tenant>.auth0.com/`.
- For API calls in production, prefer **access tokens** with your API audience. For quick Postman tests, the ID token also works for `/api/me` (not recommended for real API authorization).

#### Create Application
1) Auth0 Dashboard -> Applications -> Create Application.
2) Type: **Regular Web Application**.
3) Set **Allowed Callback URLs**:
   - `https://oauth.pstmn.io/v1/callback`
4) Connections tab: enable **Database (Username/Password)** and **Google** if needed.

#### Create API
1) Auth0 Dashboard -> APIs -> Create API.
2) Name: `Rosterapp API`.
3) Identifier: `<API Identifier>`.
4) (Optional) Define scopes like `read:hello`.

#### Postman (Authorization Code + PKCE)
- Grant Type: Authorization Code (With PKCE), Code Challenge Method: S256.
- Token type: **ID Token** (for quick `/api/me` checks).
- Auth URL: `https://<YOUR_AUTH0_DOMAIN>/authorize`
- Access Token URL: `https://<YOUR_AUTH0_DOMAIN>/oauth/token`
- Client ID/Secret: from the Auth0 application.
- Callback URL: `https://oauth.pstmn.io/v1/callback`
- Scope: `openid profile email` (+ your API scope like `read:hello` if defined).
- If you want an access token for your API, pass `audience=<API Identifier>` in the request; then call `/api/**` with `Authorization: Bearer <access_token>`.
