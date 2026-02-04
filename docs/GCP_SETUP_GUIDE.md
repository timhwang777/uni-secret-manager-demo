# GCP Secret Manager Setup Guide

A beginner-friendly, step-by-step guide to set up Google Cloud Secret Manager for the `uni-secret-manager-demo` project.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Create a Google Cloud Account](#2-create-a-google-cloud-account)
3. [Install the gcloud CLI](#3-install-the-gcloud-cli)
4. [Create a GCP Project](#4-create-a-gcp-project)
5. [Enable the Secret Manager API](#5-enable-the-secret-manager-api)
6. [Set Up Authentication](#6-set-up-authentication)
7. [Grant IAM Permissions](#7-grant-iam-permissions)
8. [Seed Test Secrets](#8-seed-test-secrets)
9. [Verify the Setup](#9-verify-the-setup)
10. [Run the Demo Project](#10-run-the-demo-project)
11. [Cleanup](#11-cleanup)
12. [Troubleshooting](#12-troubleshooting)
13. [References](#13-references)

---

## 1. Prerequisites

Before you begin, make sure you have:

- A **Google account** (Gmail or Google Workspace)
- A **credit or debit card** (required for identity verification — you will NOT be charged if you stay within the free tier)
- **macOS, Linux, or Windows** machine
- **Terminal** access (Terminal on macOS/Linux, PowerShell or CMD on Windows)

### Cost

GCP Secret Manager is **effectively free** for this demo:

- **6 active secret versions** are included in the free tier at no cost
- Beyond that: $0.06 per 10,000 access operations
- New GCP accounts get **$300 in free credits** valid for 90 days

---

## 2. Create a Google Cloud Account

If you already have a GCP account, skip to [Step 3](#3-install-the-gcloud-cli).

1. Go to [https://cloud.google.com/free](https://cloud.google.com/free)
2. Click **Get started for free**
3. Sign in with your Google account
4. Fill in the billing information (credit card for verification only)
5. Accept the terms of service

When signup completes, Google automatically creates:

- A project named **"My First Project"**
- A billing account named **"My Billing Account"** with $300 free credits
- A payments profile

> **Note:** If you don't upgrade to a paid account within 90 days or you exhaust the $300 credit, your account enters a 30-day grace period. After that, resources are permanently deleted. For this demo, the free tier is more than sufficient.

---

## 3. Install the gcloud CLI

The `gcloud` CLI is the command-line tool for interacting with Google Cloud.

### macOS (Homebrew — recommended)

```bash
brew install --cask gcloud-cli
```

### macOS / Linux (manual)

```bash
# Download the installer
curl https://sdk.cloud.google.com | bash

# Restart your shell
exec -l $SHELL

# Verify installation
gcloud version
```

### Windows

1. Download the installer from [https://cloud.google.com/sdk/docs/install-sdk](https://cloud.google.com/sdk/docs/install-sdk)
2. Run the installer (it bundles Python automatically)
3. The installer will open a terminal and run `gcloud init`

### Initialize gcloud

After installation, run:

```bash
gcloud init
```

This will:

1. Open a browser to authenticate with your Google account
2. Ask you to select or create a GCP project
3. Optionally set a default region/zone

Verify it worked:

```bash
gcloud --version
# Should print something like: Google Cloud SDK 554.0.0
```

---

## 4. Create a GCP Project

If you already have a project you want to use, skip to [Step 5](#5-enable-the-secret-manager-api).

### Option A: Via the Console (browser)

1. Go to [https://console.cloud.google.com/projectcreate](https://console.cloud.google.com/projectcreate)
2. Enter a **Project name** (e.g., `uni-secret-demo`)
3. Note the **Project ID** shown below the name — you'll need this later
4. Click **Create**

### Option B: Via the gcloud CLI

```bash
# Replace "uni-secret-demo" with your preferred project ID
gcloud projects create uni-secret-demo --name="Uni Secret Manager Demo"
```

> **Important:** Project IDs must be globally unique across all of Google Cloud. If `uni-secret-demo` is taken, add a random suffix like `uni-secret-demo-12345`.

### Set it as your default project

```bash
gcloud config set project uni-secret-demo
```

### Link billing (required to use APIs)

```bash
# List your billing accounts
gcloud billing accounts list

# Link billing to your project (replace BILLING_ACCOUNT_ID)
gcloud billing projects link uni-secret-demo --billing-account=BILLING_ACCOUNT_ID
```

Or link billing via the Console: go to **Billing** > **My Projects** > click **Link a billing account** next to your project.

---

## 5. Enable the Secret Manager API

The Secret Manager API must be enabled once per project before you can create or access secrets.

```bash
gcloud services enable secretmanager.googleapis.com --project=uni-secret-demo
```

Verify it's enabled:

```bash
gcloud services list --enabled --project=uni-secret-demo | grep secretmanager
# Should output: secretmanager.googleapis.com
```

---

## 6. Set Up Authentication

Your local machine needs credentials so the Java application (and the `gcloud` CLI) can talk to GCP.

### What is Application Default Credentials (ADC)?

ADC is the standard way Google Cloud client libraries discover credentials. When your Spring Boot app starts, the GCP Secret Manager SDK automatically looks for credentials in this order:

1. `GOOGLE_APPLICATION_CREDENTIALS` environment variable (path to a service account key file)
2. Credentials created by `gcloud auth application-default login`
3. Metadata server (when running on GCP infrastructure like GKE, Cloud Run, etc.)

For local development, option 2 is the recommended approach.

### Set up ADC for local development

```bash
gcloud auth application-default login
```

This will:

1. Open a browser window
2. Ask you to sign in with your Google account
3. Grant permission to access your GCP resources
4. Save a credential file at a well-known location on your filesystem

> **Note:** This is different from `gcloud auth login`. The `gcloud auth login` command authenticates only the `gcloud` CLI itself. The `gcloud auth application-default login` command sets up credentials that **all** Google Cloud client libraries (Java, Python, Go, etc.) can use.

### Set the quota project

```bash
gcloud auth application-default set-quota-project uni-secret-demo
```

This tells Google Cloud which project to bill for API calls made with your ADC credentials.

### Export the project ID

The demo project reads the GCP project ID from an environment variable. Add this to your shell profile (`~/.zshrc`, `~/.bashrc`, etc.):

```bash
export GCP_PROJECT_ID=uni-secret-demo
```

Then reload your shell:

```bash
source ~/.zshrc   # or ~/.bashrc
```

---

## 7. Grant IAM Permissions

Your Google account needs the **Secret Manager Admin** role to create and access secrets.

If you created the project, you're the **Owner** and already have full access. You can skip this step.

If someone else owns the project and gave you access, ask them to run:

```bash
# Replace YOUR_EMAIL with your Google account email
gcloud projects add-iam-policy-binding uni-secret-demo \
  --member="user:YOUR_EMAIL@gmail.com" \
  --role="roles/secretmanager.admin"
```

### IAM Roles for Secret Manager (reference)

| Role | Description | Use case |
|------|-------------|----------|
| `roles/secretmanager.admin` | Full access to create, delete, and manage secrets | Project setup, CI/CD |
| `roles/secretmanager.secretAccessor` | Read-only access to secret **values** | Application runtime |
| `roles/secretmanager.viewer` | Read-only access to secret **metadata** (not values) | Monitoring, auditing |

> **Best practice:** In production, give your application's service account only `roles/secretmanager.secretAccessor` — the minimum permission needed to read secrets.

---

## 8. Seed Test Secrets

Now you're ready to create the test secrets the demo project expects.

### Option A: Run the setup script (recommended)

```bash
cd /Users/tim/Works/uni-secret-manager-demo

# Make sure the script is executable
chmod +x scripts/setup-gcp-secrets.sh

# Run it with your project ID
./scripts/setup-gcp-secrets.sh uni-secret-demo
```

The script creates these secrets:

| Secret name | Value | Purpose |
|-------------|-------|---------|
| `db-credentials` | `{"username":"admin","password":"s3cret","host":"db.example.com","port":5432}` | JSON field extraction |
| `gcp-api-key` | `gcp-test-api-key-12345` | GCP-specific secret |
| `shared-secret` | `shared-value-from-gcp` | Multi-provider fallback |
| `nested-config` | Deeply nested JSON object | Dot-notation extraction |

### Option B: Create secrets manually

If you prefer to understand each step:

```bash
# Set your project ID
export GCP_PROJECT_ID=uni-secret-demo

# 1. db-credentials (JSON blob)
echo -n '{"username":"admin","password":"s3cret","host":"db.example.com","port":5432}' | \
  gcloud secrets create db-credentials \
    --project="$GCP_PROJECT_ID" \
    --data-file=- \
    --replication-policy="automatic"

# 2. gcp-api-key (plain string)
echo -n "gcp-test-api-key-12345" | \
  gcloud secrets create gcp-api-key \
    --project="$GCP_PROJECT_ID" \
    --data-file=- \
    --replication-policy="automatic"

# 3. shared-secret (for fallback chain testing)
echo -n "shared-value-from-gcp" | \
  gcloud secrets create shared-secret \
    --project="$GCP_PROJECT_ID" \
    --data-file=- \
    --replication-policy="automatic"

# 4. nested-config (deeply nested JSON)
echo -n '{"database":{"connection":{"host":"db.example.com","password":"nested-pass","port":5432}},"cache":{"redis":{"host":"redis.example.com","port":6379}}}' | \
  gcloud secrets create nested-config \
    --project="$GCP_PROJECT_ID" \
    --data-file=- \
    --replication-policy="automatic"
```

---

## 9. Verify the Setup

### List all secrets

```bash
gcloud secrets list --project=uni-secret-demo
```

Expected output:

```
NAME              CREATED              REPLICATION_POLICY  LOCATIONS
db-credentials    2026-01-29T...       automatic           -
gcp-api-key       2026-01-29T...       automatic           -
nested-config     2026-01-29T...       automatic           -
shared-secret     2026-01-29T...       automatic           -
```

### Read a secret value

```bash
gcloud secrets versions access latest --secret="gcp-api-key" --project=uni-secret-demo
# Should output: gcp-test-api-key-12345
```

### Verify ADC is working

```bash
gcloud auth application-default print-access-token
# Should print a long access token (not an error)
```

If all three checks pass, your GCP setup is complete.

---

## 10. Run the Demo Project

### Run the cloud tests

```bash
cd /Users/tim/Works/uni-secret-manager-demo

# Make sure GCP_PROJECT_ID is set
export GCP_PROJECT_ID=uni-secret-demo

# Run cloud-tier tests (requires GCP credentials + Docker for LocalStack)
make test-cloud
```

### Run the app with staging profile

```bash
# Start LocalStack first (for AWS emulation)
make docker-up

# Run the application
export GCP_PROJECT_ID=uni-secret-demo
make run-staging
```

Then open [http://localhost:8080/api/demo/all](http://localhost:8080/api/demo/all) to see all services resolving secrets from GCP.

---

## 11. Cleanup

When you're done testing, remove the secrets to avoid any charges (even though they're within the free tier):

```bash
./scripts/cleanup-gcp-secrets.sh uni-secret-demo
```

Or manually:

```bash
gcloud secrets delete db-credentials --project=uni-secret-demo --quiet
gcloud secrets delete gcp-api-key --project=uni-secret-demo --quiet
gcloud secrets delete shared-secret --project=uni-secret-demo --quiet
gcloud secrets delete nested-config --project=uni-secret-demo --quiet
```

To revoke local ADC credentials:

```bash
gcloud auth application-default revoke
```

---

## 12. Troubleshooting

### "Permission denied" when creating secrets

```
ERROR: (gcloud.secrets.create) PERMISSION_DENIED: Permission 'secretmanager.secrets.create' denied
```

**Fix:** Grant yourself the Secret Manager Admin role:

```bash
gcloud projects add-iam-policy-binding uni-secret-demo \
  --member="user:YOUR_EMAIL@gmail.com" \
  --role="roles/secretmanager.admin"
```

### "API not enabled" error

```
ERROR: (gcloud.secrets.create) PERMISSION_DENIED: Secret Manager API has not been used in project
```

**Fix:** Enable the API:

```bash
gcloud services enable secretmanager.googleapis.com --project=uni-secret-demo
```

### "Could not find default credentials" in Java

```
java.io.IOException: The Application Default Credentials are not available
```

**Fix:** Run ADC login:

```bash
gcloud auth application-default login
gcloud auth application-default set-quota-project uni-secret-demo
```

### "Billing account not found" or "Billing must be enabled"

**Fix:** Link a billing account to the project:

```bash
gcloud billing accounts list
gcloud billing projects link uni-secret-demo --billing-account=BILLING_ACCOUNT_ID
```

### Secret exists but test fails with "not found"

Possible causes:

1. **Wrong project ID** — check `echo $GCP_PROJECT_ID` matches your project
2. **Secret has no active version** — verify with:
   ```bash
   gcloud secrets versions list db-credentials --project=uni-secret-demo
   ```
3. **ADC credentials expired** — re-run:
   ```bash
   gcloud auth application-default login
   ```

### "Quota exceeded" errors

The free tier allows 10,000 access operations. If you're running tests in a loop:

```bash
# Check quota usage
gcloud services quota list --service=secretmanager.googleapis.com --project=uni-secret-demo
```

---

## 13. References

All documentation referenced in this guide comes from official Google Cloud sources:

### Google Cloud Account & Billing

- [Free Trial & Free Tier Overview](https://cloud.google.com/free) — $300 credits, 90-day trial, always-free products
- [Free Trial Features Documentation](https://docs.cloud.google.com/free/docs/free-cloud-features) — Detailed breakdown of free-tier limits
- [Free Trial FAQs](https://cloud.google.com/signup-faqs) — Common questions about the trial period

### gcloud CLI Installation

- [Quickstart: Install the gcloud CLI](https://docs.cloud.google.com/sdk/docs/install-sdk) — Official installation guide for all platforms
- [Install via Homebrew (macOS)](https://docs.cloud.google.com/sdk/docs/downloads-homebrew) — `brew install --cask gcloud-cli`
- [gcloud CLI Overview](https://docs.cloud.google.com/sdk/gcloud) — General reference and current version info
- [Homebrew Formulae: gcloud-cli](https://formulae.brew.sh/cask/gcloud-cli) — Homebrew cask details

### Authentication

- [How Application Default Credentials Works](https://docs.cloud.google.com/docs/authentication/application-default-credentials) — ADC search order and behavior
- [Set Up Application Default Credentials](https://docs.cloud.google.com/docs/authentication/provide-credentials-adc) — Step-by-step ADC setup
- [gcloud auth application-default login Reference](https://cloud.google.com/sdk/gcloud/reference/auth/application-default/login) — CLI command reference
- [Authenticate for Using the gcloud CLI](https://docs.cloud.google.com/docs/authentication/gcloud) — Difference between `gcloud auth login` and ADC

### Secret Manager

- [Secret Manager Documentation (main page)](https://docs.cloud.google.com/secret-manager/docs) — Entry point for all Secret Manager docs
- [Secret Manager Overview](https://docs.cloud.google.com/secret-manager/docs/overview) — Concepts, architecture, pricing
- [Create and Access a Secret (Quickstart)](https://docs.cloud.google.com/secret-manager/docs/create-secret-quickstart) — Step-by-step quickstart
- [Create a Secret](https://docs.cloud.google.com/secret-manager/docs/creating-and-accessing-secrets) — Detailed guide for creating secrets via Console, CLI, and API
- [Enable the Secret Manager API](https://docs.cloud.google.com/secret-manager/docs/configuring-secret-manager) — API enablement instructions

### IAM & Permissions

- [Access Control with IAM (Secret Manager)](https://docs.cloud.google.com/secret-manager/docs/access-control) — Roles, permissions, and best practices
- [Secret Manager Roles and Permissions Reference](https://docs.cloud.google.com/iam/docs/roles-permissions/secretmanager) — Complete IAM role listing
- [Manage Access to Secrets](https://docs.cloud.google.com/secret-manager/docs/manage-access-to-secrets) — Granting and revoking access
