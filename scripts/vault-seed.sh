#!/bin/bash
# Seeds demo secrets into the local Vault dev container (KV v2 at mount: secret).
set -euo pipefail

VAULT_ADDR="${VAULT_ADDR:-http://localhost:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-dev-only-token}"

write_secret() {
  local key="$1"
  local json_data="$2"

  curl -fsS \
    -X POST \
    -H "X-Vault-Token: ${VAULT_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{\"data\":${json_data}}" \
    "${VAULT_ADDR}/v1/secret/data/${key}" >/dev/null
}

echo "=== Seeding secrets into Vault (${VAULT_ADDR}) ==="

# Core secrets used by demo services.
write_secret "db-credentials" '{"username":"vault-admin","password":"vault-s3cret","host":"vault-db.local","port":5432}'
write_secret "api-key" '{"value":"vault-api-key-12345"}'
write_secret "shared-secret" '{"value":"shared-value-from-vault"}'

# Two writes create two KV v2 versions for versioned lookup demos.
write_secret "rotating-secret" '{"value":"vault-rotated-v1"}'
write_secret "rotating-secret" '{"value":"vault-rotated-v2"}'

# Nested object shape for dot-path field extraction tests.
write_secret "nested-config" '{"database":{"connection":{"host":"vault-db.local","password":"vault-nested-pass","port":5432}},"cache":{"redis":{"host":"vault-redis.local","port":6379}}}'

echo "=== Vault secrets seeded successfully ==="
