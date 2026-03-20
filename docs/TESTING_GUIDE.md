# Testing Guide

A detailed, beginner-friendly guide explaining every test in this project, what it needs to run, and how to execute tests in different combinations — from fully offline to full production-like setups.

---

## Table of Contents

1. [How Testing Works in This Project](#1-how-testing-works-in-this-project)
2. [Test Inventory](#2-test-inventory)
3. [Level 1 — Offline Tests (No Docker, No Cloud)](#3-level-1--offline-tests-no-docker-no-cloud)
4. [Level 2 — Local Tests with Docker (LocalStack + Vault)](#4-level-2--local-tests-with-docker-localstack--vault)
5. [Level 3 — Cloud Tests (Real GCP Only)](#5-level-3--cloud-tests-real-gcp-only)
6. [Level 4 — Cloud Tests with Docker (Real GCP + LocalStack)](#6-level-4--cloud-tests-with-docker-real-gcp--localstack)
7. [Level 5 — Full Suite (Everything)](#7-level-5--full-suite-everything)
8. [Running Individual Tests](#8-running-individual-tests)
9. [Running the App Manually Per Profile](#9-running-the-app-manually-per-profile)
10. [Understanding the Configuration Layers](#10-understanding-the-configuration-layers)
11. [Quick Reference Cheat Sheet](#11-quick-reference-cheat-sheet)

---

## 1. How Testing Works in This Project

### Tags

Every test class is annotated with a **JUnit 5 tag**: either `@Tag("local")` or `@Tag("cloud")`. These tags let Maven filter which tests to run.

```
@Tag("local")   → runs without any cloud credentials
@Tag("cloud")   → requires real GCP credentials (and some also need Docker)
```

### Maven Profiles

The `pom.xml` defines three Maven profiles that map to tag filters:

| Profile | Activated by | Runs tests tagged | Default? |
|---------|-------------|-------------------|----------|
| `local-tests` | `mvn test -Plocal-tests` | `local` | Yes (active by default) |
| `cloud-tests` | `mvn test -Pcloud-tests` | `cloud` | No |
| `all-tests` | `mvn test -Pall-tests` | `local` and `cloud` | No |

When you run `mvn test` without specifying a profile, the `local-tests` profile activates by default, so only `@Tag("local")` tests run.

### Conditional Execution

Cloud tests have an extra safety net: `@EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")`. Even if you accidentally run cloud tests without GCP credentials, they will be **skipped** (not failed) because the environment variable isn't set.

### Test Configuration Files

Tests use Spring profiles to load the right configuration:

| File | Activated by | What it configures |
|------|-------------|-------------------|
| `application-test.yml` | `@ActiveProfiles("test")` | Local provider only, all secrets hardcoded, no cloud |
| `application-test-cloud.yml` | `@ActiveProfiles("test-cloud")` | Real GCP provider + local fallback |

Some tests don't use either file — they set their own configuration entirely via `@TestPropertySource` or `@DynamicPropertySource` annotations directly in the test class.

---

## 2. Test Inventory

### Local Tier — `@Tag("local")` (8 tests)

| # | Test Class | Needs Docker? | Needs GCP? | What It Tests |
|---|-----------|:---:|:---:|--------------|
| 1 | `LocalProviderDemoTest` | No | No | Secrets resolve from local config using real service beans (DatabaseService, FeatureFlagService, etc.) |
| 2 | `FallbackChainDemoTest` | No | No | When AWS is listed first but disabled, the local provider handles the request |
| 3 | `JsonFieldExtractionDemoTest` | No | No | Flat JSON fields (`"password"`) and nested dot-notation paths (`"database.connection.host"`) |
| 4 | `DefaultValueDemoTest` | No | No | `@SecretValue(defaultValue = "my-default")` is used when a secret doesn't exist |
| 5 | `FailOnMissingDemoTest` | No | No | Application startup fails with `SecretNotFoundException` when a required secret is absent |
| 6 | `CacheRefreshDemoTest` | No | No | `SecretRefreshService.refresh()` and `refreshAll()` invalidate cached entries, statistics are tracked |
| 7 | `AwsProviderDemoTest` | **Yes** | No | Testcontainers spins up LocalStack, seeds AWS secrets, and verifies `@SecretValue` resolves them |
| 8 | `VaultProviderDemoTest` | **Yes** | No | Testcontainers spins up Vault dev mode, verifies provider precedence, JSON field extraction, and KV v2 version reads |

### Cloud Tier — `@Tag("cloud")` (4 tests)

| # | Test Class | Needs Docker? | Needs GCP? | What It Tests |
|---|-----------|:---:|:---:|--------------|
| 9 | `GcpProviderDemoTest` | No | **Yes** | Directly calls `GcpSecretProvider` against real GCP Secret Manager |
| 10 | `GcpJsonFieldDemoTest` | No | **Yes** | JSON field extraction (flat + nested) from real GCP secrets via Spring Boot context |
| 11 | `GcpAwsFallbackDemoTest` | **Yes** | **Yes** | GCP is tried first (real); if a secret only exists in AWS (LocalStack), fallback works |
| 12 | `FullStackDemoTest` | **Yes** | **Yes** | All three providers together — GCP (real) + AWS (LocalStack) + Local — with JSON extraction, fallback, defaults, and cache refresh |

### Dependency Matrix

```
                    No Docker     Docker Required
                 ┌─────────────┬──────────────────┐
  No GCP Creds   │ Tests 1-6   │ Tests 7-8        │
                 │ (6 tests)   │ (2 tests)        │
                 ├─────────────┼──────────────────┤
  GCP Creds      │ Tests 9-10  │ Tests 11-12      │
  Required       │ (2 tests)   │ (2 tests)        │
                 └─────────────┴──────────────────┘
```

---

## 3. Level 1 — Offline Tests (No Docker, No Cloud)

**What you need:** Java 21, Maven. Nothing else.

**What runs:** Tests 1–6 (all local tests except Docker-dependent ones).

These tests use only the local secret provider. All secrets are hardcoded in `@TestPropertySource` annotations or `application-test.yml`. No network calls are made.

### How to run

```bash
cd /Users/tim/Works/uni-secret-manager-demo

# Run only the tests that don't need Docker
mvn test -Plocal-tests -Dtest="LocalProviderDemoTest,FallbackChainDemoTest,JsonFieldExtractionDemoTest,DefaultValueDemoTest,FailOnMissingDemoTest,CacheRefreshDemoTest"
```

### What each test proves

**LocalProviderDemoTest** — boots the full Spring application with the `test` profile and verifies that all the demo services (`DatabaseService`, `FeatureFlagService`, `VersionedSecretService`, `NestedConfigService`) get their `@SecretValue` fields populated from `application-test.yml`.

```
application-test.yml   →   local provider   →   @SecretValue("db-credentials")
                                                  field = "password"
                                                  ↓
                                              "test-password" injected into DatabaseService.dbPassword
```

**FallbackChainDemoTest** — sets `provider-order: aws, local` but disables AWS (`aws.enabled=false`). The resolver skips AWS and falls through to local.

```
provider-order: [aws, local]
aws.enabled = false
local.enabled = true
local.secrets.fallback-secret = "local-fallback-value"

@SecretValue("fallback-secret")
→ AWS skipped (disabled) → local hit → "local-fallback-value"
```

**JsonFieldExtractionDemoTest** — stores full JSON blobs in the local provider, then extracts specific fields:

```
Secret "flat-json" = {"username":"admin","password":"flat-pass"}

@SecretValue(value = "flat-json", field = "username")  → "admin"
@SecretValue(value = "flat-json", field = "password")  → "flat-pass"

Secret "nested-json" = {"database":{"connection":{"host":"db.test","password":"nested-pass","port":5432}}}

@SecretValue(value = "nested-json", field = "database.connection.host")     → "db.test"
@SecretValue(value = "nested-json", field = "database.connection.password") → "nested-pass"
```

**DefaultValueDemoTest** — one secret exists, one doesn't. The missing secret falls back to its `defaultValue`:

```
local.secrets.existing-secret = "real-value"
(no entry for "non-existent-secret")

@SecretValue("existing-secret")                                      → "real-value"
@SecretValue(value = "non-existent-secret", defaultValue = "my-default") → "my-default"
```

**FailOnMissingDemoTest** — creates a standalone Spring Boot app with `fail-on-missing=true` and a bean that requests a secret that doesn't exist. Asserts the app fails to start with `SecretNotFoundException`.

```
fail-on-missing = true
@SecretValue("this-secret-does-not-exist")  → SecretNotFoundException → startup fails
```

**CacheRefreshDemoTest** — directly manipulates the `SecretCache` and `SecretRefreshService` beans:

```
1. Put "manual-value" into cache under key "manual-key"
2. Call refreshService.refresh("manual-key")
3. Assert cache no longer contains "manual-key"
4. Put 3 entries, call refreshAll(), assert all gone
5. Call refresh() twice, refreshAll() once → stats: single=2, full=1
```

---

## 4. Level 2 — Local Tests with Docker (LocalStack + Vault)

**What you need:** Java 21, Maven, Docker running.

**What runs:** All 8 local tests (Tests 1–8), including `AwsProviderDemoTest` and `VaultProviderDemoTest`.

This is the default `make test-local` target.

### Prerequisites

Make sure Docker is running:

```bash
docker info
# Should print Docker system info, not an error
```

### How to run

```bash
cd /Users/tim/Works/uni-secret-manager-demo

make test-local
# Equivalent to: mvn test -Plocal-tests
```

### What the Docker-based tests prove

**AwsProviderDemoTest** — spins up LocalStack and verifies AWS secret resolution end to end:

```
1. @Container annotation tells Testcontainers to start LocalStack 4.12.0
2. LocalStack boots with Secrets Manager service on a random port
3. @BeforeAll seeds two secrets into LocalStack:
   - "aws-plain-secret" → "aws-plain-value"
   - "aws-json-secret"  → {"username":"aws-admin","password":"aws-s3cret"}
4. @DynamicPropertySource configures the Spring context:
   - secrets.aws.enabled = true
   - secrets.aws.endpoint = http://localhost:<random-port>  (points to LocalStack)
   - secrets.provider-order = aws
5. Spring boots → SecretValueBeanPostProcessor processes @SecretValue fields
6. AwsTestBean fields get populated:
   - @SecretValue("aws-plain-secret")                        → "aws-plain-value"
   - @SecretValue(value = "aws-json-secret", field = "password") → "aws-s3cret"
7. Assertions verify the values
8. Testcontainers shuts down LocalStack after tests complete
```

**VaultProviderDemoTest** — spins up Vault dev mode and verifies:

```
1. Vault provider can resolve secrets from a containerized dev server
2. JSON field extraction works for Vault values
3. KV v2 version reads work (explicit version lookup)
4. Fallback to local works when a key doesn't exist in Vault
```

You do **NOT** need to run `docker compose up` or `make docker-up` for these tests. Testcontainers manages container lifecycle automatically.

### If Docker is not available

If Docker isn't running, Docker-dependent tests will fail. The other 6 local tests still pass. To skip Docker-dependent tests:

```bash
mvn test -Plocal-tests -Dtest="!AwsProviderDemoTest,!VaultProviderDemoTest"
```

---

## 5. Level 3 — Cloud Tests (Real GCP Only)

**What you need:** Java 21, Maven, GCP credentials configured (see `docs/GCP_SETUP_GUIDE.md`).

**What runs:** Tests 9–10 (`GcpProviderDemoTest`, `GcpJsonFieldDemoTest`).

These two cloud tests only talk to real GCP — no Docker needed.

### Prerequisites

1. GCP project with Secret Manager API enabled
2. Test secrets seeded (via `make gcp-setup` or `./scripts/setup-gcp-secrets.sh`)
3. Application Default Credentials configured:
   ```bash
   gcloud auth application-default login
   ```
4. Environment variable set:
   ```bash
   export GCP_PROJECT_ID=your-project-id
   ```

### How to run

```bash
cd /Users/tim/Works/uni-secret-manager-demo

export GCP_PROJECT_ID=your-project-id

# Run only the two GCP-only tests (no Docker needed)
mvn test -Pcloud-tests -Dtest="GcpProviderDemoTest,GcpJsonFieldDemoTest"
```

### What each test proves

**GcpProviderDemoTest** — instantiates `GcpSecretProvider` directly (no Spring context) and calls the real GCP API:

```
GCP Secret Manager
├── getSecret("gcp-api-key")                → "gcp-test-api-key-12345"  (plain string)
├── getSecret("db-credentials")             → JSON blob containing "username" and "password"
└── getSecret("non-existent-secret-<random>") → Optional.empty()
```

This validates that the GCP SDK, Application Default Credentials, and project configuration work end-to-end.

**GcpJsonFieldDemoTest** — boots a full Spring context with `@ActiveProfiles("test-cloud")`, which loads `application-test-cloud.yml` (GCP enabled, local fallback). A test bean uses `@SecretValue` with JSON field extraction against real GCP secrets:

```
GCP: "db-credentials" = {"username":"admin","password":"s3cret",...}
GCP: "nested-config"  = {"database":{"connection":{"host":"db.example.com","password":"nested-pass",...},...},...}

@SecretValue(value = "db-credentials", field = "username")                  → "admin"
@SecretValue(value = "db-credentials", field = "password")                  → "s3cret"
@SecretValue(value = "nested-config", field = "database.connection.password") → "nested-pass"
@SecretValue(value = "nested-config", field = "cache.redis.host")           → "redis.example.com"
```

---

## 6. Level 4 — Cloud Tests with Docker (Real GCP + LocalStack)

**What you need:** Java 21, Maven, Docker running, GCP credentials configured.

**What runs:** Tests 11–12 (`GcpAwsFallbackDemoTest`, `FullStackDemoTest`).

These tests combine real GCP with LocalStack to validate cross-provider behavior.

### Prerequisites

Everything from [Level 3](#5-level-3--cloud-tests-real-gcp-only) plus Docker running.

### How to run

```bash
cd /Users/tim/Works/uni-secret-manager-demo

export GCP_PROJECT_ID=your-project-id

# Run the two tests that need both GCP and Docker
mvn test -Pcloud-tests -Dtest="GcpAwsFallbackDemoTest,FullStackDemoTest"
```

### What each test proves

**GcpAwsFallbackDemoTest** — the core fallback chain test. Provider order is `gcp, aws`. Two secrets are tested:

```
provider-order: [gcp, aws]

Secret "gcp-api-key":
  └─ GCP has it → resolved from GCP → "gcp-test-api-key-12345"

Secret "aws-only-secret":
  ├─ GCP does NOT have it → miss
  └─ AWS (LocalStack) has it → resolved from AWS → "aws-only-value"
```

This proves that the fallback chain works across real cloud providers and emulators.

**FullStackDemoTest** — the most comprehensive test. All three providers are active (`gcp, aws, local`):

```
provider-order: [gcp, aws, local]

┌────────────────────────┬───────────────────┬────────────────────────────┐
│ Secret                 │ Resolved From     │ Value                      │
├────────────────────────┼───────────────────┼────────────────────────────┤
│ db-credentials.password│ GCP (1st in chain)│ "s3cret"                   │
│ nested-config (nested) │ GCP (1st in chain)│ "db.example.com"           │
│ aws-exclusive          │ AWS (GCP miss)    │ "aws-exclusive-value"      │
│ local-only-secret      │ Local (GCP+AWS miss)│ "local-only-value"       │
│ phantom-secret         │ Default value     │ "phantom-default"          │
└────────────────────────┴───────────────────┴────────────────────────────┘
```

It also tests that `SecretRefreshService` is wired and functional.

---

## 7. Level 5 — Full Suite (Everything)

**What you need:** Java 21, Maven, Docker running, GCP credentials configured.

**What runs:** All 12 tests.

### Prerequisites

Everything from all previous levels.

### How to run

```bash
cd /Users/tim/Works/uni-secret-manager-demo

export GCP_PROJECT_ID=your-project-id

make test-all
# Equivalent to: mvn test -Pall-tests
```

### What happens

Maven runs both `@Tag("local")` and `@Tag("cloud")` tests in a single pass:

```
[INFO] Running io.github.timhwang777.unisecretdemo.local.LocalProviderDemoTest        ✓ (no deps)
[INFO] Running io.github.timhwang777.unisecretdemo.local.FallbackChainDemoTest         ✓ (no deps)
[INFO] Running io.github.timhwang777.unisecretdemo.local.JsonFieldExtractionDemoTest   ✓ (no deps)
[INFO] Running io.github.timhwang777.unisecretdemo.local.DefaultValueDemoTest          ✓ (no deps)
[INFO] Running io.github.timhwang777.unisecretdemo.local.FailOnMissingDemoTest         ✓ (no deps)
[INFO] Running io.github.timhwang777.unisecretdemo.local.CacheRefreshDemoTest          ✓ (no deps)
[INFO] Running io.github.timhwang777.unisecretdemo.local.AwsProviderDemoTest           ✓ (Docker)
[INFO] Running io.github.timhwang777.unisecretdemo.local.VaultProviderDemoTest         ✓ (Docker)
[INFO] Running io.github.timhwang777.unisecretdemo.cloud.GcpProviderDemoTest           ✓ (GCP)
[INFO] Running io.github.timhwang777.unisecretdemo.cloud.GcpJsonFieldDemoTest          ✓ (GCP)
[INFO] Running io.github.timhwang777.unisecretdemo.cloud.GcpAwsFallbackDemoTest        ✓ (Docker+GCP)
[INFO] Running io.github.timhwang777.unisecretdemo.cloud.FullStackDemoTest             ✓ (Docker+GCP)
```

---

## 8. Running Individual Tests

You can run any single test class using the `-Dtest` flag. You need to bypass the default profile tag filter by passing an empty groups value.

### Syntax

```bash
# Run a single test class
mvn test -Dtest="ClassName" -Dgroups=""

# Run a single test method
mvn test -Dtest="ClassName#methodName" -Dgroups=""

# Run multiple specific test classes
mvn test -Dtest="ClassA,ClassB" -Dgroups=""

# Exclude Docker-dependent local tests
mvn test -Plocal-tests -Dtest="!AwsProviderDemoTest,!VaultProviderDemoTest"
```

### Examples

```bash
# Just the JSON extraction test (no deps)
mvn test -Dtest="JsonFieldExtractionDemoTest" -Dgroups=""

# Just the AWS LocalStack test (needs Docker)
mvn test -Dtest="AwsProviderDemoTest" -Dgroups=""

# Just the Vault provider test (needs Docker)
mvn test -Dtest="VaultProviderDemoTest" -Dgroups=""

# Just the full stack cloud test (needs Docker + GCP)
export GCP_PROJECT_ID=your-project-id
mvn test -Dtest="FullStackDemoTest" -Dgroups=""

# Run both GCP-only tests (needs GCP, no Docker)
export GCP_PROJECT_ID=your-project-id
mvn test -Dtest="GcpProviderDemoTest,GcpJsonFieldDemoTest" -Dgroups=""

# Run all local tests EXCEPT the Docker-dependent one
mvn test -Plocal-tests -Dtest="!AwsProviderDemoTest,!VaultProviderDemoTest"
```

---

## 9. Running the App Manually Per Profile

Beyond automated tests, you can start the application with different Spring profiles to observe behavior interactively.

### Dev Profile (local only, no cloud)

```bash
make run-dev
# Equivalent to: mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**What's active:**
- Local provider only
- All secrets from `application-dev.yml`
- `fail-on-missing = false`
- No Docker, no GCP needed

**Try it:**
```bash
curl http://localhost:8080/api/demo/all
curl http://localhost:8080/api/secrets/status
```

### Staging Profile (GCP + LocalStack + local)

```bash
# Start LocalStack first
make docker-up

# Set GCP credentials
export GCP_PROJECT_ID=your-project-id

# Run the app
make run-staging
# Equivalent to: mvn spring-boot:run -Dspring-boot.run.profiles=staging
```

**What's active:**
- GCP (real) as primary provider
- AWS (LocalStack at localhost:4566) as secondary
- Local as fallback
- `fail-on-missing = true`

**Try it:**
```bash
# See all services and where secrets came from
curl http://localhost:8080/api/demo/all

# Check which providers are active
curl http://localhost:8080/api/secrets/status

# Trigger a cache refresh
curl -X POST http://localhost:8080/api/secrets/refresh

# Refresh a single key
curl -X POST http://localhost:8080/api/secrets/refresh/db-credentials
```

**Stop:**
```bash
# Ctrl+C to stop the app, then:
make docker-down
```

---

## 10. Understanding the Configuration Layers

This section explains how Spring profiles, test annotations, and config files interact.

### For regular tests with `@ActiveProfiles`

```
Base config:          application.yml           (always loaded)
                            +
Profile config:       application-test.yml      (loaded by @ActiveProfiles("test"))
                            =
Merged config used by the test
```

Example: `LocalProviderDemoTest` uses `@ActiveProfiles("test")`, which merges `application.yml` (base cache/retry settings) with `application-test.yml` (local provider + hardcoded secrets).

### For tests with `@TestPropertySource`

```
Base config:          application.yml           (always loaded)
                            +
Inline properties:    @TestPropertySource(properties = {
                          "secrets.provider-order=local",
                          "secrets.local.enabled=true",
                          ...
                      })
                            =
Merged config (inline properties override file values)
```

Example: `FallbackChainDemoTest` defines all its config inline. No profile YAML needed.

### For tests with `@DynamicPropertySource`

```
Base config:          application.yml           (always loaded)
                            +
Dynamic properties:   @DynamicPropertySource     (set at runtime from container info)
                          "secrets.aws.endpoint" = localstack.getEndpoint()
                          "secrets.aws.region"   = localstack.getRegion()
                            =
Merged config (dynamic properties override file values)
```

Example: `AwsProviderDemoTest` gets the LocalStack endpoint URL dynamically after Testcontainers starts the container. This is necessary because the container port is randomly assigned.

### For cloud tests

```
Base config:          application.yml               (always loaded)
                            +
Profile config:       application-test-cloud.yml    (loaded by @ActiveProfiles("test-cloud"))
                            +
Dynamic properties:   @DynamicPropertySource         (LocalStack endpoint, if used)
                            +
Environment var:      GCP_PROJECT_ID                 (read via ${GCP_PROJECT_ID:})
                            =
Merged config
```

### Priority order (highest wins)

```
1. @DynamicPropertySource          (highest priority — runtime values)
2. @TestPropertySource             (inline test properties)
3. application-{profile}.yml       (profile-specific config)
4. application.yml                 (base config, lowest priority)
```

---

## 11. Quick Reference Cheat Sheet

### "I just want to run tests. What do I type?"

| I have... | Command | Tests that run |
|-----------|---------|---------------|
| Java + Maven only | `mvn test -Plocal-tests -Dtest='!AwsProviderDemoTest,!VaultProviderDemoTest'` | 1–6 (6 tests) |
| Java + Maven + Docker | `make test-local` | 1–8 (8 tests) |
| Java + Maven + GCP creds | `export GCP_PROJECT_ID=... && mvn test -Pcloud-tests -Dtest='GcpProviderDemoTest,GcpJsonFieldDemoTest'` | 9–10 (2 tests) |
| Java + Maven + Docker + GCP creds | `export GCP_PROJECT_ID=... && make test-all` | 1–12 (12 tests) |

### "I want to run the app. What do I type?"

| Environment | Command | What I need |
|------------|---------|-------------|
| Dev (offline) | `make run-dev` | Nothing |
| Staging (mixed) | `make docker-up && export GCP_PROJECT_ID=... && make run-staging` | Docker + GCP |
| Vault demo (local) | `make run-vault` | Docker |

### "Something failed. What do I check?"

| Error | Likely cause | Fix |
|-------|-------------|-----|
| `AwsProviderDemoTest` fails | Docker not running | Start Docker Desktop |
| `VaultProviderDemoTest` fails | Docker not running or port `8200` blocked | Start Docker Desktop, free port `8200` |
| Cloud tests skipped | `GCP_PROJECT_ID` not set | `export GCP_PROJECT_ID=...` |
| `SecretNotFoundException` in cloud tests | GCP secrets not seeded | `make gcp-setup` or `./scripts/setup-gcp-secrets.sh your-project-id` |
| `IOException: Application Default Credentials are not available` | ADC not configured | `gcloud auth application-default login` |
| `API has not been used in project` | Secret Manager API not enabled | `gcloud services enable secretmanager.googleapis.com --project=...` |
