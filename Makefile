.PHONY: install-starter build check-gcp check-docker check-localstack check-vault test-offline test-local test-gcp test-cloud test-all run-dev run-staging run-staging-vault run-vault docker-up docker-up-vault docker-down gcp-setup gcp-cleanup

# Install the uni-secret-manager-spring starter to local Maven repository (~/.m2)
# Prerequisite: Clone the starter repo as a sibling directory
#   git clone https://github.com/timhwang777/uni-secret-manager-spring.git ../uni-secret-manager-spring
install-starter:
	mvn -f ../uni-secret-manager-spring/pom.xml install -DskipTests -Djacoco.skip=true

# Compile the demo project
build:
	mvn clean compile

# ============================================================================
# Prerequisite Checks
# Reusable targets to verify required dependencies are available
# ============================================================================

# Check that GCP_PROJECT_ID environment variable is set
check-gcp:
	@if [ -z "$$GCP_PROJECT_ID" ]; then \
		echo "Error: GCP_PROJECT_ID is not set"; \
		echo "Usage: export GCP_PROJECT_ID=your-project-id"; \
		exit 1; \
	fi
	@echo "✓ GCP_PROJECT_ID is set to: $$GCP_PROJECT_ID"

# Check that Docker daemon is running (needed for Testcontainers)
check-docker:
	@docker info > /dev/null 2>&1 || (echo "Error: Docker is not running. Please start Docker Desktop." && exit 1)
	@echo "✓ Docker is running"

# Start LocalStack via docker-compose and wait for it to be healthy
# Used for run-staging (tests use Testcontainers which manages its own containers)
check-localstack: check-docker
	@echo "Starting LocalStack..."
	@docker compose up -d localstack
	@echo "Waiting for LocalStack to be healthy..."
	@until curl -s http://localhost:4566/_localstack/health 2>/dev/null | grep -q '"secretsmanager"'; do \
		echo "  waiting for secretsmanager service..."; \
		sleep 2; \
	done
	@echo "✓ LocalStack is ready"

# Start Vault dev container via docker-compose, wait for health, and seed demo secrets
# Used for run-vault profile (tests use Testcontainers which manages its own containers)
check-vault: check-docker
	@echo "Starting Vault dev container..."
	@docker compose up -d vault
	@echo "Waiting for Vault to be healthy..."
	@until curl -fs http://localhost:8200/v1/sys/health > /dev/null 2>&1; do \
		echo "  waiting for Vault service..."; \
		sleep 2; \
	done
	@echo "Seeding Vault demo secrets..."
	@./scripts/vault-seed.sh
	@echo "✓ Vault is ready"

# ============================================================================
# Test Commands
# Aligned with docs/TESTING_GUIDE.md test levels
# ============================================================================

# Level 1: Offline tests (Tests 1-6)
# Requirements: Java 21 + Maven only
# No Docker, no cloud credentials needed
test-offline:
	mvn test -Plocal-tests -Dtest="LocalProviderDemoTest,FallbackChainDemoTest,JsonFieldExtractionDemoTest,DefaultValueDemoTest,FailOnMissingDemoTest,CacheRefreshDemoTest"

# Level 2: Local tests with Docker (Tests 1-9)
# Requirements: Java 21 + Maven + Docker
# Includes AwsProviderDemoTest, VaultProviderDemoTest, and VaultAwsFallbackDemoTest (all use Testcontainers)
test-local: check-docker
	mvn test -Plocal-tests

# Level 3: Cloud tests - GCP only (Tests 10-11)
# Requirements: Java 21 + Maven + GCP credentials
# No Docker needed - tests real GCP Secret Manager directly
test-gcp: check-gcp
	mvn test -Pcloud-tests -Dtest="GcpProviderDemoTest,GcpJsonFieldDemoTest"

# Level 4: Cloud tests with Docker (Tests 12-13)
# Requirements: Java 21 + Maven + Docker + GCP credentials
# Tests cross-provider fallback between real GCP and LocalStack AWS
test-cloud: check-gcp check-docker
	mvn test -Pcloud-tests -Dtest="GcpAwsFallbackDemoTest,FullStackDemoTest"

# Level 5: Full test suite (Tests 1-13)
# Requirements: Java 21 + Maven + Docker + GCP credentials
# Runs all local and cloud tests
test-all: check-gcp check-docker
	mvn test -Pall-tests

# ============================================================================
# Run Commands
# ============================================================================

# Run with dev profile (local provider only)
# Requirements: Java 21 + Maven only
# No external dependencies - all secrets from application-dev.yml
run-dev:
	mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Run with staging profile (GCP + LocalStack AWS + local fallback)
# Requirements: Docker + GCP credentials
# Automatically starts LocalStack and verifies it's healthy before running
# Note: Run "make docker-down" when done to stop LocalStack
run-staging: check-gcp check-localstack
	AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test mvn spring-boot:run -Dspring-boot.run.profiles=staging

# Run with staging-vault profile (Vault + LocalStack AWS + local fallback)
# Requirements: Docker only (no GCP credentials needed)
# Automatically starts/seeds Vault and LocalStack before running
# Note: Run "make docker-down" when done to stop containers
run-staging-vault: check-vault check-localstack
	AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test VAULT_TOKEN=dev-only-token mvn spring-boot:run -Dspring-boot.run.profiles=staging-vault

# Run with vault profile (Vault dev container + local fallback)
# Requirements: Docker
# Automatically starts/seeds Vault dev container before running
# Note: Run "make docker-down" when done to stop Vault
run-vault: check-vault
	VAULT_TOKEN=dev-only-token mvn spring-boot:run -Dspring-boot.run.profiles=vault

# ============================================================================
# Docker Commands
# ============================================================================

# Start LocalStack manually (prefer using check-localstack for automated setup)
docker-up:
	docker compose up -d

# Start only the Vault dev container
docker-up-vault:
	docker compose up -d vault

# Stop LocalStack and clean up containers
docker-down:
	docker compose down

# ============================================================================
# GCP Setup Commands
# ============================================================================

# Create test secrets in GCP Secret Manager
# Prerequisite: export GCP_PROJECT_ID=your-project-id
gcp-setup: check-gcp
	./scripts/setup-gcp-secrets.sh

# Delete test secrets from GCP Secret Manager
# Prerequisite: export GCP_PROJECT_ID=your-project-id
gcp-cleanup: check-gcp
	./scripts/cleanup-gcp-secrets.sh
