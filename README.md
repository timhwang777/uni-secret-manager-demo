# uni-secret-manager-demo

Production-level Spring Boot 3.2 demo app for validating `uni-secret-manager-spring-boot-starter` across:

- Local provider
- GCP Secret Manager
- AWS Secrets Manager (via LocalStack in tests/dev)
- HashiCorp Vault (via Docker dev mode in tests/dev)

The app exposes REST endpoints that show secret-resolution status without exposing raw secret values.

## What This Demo Covers

- `@SecretValue` injection for plain strings and JSON fields
- Nested JSON extraction via dot-notation paths
- Default values when secrets are missing
- Provider ordering and fallback (`gcp -> aws -> local`, `vault -> aws -> local`)
- Cache invalidation with `SecretRefreshService` and REST endpoints
- Real GCP integration tests plus Docker-based AWS/Vault tests with Testcontainers

## Tech Stack

- Java 21
- Spring Boot 3.2.1
- Maven
- JUnit 5 + Testcontainers (LocalStack + Vault)

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
│   ├── application-prod.yml
│   ├── application-vault.yml
│   └── application-staging-vault.yml
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
- Docker Desktop (for LocalStack/Vault/Testcontainers flows)
- Optional for cloud tests/staging/prod:
  - `gcloud` CLI
  - GCP Application Default Credentials
  - `GCP_PROJECT_ID` environment variable

## Setup

### 1) Build the demo

The starter dependency (`uni-secret-manager-spring-boot-starter`) is resolved automatically from [JitPack](https://jitpack.io) — no extra setup needed:

```bash
make build
```

### Local development (optional)

If you're actively developing the starter alongside this demo, you can install it to your local Maven cache instead:

```bash
make install-starter
```

This builds the starter from `../uni-secret-manager-spring` and installs it to `~/.m2`. The local artifact takes priority over JitPack.

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

### Staging-Vault profile (Vault + LocalStack AWS + local fallback)

Requires Docker only (no GCP credentials). `make run-staging-vault` starts both Vault and LocalStack, seeds demo secrets, then boots Spring.

```bash
make run-staging-vault
```

### Vault profile (Vault dev container + local fallback)

Requires Docker. `make run-vault` will start Vault dev mode, seed demo secrets, then boot Spring with `vault` profile.

```bash
make run-vault
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
| `vault` | `vault, local` | `false` | Vault points to local dev container (`http://localhost:8200`) |
| `staging-vault` | `vault, aws, local` | `true` | Vault + LocalStack AWS + local fallback (Docker only) |
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

### Secrets seeded for Vault dev (`scripts/vault-seed.sh`)

- `db-credentials` (JSON object)
- `api-key` (JSON object with `value` field)
- `shared-secret` (JSON object with `value` field)
- `rotating-secret` (multiple KV v2 versions)
- `nested-config` (nested JSON object)

### Local fallback secrets in profile YAML

- `application-dev.yml` and `application-test.yml` include local secrets for all demo services
- `application-staging.yml` includes local fallback entries such as `rotating-secret`, `nested-config`, and `optional-feature`
- `application-prod.yml` disables local provider

## Secret Injection Examples in Code

- `DatabaseService`: JSON fields from `db-credentials`
- `ExternalApiService`: profile-driven API key resolution using the active provider order
- `FeatureFlagService`: default value (`defaultValue="disabled"`)
- `MultiCloudService`: custom chain (`providers={"gcp","aws"}`)
- `VersionedSecretService`: versioned lookup (`version="latest"`)
- `NestedConfigService`: nested dot-path extraction (for example `database.connection.password`)
- `VaultMultiProviderService`: Vault provider override (`provider="vault"`) and Vault→AWS chain (`providers={"vault","aws"}`)

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
    "vault": false,
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

- `@Tag("local")`: local/offline + Docker-backed provider coverage (AWS LocalStack + Vault)
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
- `VaultProviderDemoTest` (Docker/Testcontainers required)
- `VaultAwsFallbackDemoTest` (Docker/Testcontainers required — Vault + LocalStack)

Cloud tag (`@Tag("cloud")`):

- `GcpProviderDemoTest`
- `GcpJsonFieldDemoTest`
- `GcpAwsFallbackDemoTest` (Docker + GCP)
- `FullStackDemoTest` (Docker + GCP)

## LocalStack, Vault, and GCP Utilities

### LocalStack + Vault (manual)

```bash
make docker-up
make docker-down
```

`docker-compose.yml` runs LocalStack and Vault dev mode. Vault demo secrets can be seeded with:

```bash
./scripts/vault-seed.sh
```

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
  - The starter is resolved from JitPack automatically. If JitPack is unavailable, install locally with `make install-starter`
