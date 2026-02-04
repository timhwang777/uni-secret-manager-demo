#!/bin/bash
# Seeds test secrets into LocalStack AWS Secrets Manager on container startup.
# This script runs automatically when LocalStack becomes ready.

set -euo pipefail

echo "=== Seeding secrets into LocalStack ==="

# db-credentials: JSON blob for testing field extraction
awslocal secretsmanager create-secret \
  --name "db-credentials" \
  --secret-string '{"username":"admin","password":"s3cret","host":"db.example.com","port":5432}'

# api-key: plain string secret
awslocal secretsmanager create-secret \
  --name "api-key" \
  --secret-string "test-api-key-12345"

# shared-secret: used for multi-provider fallback chain testing
awslocal secretsmanager create-secret \
  --name "shared-secret" \
  --secret-string "shared-value-from-aws"

# rotating-secret: secret with version support
# Create initial version
awslocal secretsmanager create-secret \
  --name "rotating-secret" \
  --secret-string "rotated-value-v2"

# Update to create a previous version (AWSCURRENT becomes v2, AWSPREVIOUS becomes v1)
awslocal secretsmanager put-secret-value \
  --secret-id "rotating-secret" \
  --secret-string "rotated-value-v3"

# nested-config: deeply nested JSON for dot-notation extraction testing
awslocal secretsmanager create-secret \
  --name "nested-config" \
  --secret-string '{"database":{"connection":{"host":"db.example.com","password":"nested-pass","port":5432}},"cache":{"redis":{"host":"redis.example.com","port":6379}}}'

echo "=== All secrets seeded successfully ==="
awslocal secretsmanager list-secrets --query 'SecretList[].Name' --output table
