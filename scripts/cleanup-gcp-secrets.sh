#!/bin/bash
# Deletes test secrets from GCP Secret Manager.
#
# Usage:
#   ./scripts/cleanup-gcp-secrets.sh                    # uses GCP_PROJECT_ID env var
#   ./scripts/cleanup-gcp-secrets.sh my-project-id      # explicit project ID

set -euo pipefail

PROJECT_ID="${1:-${GCP_PROJECT_ID:-}}"

if [ -z "$PROJECT_ID" ]; then
  echo "Error: No GCP project ID provided."
  echo "Usage: $0 <project-id>"
  echo "   or: export GCP_PROJECT_ID=your-project-id && $0"
  exit 1
fi

echo "=== Cleaning up GCP secrets in project: $PROJECT_ID ==="

SECRETS=(
  "db-credentials"
  "gcp-api-key"
  "shared-secret"
  "nested-config"
)

for secret in "${SECRETS[@]}"; do
  if gcloud secrets describe "$secret" --project="$PROJECT_ID" &>/dev/null; then
    echo "Deleting secret: $secret"
    gcloud secrets delete "$secret" --project="$PROJECT_ID" --quiet
  else
    echo "Secret not found (skipping): $secret"
  fi
done

echo ""
echo "=== Cleanup complete ==="
