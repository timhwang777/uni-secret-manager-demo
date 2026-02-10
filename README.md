# uni-secret-manager-demo

Production-level Spring Boot 3.2 demo app for validating `uni-secret-manager-spring-boot-starter` across:

- Local provider
- GCP Secret Manager
- AWS Secrets Manager (via LocalStack in tests/dev)

The app exposes REST endpoints that show secret-resolution status without exposing raw secret values.

## What This Demo Covers

- `@SecretValue` injection for plain strings and JSON fields
- Nested JSON extraction via dot-notation paths
- Default values when secrets are missing
- Provider ordering and fallback (`gcp -> aws -> local`)
- Cache invalidation with `SecretRefreshService` and REST endpoints
- Real GCP integration tests plus AWS LocalStack tests with Testcontainers

## Tech Stack

- Java 21
- Spring Boot 3.2.1
- Maven
- JUnit 5 + Testcontainers (LocalStack)

## Project Layout

```text
.
├── src/main/java/io/github/timhwang777/unisecretdemo
│   ├── UniSecretManagerDemoApplication.java
│   ├── controller/
│   │   ├── DemoController.java
│   │   └── SecretManagementController.java
│   └── service/
├── src/main/resources
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-staging.yml
│   └── application-prod.yml
├── src/test/java/io/github/timhwang777/unisecretdemo
│   ├── local/
│   └── cloud/
├── src/test/resources
│   ├── application-test.yml
│   └── application-test-cloud.yml
├── scripts/
├── docs/
├── docker-compose.yml
└── Makefile
```

## Prerequisites

- Java 21
- Maven 3.8+
- Docker Desktop (for LocalStack/Testcontainers flows)
- Optional for cloud tests/staging/prod:
  - `gcloud` CLI
  - GCP Application Default Credentials
  - `GCP_PROJECT_ID` environment variable

## Setup

### 1) Install the starter artifact to local Maven cache

This project depends on `io.github.timhwang777:uni-secret-manager-spring-boot-starter:1.0.0-SNAPSHOT`.

Use the provided Make target:

```bash
make install-starter
```

If your local starter repo is at `../uni-secret-manager-spring`, this equivalent command works directly:

```bash
mvn -f ../uni-secret-manager-spring/pom.xml install -DskipTests -Djacoco.skip=true
```

If your starter repo path is different, run the same Maven install command against that repo's `pom.xml`.

### 2) Build the demo

```bash
make build
```

## Running the App

### Dev profile (local-only secrets)

No Docker or cloud credentials required.

```bash
make run-dev
```

### Staging profile (GCP + LocalStack AWS + local fallback)

Requires:
- `GCP_PROJECT_ID`
- GCP ADC (`gcloud auth application-default login`)
- Docker running

`make run-staging` will start LocalStack and wait for health before booting Spring.

```bash
export GCP_PROJECT_ID=atsquareone
make run-staging
```

### Prod profile (GCP + AWS, strict mode)

Local fallback is disabled and `fail-on-missing=true`.

```bash
export GCP_PROJECT_ID=your-project-id
export AWS_REGION=us-east-1
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

## Runtime Configuration Profiles

| Profile | Provider order | fail-on-missing | Notes |
|---|---|---|---|
| `dev` | `local` | `false` | All secrets come from `application-dev.yml` |
| `staging` | `gcp, aws, local` | `true` | AWS endpoint points to LocalStack (`http://localhost:4566`) |
| `prod` | `gcp, aws` | `true` | Local provider disabled |

Base config in `application.yml` enables secret caching and retry settings shared by all profiles.

## Provider and Secret Inventory

### Secrets seeded for LocalStack (`scripts/localstack-init.sh`)

- `db-credentials` (JSON)
- `api-key` (plain string)
- `shared-secret` (plain string)
- `rotating-secret` (multiple versions)
- `nested-config` (nested JSON)

### Secrets seeded for GCP (`scripts/setup-gcp-secrets.sh`)

- `db-credentials` (JSON)
- `gcp-api-key` (plain string)
- `shared-secret` (plain string)
- `nested-config` (nested JSON)

### Local fallback secrets in profile YAML

- `application-dev.yml` and `application-test.yml` include local secrets for all demo services
- `application-staging.yml` includes local fallback entries such as `rotating-secret`, `nested-config`, and `optional-feature`
- `application-prod.yml` disables local provider

## Secret Injection Examples in Code

- `DatabaseService`: JSON fields from `db-credentials`
- `ExternalApiService`: provider override (`provider="gcp"`)
- `FeatureFlagService`: default value (`defaultValue="disabled"`)
- `MultiCloudService`: custom chain (`providers={"gcp","aws"}`)
- `VersionedSecretService`: versioned lookup (`version="latest"`)
- `NestedConfigService`: nested dot-path extraction (for example `database.connection.password`)

## API Endpoints

### Demo endpoints

- `GET /api/demo/db-status`
- `GET /api/demo/api-status`
- `GET /api/demo/all`

### Secret management endpoints

- `GET /api/secrets/status`
- `GET /api/secrets/health`
- `POST /api/secrets/refresh`
- `POST /api/secrets/refresh/{key}`

### Quick smoke test

```bash
curl -s http://localhost:8080/api/secrets/health | jq
curl -s http://localhost:8080/api/secrets/status | jq
curl -s http://localhost:8080/api/demo/all | jq
curl -s -X POST http://localhost:8080/api/secrets/refresh | jq
```

`DatabaseService` masks sensitive values in responses (for example password preview like `de***`) instead of returning raw secrets.

### Example response shapes

`GET /api/secrets/health`

```json
{
  "status": "UP",
  "message": "Secret manager is operational"
}
```

`GET /api/secrets/status`

```json
{
  "enabled": true,
  "providerOrder": ["gcp", "aws", "local"],
  "failOnMissing": true,
  "providers": {
    "aws": true,
    "gcp": true,
    "local": true
  },
  "cache": {
    "enabled": true,
    "ttl": "PT3M",
    "maxSize": 1000
  }
}
```

## Testing

Tests are split by JUnit tags:

- `@Tag("local")`: local/offline + LocalStack-only coverage
- `@Tag("cloud")`: real GCP coverage (some also use LocalStack)

Cloud tests are guarded by `@EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")`.

### Recommended Make targets

| Level | Command | Needs Docker | Needs GCP |
|---|---|---|---|
| Offline local tests (no Docker) | `make test-offline` | No | No |
| Local tests (+ LocalStack via Testcontainers) | `make test-local` | Yes | No |
| Cloud tests (GCP only) | `make test-gcp` | No | Yes |
| Cloud + LocalStack fallback tests | `make test-cloud` | Yes | Yes |
| Full suite | `make test-all` | Yes | Yes |

### Maven profiles

```bash
mvn test -Plocal-tests   # default
mvn test -Pcloud-tests
mvn test -Pall-tests
```

### Test inventory

Local tag (`@Tag("local")`):

- `LocalProviderDemoTest`
- `FallbackChainDemoTest`
- `JsonFieldExtractionDemoTest`
- `DefaultValueDemoTest`
- `FailOnMissingDemoTest`
- `CacheRefreshDemoTest`
- `AwsProviderDemoTest` (Docker/Testcontainers required)

Cloud tag (`@Tag("cloud")`):

- `GcpProviderDemoTest`
- `GcpJsonFieldDemoTest`
- `GcpAwsFallbackDemoTest` (Docker + GCP)
- `FullStackDemoTest` (Docker + GCP)

## LocalStack and GCP Utilities

### LocalStack (manual)

```bash
make docker-up
make docker-down
```

`docker-compose.yml` runs LocalStack with Secrets Manager and auto-seeds secrets using `scripts/localstack-init.sh`.

### GCP secret lifecycle scripts

```bash
export GCP_PROJECT_ID=atsquareone
make gcp-setup
make gcp-cleanup
```

Equivalent direct script usage:

```bash
./scripts/setup-gcp-secrets.sh
./scripts/cleanup-gcp-secrets.sh
```

## Required Environment Variables

### For cloud tests / staging / prod

```bash
export GCP_PROJECT_ID=atsquareone
```

### Optional but typically required for local GCP auth

```bash
gcloud auth application-default login
```

## Documentation

- `docs/TESTING_GUIDE.md` - full test tiers and per-test behavior
- `docs/GCP_SETUP_GUIDE.md` - end-to-end GCP setup
- `docs/fresh-setup-guide.md` - clean-machine setup guidance
- `docs/migration-from-local-to-remote-packages.md` - local to package-consumption migration

## Troubleshooting

- `Error: GCP_PROJECT_ID is not set`
  - `export GCP_PROJECT_ID=your-project-id`
- Docker-related LocalStack/Testcontainers failures
  - Start Docker Desktop and re-run
- GCP auth failures (`UNAUTHENTICATED`, permission denied)
  - Run `gcloud auth application-default login`
  - Verify IAM roles for Secret Manager access
- Missing starter dependency during build
  - Ensure the starter artifact is installed locally (`make install-starter`)
