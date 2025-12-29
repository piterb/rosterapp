# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Commands

### Java / Spring Boot application

- Full test suite (used in CI):
  - `./gradlew test`
  - Requires Docker available for Testcontainers (PostgreSQL 16, see `src/test/resources/application.yaml`).
- Single test class:
  - `./gradlew test --tests com.ryr.ros2cal_api.RosterControllerTest`
- Single test method:
  - `./gradlew test --tests "com.ryr.ros2cal_api.RosterControllerTest.convertRosterReturnsSampleStringWhenAuthenticated"`
- Build runnable jar (matches `Dockerfile` and deploy pipeline):
  - `./gradlew bootJar`
  - Outputs `build/libs/app.jar` (see `build.gradle` `bootJar` task).
- Run the app locally via Gradle:
  - `./gradlew bootRun`
- Run the built jar directly:
  - `java -jar build/libs/app.jar`

When running locally against a real Postgres instance, the app expects the usual Spring datasource env vars and auth config, aligned with `COOKBOOK.md` and `README-INFRA.md`:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD` (or a Secret Manager–backed secret in Cloud Run)
- `AUTH_ISSUER_URIS` (comma-separated JWT issuers; wired into `app.security.allowed-issuers`).
- `CORS_ALLOWED_ORIGINS` (comma-separated origins; wired into `app.security.allowed-origins`).
- `MULTIPART_MAX_FILE_SIZE` (max size per uploaded file; wired into `spring.servlet.multipart.max-file-size`).
- `MULTIPART_MAX_REQUEST_SIZE` (max total request size; wired into `spring.servlet.multipart.max-request-size`).
- `OPENAI_API_KEY` (OpenAI key for roster OCR/parse).
- `OPENAI_BASE_URL` (optional, default `https://api.openai.com/v1`).
- `OPENAI_OCR_MODEL` (default `gpt-4.1`).
- `OPENAI_PARSE_MODEL` (default `gpt-5.1`).
- `ROSTER_LOCAL_TZ` (local timezone for ICS descriptions, default `Europe/Berlin`).
- `ROSTER_CALENDAR_NAME` (calendar name for ICS PRODID, default `Roster`).

### Docker image

The Docker build uses a Gradle builder image and produces a distroless Java 17 image (see `Dockerfile`). For a local image that mirrors CI/CD:

- Build image:
  - `docker build -t app:local .`
- Run container (example; adjust envs to your DB and issuer configuration):
  - `docker run --rm -p 8080:8080 \
      -e PORT=8080 \
      -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/db \
      -e SPRING_DATASOURCE_USERNAME=user \
      -e SPRING_DATASOURCE_PASSWORD=pass \
      -e AUTH_ISSUER_URIS="https://issuer.example.com" \
      -e CORS_ALLOWED_ORIGINS="http://localhost:3000" \
      -e MULTIPART_MAX_FILE_SIZE=262144 \
      -e MULTIPART_MAX_REQUEST_SIZE=262144 \
      -e OPENAI_API_KEY="sk-..." \
      -e OPENAI_OCR_MODEL="gpt-4.1" \
      -e OPENAI_PARSE_MODEL="gpt-5.1" \
      -e ROSTER_LOCAL_TZ="Europe/Berlin" \
      -e ROSTER_CALENDAR_NAME="Roster" \
      app:local`

CI builds and pushes an image for Cloud Run using the `Deploy` workflow (`.github/workflows/deploy.yml`); it tags the image as:

- `${GCP_REGION}-docker.pkg.dev/${GCP_PROJECT_ID}/${ARTIFACT_REPO}/app:${GITHUB_SHA}`

### Terraform (infrastructure)

Infrastructure is split into bootstrap (local) and main (GitHub Actions) Terraform (see `README-INFRA.md`, `infra/bootstrap/README.md`, `infra/main/README.md`).

#### Bootstrap (local, human-run)

From `infra/bootstrap`:

- Initialize:
  - `cd infra/bootstrap`
  - `terraform init`
- Apply bootstrap (creates WIF, Terraform admin SA, and remote state bucket):
  - `terraform apply \
      -var "project_id=REPLACE_WITH_PROJECT_ID" \
      -var "region=REPLACE_WITH_REGION" \
      -var "github_repo=ORG/REPO" \
      [-var "tf_state_bucket_name=YOUR_BUCKET_NAME"]`

Outputs map directly to GitHub environment variables (e.g., `GCP_PROJECT_ID`, `GCP_REGION`, `GCP_WIF_PROVIDER`, `GCP_TF_SA_EMAIL`, `TF_STATE_BUCKET`).

#### Main Terraform (optionally local; primarily via GitHub Actions)

Local workflow (mirrors `.github/workflows/infra.yml` and `infra/main/README.md`):

- Configure backend once:
  - `cp infra/main/backend.tf.example infra/main/backend.tf`
  - Edit the bucket name to match `TF_STATE_BUCKET`.
- Plan/apply locally:
  - `cd infra/main`
  - `terraform init -backend-config="bucket=REPLACE_WITH_TF_STATE_BUCKET" -backend-config="prefix=terraform/main"`
  - `terraform plan \
      -var "project_id=REPLACE_WITH_PROJECT_ID" \
      -var "region=REPLACE_WITH_REGION" \
      -var "github_repo=ORG/REPO" \
      [-var "artifact_repo_name=REPLACE_WITH_ARTIFACT_REPO"] \
      [-var "cloud_run_service_name=REPLACE_WITH_SERVICE_NAME"] \
      [-var "cloud_run_image=REPLACE_WITH_IMAGE_URI"]`

In CI, the `Infra` workflow (`.github/workflows/infra.yml`) performs `terraform init` and `terraform plan`, and either `apply` or `destroy` based on the `action` input.

### GitHub Actions expectations

- `CI` workflow (`.github/workflows/ci.yml`): runs on pull requests to `main`, executes `./gradlew test` and Terraform fmt/validate for both `infra/bootstrap` and `infra/main`.
- `Deploy` workflow (`.github/workflows/deploy.yml`): builds and pushes the Docker image, syncs secrets into Secret Manager, and deploys to Cloud Run. It expects GitHub Environment `prod` variables like `GCP_PROJECT_ID`, `GCP_REGION`, `GCP_WIF_PROVIDER`, `GCP_DEPLOY_SA_EMAIL`, `ARTIFACT_REPO`, `CLOUD_RUN_SERVICE`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_URL`, `AUTH_ISSUER_URIS`, and secrets such as `SPRING_DATASOURCE_PASSWORD`.
- `Infra` workflow (`.github/workflows/infra.yml`): runs Terraform against `infra/main` using Workload Identity Federation and the remote state bucket.

## High-level architecture

### Overview

The core service is a Java 17 Spring Boot application (`ros2cal-api`) that exposes health, roster conversion, sample persistence, and authenticated identity endpoints. It is packaged with Gradle, containerized via a multi-stage `Dockerfile`, and deployed to Google Cloud Run. Infrastructure (APIs, Artifact Registry, Cloud Run, Identity Platform, IAM, Secret Manager) is managed with Terraform in `infra/bootstrap` and `infra/main` and orchestrated by GitHub Actions.

### Application layout

- Entry point: `src/main/java/com/ryr/ros2cal_api/Ros2calApiApplication.java` (`@SpringBootApplication`).
- REST controllers:
  - `RosterController` under `/api/roster` with `/convert` sample endpoint.
  - `FlightController` under `/api/flightz` providing a simple health-like response.
  - `api/MeController` under `/api/me`, returning selected JWT claims (subject, email, issuer, audience, expiry, and a `principal_type` marker).
  - `hello/HelloController` under `/api/hello`, serving as a simple JPA-backed example endpoint.
- Configuration and security:
  - `config/SecurityConfig` defines the HTTP security model.
  - `config/SecurityProperties` binds `app.security.allowed-issuers` from configuration.
- Domain and persistence example:
  - `hello/Hello` is a JPA entity with `UUID id`, `String message`, and `Instant createdAt` plus a `@PrePersist` hook.
  - `hello/HelloRepository` is a Spring Data `JpaRepository`.
  - `hello/HelloService` encapsulates transactional writes.

### Security and authentication

Security is implemented as an OAuth2 resource server using JWTs and supports multiple trusted issuers.

- `SecurityConfig` wires an `AuthenticationManagerResolver<HttpServletRequest>` created via `JwtIssuerAuthenticationManagerResolver.fromTrustedIssuers(...)`.
- Trusted issuer list comes from `SecurityProperties.getAllowedIssuers()`, backed by the `app.security.allowed-issuers` list property.
- If no issuers are configured, the code falls back to `https://accounts.google.com` to keep the app startable by default.
- Runtime configuration (see `src/main/resources/application.yaml`):
  - `app.security.allowed-issuers` is populated from the env var `AUTH_ISSUER_URIS` (comma-separated string).
- Authorization model:
  - `/actuator/health` and `/actuator/info` are permitted to all.
  - `/api/**` requires authentication (Bearer JWT).
  - All other routes are denied by default.
- CSRF is disabled for `/api/**` only; sessions are stateless.
- `MeController` assumes an authenticated `Jwt` principal and returns a subset of claims for introspection/testing.

Tests exercise security and controller behavior using `spring-security-test`:

- `SecurityEndpointsTest` verifies that `/actuator/health` is public and `/api/me` returns expected claims when called with a mocked JWT (issuer configured in `src/test/resources/application.yaml`).
- `RosterControllerTest` verifies `/api/roster/convert` requires authentication and returns the expected response when a JWT is present.

### Persistence, database, and migrations

The app uses Postgres with Flyway for schema management and Testcontainers for integration testing.

- Flyway is enabled (`spring.flyway.enabled=true` in both main and test `application.yaml`).
- Initial schema is defined in `src/main/resources/db/migration/V1__create_hello_table.sql`, aligned with the `hello.Hello` entity.
- In tests, the datasource is configured via `jdbc:tc:postgresql:16-alpine:///testdb` with driver `org.testcontainers.jdbc.ContainerDatabaseDriver`; this spins up an ephemeral Postgres container automatically.
- JPA is configured with `ddl-auto: validate` in both main and test configs, so Flyway migrations must match entity mappings.

At runtime (Cloud Run or local Postgres), database connection details are provided via env vars and GitHub environment/secrets as described in `README-INFRA.md` and `.github/workflows/deploy.yml` (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, etc.).

### Infrastructure and deployment

Infrastructure is designed around GCP and GitHub OIDC Workload Identity Federation.

- `infra/bootstrap`:
  - Creates the Workload Identity Pool and provider for GitHub (`github_repo`-scoped principal set).
  - Creates the Terraform admin service account and the GCS bucket used for Terraform remote state.
  - Is intended to be run only by a privileged human from a local machine (see `README-INFRA.md` and `infra/bootstrap/README.md`).
- `infra/main`:
  - Provisions application infrastructure: enables required APIs, creates Artifact Registry, configures Cloud Run service, Secret Manager, and Identity Platform (if using Google identity).
  - Uses remote state in the bootstrap-created GCS bucket (`backend.tf` / `backend.tf.example`).

Deployment flow (see `COOKBOOK.md`, `README-INFRA.md`, `.github/workflows/deploy.yml`, `.github/workflows/infra.yml`):

1. Run bootstrap Terraform locally to create WIF, Terraform admin SA, and state bucket.
2. Configure GitHub environment variables from bootstrap outputs.
3. Run the `Infra` workflow to apply main Terraform and create app infrastructure.
4. Push to `main` or trigger the `Deploy` workflow to build the Docker image, push it to Artifact Registry, sync secrets to Secret Manager, and deploy the image to Cloud Run with appropriate env vars and secret mounts.

Cloud Run expectations:

- Image is the distroless Java 17 image built from this repo (`Dockerfile`).
- Container listens on `PORT` (default 8080) configured via `server.port: ${PORT:8080}`.
- Secrets are materialized in Secret Manager with names derived from `CLOUD_RUN_SERVICE` and env variable keys (see `deploy.yml` secret sync step).

This structure means most application-layer changes happen under `src/main/java/com/ryr/ros2cal_api/**` and `src/main/resources/**`, while infra changes are confined to `infra/**` and mirrored by the three GitHub workflows under `.github/workflows/`.
