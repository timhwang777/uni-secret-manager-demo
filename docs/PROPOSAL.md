# Proposal: uni-secret-manager-demo Test Project

## Goal

Build a production-level demo Spring Boot application that exercises every major feature of the `uni-secret-manager-spring-boot-starter` plugin, using **real GCP Secret Manager** as the primary provider and **LocalStack (AWS emulator)** as the secondary provider.

- **Location**: `/Users/tim/Works/uni-secret-manager-demo`
- **Starter under test**: `io.github.timhwang777:uni-secret-manager-spring-boot-starter:1.0.0-SNAPSHOT`

---

## 1. Project Skeleton

| Item | Detail |
|------|--------|
| Framework | Spring Boot 3.2.1 |
| Java version | 21 (LTS) |
| Build tool | Maven |
| Starter dependency | `uni-secret-manager-spring-boot-starter:1.0.0-SNAPSHOT` (local `.m2`) |
| Layout | Standard `src/main/java`, `src/main/resources`, `src/test/java` |

---

## 2. Multi-Profile Configuration

| Profile | GCP | AWS | Local | Use case |
|---------|-----|-----|-------|----------|
| `dev` | off | off | on | Local development, no cloud credentials needed |
| `staging` | real GCP | LocalStack | fallback | Integration testing with real GCP + emulated AWS |
| `prod` | real GCP | real AWS | off | Production-like, `fail-on-missing: true`, longer cache TTL |

Configuration files:

- **`application.yml`** — Base config (cache, retry settings).
- **`application-dev.yml`** — Local provider only with hardcoded dev secrets.
- **`application-staging.yml`** — GCP primary + AWS (LocalStack at `localhost:4566`) secondary + local fallback.
- **`application-prod.yml`** — GCP primary, AWS secondary, strict mode (`fail-on-missing: true`).

---

## 3. AWS Testing: LocalStack

### What is LocalStack

LocalStack is an open-source Docker container that emulates AWS services locally. It exposes the AWS Secrets Manager API at `http://localhost:4566`. The starter's own test suite already uses LocalStack via Testcontainers.

### How it is used

- **`docker-compose.yml`** includes a LocalStack service for manual local development (`docker compose up`).
- **Testcontainers** spins up LocalStack programmatically during automated integration tests (no manual Docker required).
- **`localstack-init.sh`** seeds secrets into LocalStack on container startup.

### Secrets seeded into LocalStack

| Secret name | Value | Feature exercised |
|-------------|-------|-------------------|
| `db-credentials` | `{"username":"admin","password":"s3cret","host":"db.example.com"}` | JSON field extraction |
| `api-key` | `test-api-key-12345` | Plain string secret |
| `rotating-secret` | Two versions (AWSCURRENT + AWSPREVIOUS) | Version support |
| `shared-secret` | `shared-value-from-aws` | Multi-provider fallback chain |

---

## 4. GCP Testing: Real GCP Secret Manager

### Why real GCP instead of an emulator

There is no official GCP Secret Manager emulator. The `gcloud beta emulators` command supports Pub/Sub, Bigtable, Datastore, Firestore, and Spanner — but **not** Secret Manager. Community fakes exist but are unmaintained and fragile.

Since GCP is the primary provider, testing against the real API gives true confidence in authentication, IAM, and SDK behavior. Cost is essentially free (6 active secret versions included in the free tier, then $0.06 per 10K access operations).

### Setup

A shell script (`setup-gcp-secrets.sh`) creates test secrets via the `gcloud` CLI in a target GCP project. Authentication is via Application Default Credentials (`gcloud auth application-default login`).

### Secrets created in GCP

| Secret name | Value | Feature exercised |
|-------------|-------|-------------------|
| `db-credentials` | `{"username":"admin","password":"s3cret","host":"db.example.com"}` | JSON field extraction via GCP |
| `gcp-api-key` | `gcp-test-api-key-12345` | GCP-specific secret |
| `shared-secret` | `shared-value-from-gcp` | Multi-provider fallback (GCP first) |
| `nested-config` | `{"database":{"connection":{"host":"db.example.com","password":"nested-pass","port":5432}}}` | Dot-notation field extraction |

---

## 5. Demo Services

Each service demonstrates a specific `@SecretValue` annotation feature.

| Service | Annotation usage | Feature tested |
|---------|-----------------|----------------|
| `DatabaseService` | `@SecretValue(value="db-credentials", field="password")` | JSON field extraction |
| `ExternalApiService` | `@SecretValue(value="gcp-api-key", provider="gcp")` | Single provider override |
| `FeatureFlagService` | `@SecretValue(value="optional-feature", defaultValue="disabled")` | Default value fallback |
| `MultiCloudService` | `@SecretValue(value="shared-secret", providers={"gcp","aws"})` | Custom provider chain |
| `VersionedSecretService` | `@SecretValue(value="rotating-secret", version="latest")` | Versioned secret access |
| `NestedConfigService` | `@SecretValue(value="nested-config", field="database.connection.password")` | Nested JSON dot-notation |

---

## 6. REST API Layer

Endpoints to observe and interact with secret injection at runtime.

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/secrets/status` | GET | Shows active providers (no secret values exposed) |
| `/api/secrets/health` | GET | Verifies all required secrets are resolvable |
| `/api/secrets/refresh` | POST | Triggers `SecretRefreshService.refreshAll()` |
| `/api/secrets/refresh/{key}` | POST | Triggers single-key cache refresh |
| `/api/demo/db-status` | GET | Confirms DatabaseService secret was injected |
| `/api/demo/api-status` | GET | Confirms ExternalApiService secret was injected |

---

## 7. Integration Tests

Tests are split into two tiers using JUnit 5 `@Tag` annotations.

### Tier 1: `@Tag("local")` — runs anywhere, no cloud credentials needed

| Test class | What it verifies |
|------------|-----------------|
| `LocalProviderDemoTest` | Secrets resolve from local config |
| `AwsProviderDemoTest` | AWS secrets resolve via Testcontainers + LocalStack |
| `FallbackChainDemoTest` | Fallback from unavailable provider to local |
| `JsonFieldExtractionDemoTest` | Nested JSON field extraction |
| `CacheRefreshDemoTest` | Cache invalidation via refresh service |
| `FailOnMissingDemoTest` | Startup failure when required secret is absent |
| `DefaultValueDemoTest` | Default value fallback when secret not found |

### Tier 2: `@Tag("cloud")` — requires GCP credentials

Run manually or in a CI pipeline with credentialed access.

| Test class | What it verifies |
|------------|-----------------|
| `GcpProviderDemoTest` | Real GCP Secret Manager resolution |
| `GcpJsonFieldDemoTest` | JSON extraction from real GCP secret |
| `GcpAwsFallbackDemoTest` | Real GCP to LocalStack AWS fallback chain |
| `FullStackDemoTest` | All providers together, end-to-end |

---

## 8. Docker Compose

```yaml
services:
  localstack:
    image: localstack/localstack:latest
    ports:
      - "4566:4566"
    environment:
      - SERVICES=secretsmanager
      - DEFAULT_REGION=us-east-1
    volumes:
      - ./scripts/localstack-init.sh:/etc/localstack/init/ready.d/init.sh
```

Single service. LocalStack emulates AWS Secrets Manager on port 4566. The init script seeds all test secrets on container startup.

---

## 9. Scripts and Tooling

| File | Purpose |
|------|---------|
| `scripts/setup-gcp-secrets.sh` | Creates/updates test secrets in real GCP via `gcloud` CLI |
| `scripts/cleanup-gcp-secrets.sh` | Deletes test secrets from GCP |
| `scripts/localstack-init.sh` | Seeds secrets into LocalStack on container startup |
| `Makefile` | Common operations (see below) |
| `.gitignore` | Standard Java/Maven ignores |

### Makefile targets

| Target | Command | Description |
|--------|---------|-------------|
| `install-starter` | `mvn -f ../uni-secret-manager-spring/pom.xml install -DskipTests` | Install starter to local `.m2` |
| `build` | `mvn clean compile` | Compile the demo project |
| `test-local` | `mvn test -Dgroups=local` | Run local-only tests (LocalStack, no GCP) |
| `test-cloud` | `mvn test -Dgroups=cloud` | Run cloud tests (requires GCP credentials) |
| `test-all` | `mvn test` | Run all tests |
| `run-dev` | `mvn spring-boot:run -Dspring-boot.run.profiles=dev` | Run with dev profile |
| `run-staging` | `mvn spring-boot:run -Dspring-boot.run.profiles=staging` | Run with staging profile |
| `docker-up` | `docker compose up -d` | Start LocalStack |
| `docker-down` | `docker compose down` | Stop LocalStack |
| `gcp-setup` | `./scripts/setup-gcp-secrets.sh` | Seed secrets into GCP |
| `gcp-cleanup` | `./scripts/cleanup-gcp-secrets.sh` | Remove secrets from GCP |

---

## 10. Build Order

1. Run `make install-starter` to publish the starter to the local `.m2` repository.
2. Create the full demo project structure.
3. Run `make build` to verify compilation.
4. Run `make test-local` to verify local + LocalStack tests pass (no cloud credentials needed).
5. Run `make gcp-setup` to seed secrets into a real GCP project.
6. Run `make test-cloud` to verify GCP end-to-end tests pass.

---

## 11. Project Directory Structure (planned)

```
uni-secret-manager-demo/
├── pom.xml
├── Makefile
├── docker-compose.yml
├── .gitignore
├── docs/
│   └── PROPOSAL.md
├── scripts/
│   ├── localstack-init.sh
│   ├── setup-gcp-secrets.sh
│   └── cleanup-gcp-secrets.sh
├── src/
│   ├── main/
│   │   ├── java/io/github/timhwang777/unisecretdemo/
│   │   │   ├── UniSecretManagerDemoApplication.java
│   │   │   ├── service/
│   │   │   │   ├── DatabaseService.java
│   │   │   │   ├── ExternalApiService.java
│   │   │   │   ├── FeatureFlagService.java
│   │   │   │   ├── MultiCloudService.java
│   │   │   │   ├── VersionedSecretService.java
│   │   │   │   └── NestedConfigService.java
│   │   │   └── controller/
│   │   │       ├── SecretManagementController.java
│   │   │       └── DemoController.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-staging.yml
│   │       └── application-prod.yml
│   └── test/
│       ├── java/io/github/timhwang777/unisecretdemo/
│       │   ├── local/
│       │   │   ├── LocalProviderDemoTest.java
│       │   │   ├── AwsProviderDemoTest.java
│       │   │   ├── FallbackChainDemoTest.java
│       │   │   ├── JsonFieldExtractionDemoTest.java
│       │   │   ├── CacheRefreshDemoTest.java
│       │   │   ├── FailOnMissingDemoTest.java
│       │   │   └── DefaultValueDemoTest.java
│       │   └── cloud/
│       │       ├── GcpProviderDemoTest.java
│       │       ├── GcpJsonFieldDemoTest.java
│       │       ├── GcpAwsFallbackDemoTest.java
│       │       └── FullStackDemoTest.java
│       └── resources/
│           ├── application-test.yml
│           └── application-test-cloud.yml
```
