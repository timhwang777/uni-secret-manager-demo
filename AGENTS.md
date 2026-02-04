## Project Summary
Production-level Spring Boot 3.2 demo app that exercises the `uni-secret-manager-spring-boot-starter` across local, GCP Secret Manager, and AWS (LocalStack). It includes REST endpoints that surface secret-resolution status without exposing secret values.

Key goals:
- Validate `@SecretValue` injection (plain strings, JSON fields, nested dot paths, defaults).
- Exercise provider ordering and fallback (GCP -> AWS -> Local).
- Demonstrate cache refresh via REST endpoints and tests.

## Tech Stack
- Java 21
- Spring Boot 3.2.1
- Maven
- Testcontainers (LocalStack)

## Entry Points
- App main: `src/main/java/io/github/timhwang777/unisecretdemo/UniSecretManagerDemoApplication.java`
- REST controllers:
  - `src/main/java/io/github/timhwang777/unisecretdemo/controller/DemoController.java`
  - `src/main/java/io/github/timhwang777/unisecretdemo/controller/SecretManagementController.java`

## Configuration Files
- Base: `src/main/resources/application.yml`
- Profiles:
  - `src/main/resources/application-dev.yml` (local only)
  - `src/main/resources/application-staging.yml` (GCP + LocalStack + local)
  - `src/main/resources/application-prod.yml` (GCP + AWS, strict)
- Tests:
  - `src/test/resources/application-test.yml` (local-only tests)
  - `src/test/resources/application-test-cloud.yml` (cloud tests)

## Environment Variables
### Required for cloud tests / staging profile
```bash
export GCP_PROJECT_ID=atsquareone
```

### Optional (GCP auth)
Use Application Default Credentials:
```bash
gcloud auth application-default login
```

## LocalStack (AWS emulator)
- `docker-compose.yml` runs LocalStack with Secrets Manager.
- `scripts/localstack-init.sh` seeds test secrets on container startup.
- Tests use Testcontainers to launch LocalStack automatically; you do not need `docker compose up` for tests.

## Makefile Commands (Most Common)
```bash
# Build
make build

# Run app
make run-dev
make run-staging   # requires GCP_PROJECT_ID + Docker

# Tests
make test-offline  # local-only, no Docker/GCP
make test-local    # LocalStack via Testcontainers
make test-gcp      # real GCP (no Docker)
make test-cloud    # GCP + LocalStack
make test-all
```

## Maven Test Profiles
Tests are tagged with JUnit 5 `@Tag`.
- `local-tests` (default): runs `@Tag("local")`
- `cloud-tests`: runs `@Tag("cloud")`
- `all-tests`: runs both

Examples:
```bash
mvn test -Plocal-tests
mvn test -Pcloud-tests
mvn test -Pall-tests
```

## Scripts
- `scripts/setup-gcp-secrets.sh`: create GCP test secrets
- `scripts/cleanup-gcp-secrets.sh`: delete GCP test secrets
- `scripts/localstack-init.sh`: seed LocalStack secrets

## Test Guide
Before you test any cloud-related test suites, ensure this env var is set:
```bash
export GCP_PROJECT_ID=atsquareone
```

For detailed test tiers and dependencies, see `docs/TESTING_GUIDE.md`.
