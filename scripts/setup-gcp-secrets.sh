#!/bin/bash
# Creates test secrets in GCP Secret Manager.
# Requires: gcloud CLI authenticated (gcloud auth application-default login)
#
# Usage:
#   ./scripts/setup-gcp-secrets.sh                    # uses GCP_PROJECT_ID env var
#   ./scripts/setup-gcp-secrets.sh my-project-id      # explicit project ID

set -euo pipefail

PROJECT_ID="${1:-${GCP_PROJECT_ID:-}}"

if [ -z "$PROJECT_ID" ]; then
  echo "Error: No GCP project ID provided."
  echo "Usage: $0 <project-id>"
  echo "   or: export GCP_PROJECT_ID=your-project-id && $0"
  exit 1
fi

echo "=== Setting up GCP secrets in project: $PROJECT_ID ==="

create_or_update_secret() {
  local name="$1"
  local value="$2"

  if gcloud secrets describe "$name" --project="$PROJECT_ID" &>/dev/null; then
    echo "Updating existing secret: $name"
    echo -n "$value" | gcloud secrets versions add "$name" --project="$PROJECT_ID" --data-file=-
  else
    echo "Creating new secret: $name"
    echo -n "$value" | gcloud secrets create "$name" --project="$PROJECT_ID" --data-file=- --replication-policy="automatic"
  fi
}

# db-credentials: JSON blob for testing field extraction
create_or_update_secret "db-credentials" \
  '{"username":"admin","password":"s3cret","host":"db.example.com","port":5432}'

# gcp-api-key: GCP-specific secret
create_or_update_secret "gcp-api-key" \
  "gcp-test-api-key-12345"

# shared-secret: used for multi-provider fallback chain testing
create_or_update_secret "shared-secret" \
  "shared-value-from-gcp"

# nested-config: deeply nested JSON for dot-notation extraction testing
create_or_update_secret "nested-config" \
  '{"database":{"connection":{"host":"db.example.com","password":"nested-pass","port":5432}},"cache":{"redis":{"host":"redis.example.com","port":6379}}}'

echo ""
echo "=== All GCP secrets created successfully ==="
gcloud secrets list --project="$PROJECT_ID" --format="table(name)"
