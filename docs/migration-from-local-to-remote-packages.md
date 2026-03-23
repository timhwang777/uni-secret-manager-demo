# Migration Guide: Local Development to Remote Packages

This guide covers how to set up the `uni-secret-manager-spring-boot-starter` plugin for **local development**, and then how to **migrate to consuming it from a remote repository** when you're ready.

## Prerequisites

- Java 21
- Maven 3.8+

## Part 1: Local Development Setup

If you have both the plugin repo (`uni-secret-manager-spring-boot-starter`) and this demo repo cloned locally, you can develop against a local build of the plugin with zero extra configuration.

### 1. Build and install the plugin locally

```bash
cd /path/to/uni-secret-manager-spring-boot-starter
mvn clean install
```

This installs the artifact (`io.github.timhwang777:uni-secret-manager-spring-boot-starter:1.0.0-SNAPSHOT`) into your local Maven cache at `~/.m2/repository`.

### 2. Build the demo project

```bash
cd /path/to/uni-secret-manager-demo
mvn clean install
```

Maven resolves the dependency from `~/.m2` automatically. No `<repositories>` block or credentials needed.

> **Tip:** Make sure both projects use the same version. If you bump the version in the plugin, update the demo's `pom.xml` to match.

### When to use local setup

- You're actively developing or debugging the plugin alongside the demo
- You want fast iteration without publishing to a remote registry

## Part 2: Using JitPack (Recommended)

The demo is pre-configured to resolve the starter from [JitPack](https://jitpack.io). No authentication or extra setup is needed.

The `pom.xml` already includes:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

JitPack builds the starter directly from the GitHub repository. The version in `pom.xml` uses `main-SNAPSHOT` to track the latest `main` branch.

### Verify remote resolution

Delete the locally cached artifact to ensure Maven pulls from JitPack:

```bash
rm -rf ~/.m2/repository/io/github/timhwang777/uni-secret-manager-spring-boot-starter
mvn clean compile
```

You should see Maven downloading from `https://jitpack.io/...` in the output.

### JitPack version formats

| Version | Resolves to |
|---|---|
| `main-SNAPSHOT` | Latest commit on `main` branch |
| `v1.0.0` | Git tag `v1.0.0` |
| `abc1234` | Specific commit hash |

## Part 3: Alternative - GitHub Packages

If you prefer GitHub Packages over JitPack, you can switch the repository configuration. Note that GitHub Packages **requires authentication** even for public packages.

### Step 1: Create a GitHub Personal Access Token (PAT)

1. Go to **GitHub > Settings > Developer settings > Personal access tokens > Tokens (classic)**
2. Click **Generate new token**
3. Select the **`read:packages`** scope
4. Copy the token

### Step 2: Configure Maven credentials

Edit (or create) `~/.m2/settings.xml`:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_PAT</password>
    </server>
  </servers>
</settings>
```

Replace `YOUR_GITHUB_USERNAME` and `YOUR_GITHUB_PAT` with your actual values.

> **Important:** The `<id>github</id>` must match the repository `<id>` in the next step. Never commit this file to version control.

### Step 3: Replace the JitPack repository in `pom.xml`

Replace the JitPack `<repositories>` block with:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/timhwang777/uni-secret-manager-spring-boot-starter</url>
  </repository>
</repositories>
```

### Step 4: Verify remote resolution

```bash
rm -rf ~/.m2/repository/io/github/timhwang777/uni-secret-manager-spring-boot-starter
mvn clean compile
```

You should see Maven downloading from `https://maven.pkg.github.com/...` in the output.

## Part 4: CI/CD with GitHub Actions

### Using JitPack (no extra config needed)

JitPack requires no authentication, so your CI workflow just works:

```yaml
- uses: actions/setup-java@v4
  with:
    java-version: '21'
    distribution: 'temurin'

- name: Build
  run: mvn clean compile
```

### Using GitHub Packages

For CI environments, store a PAT with `read:packages` scope as a repository secret (e.g., `READ_PACKAGES_PAT`).

Create `.github/maven-settings.xml` in the demo repo:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>${env.GITHUB_ACTOR}</username>
      <password>${env.GITHUB_TOKEN}</password>
    </server>
  </servers>
</settings>
```

```yaml
- uses: actions/setup-java@v4
  with:
    java-version: '21'
    distribution: 'temurin'

- name: Build
  run: mvn clean install -s $GITHUB_WORKSPACE/.github/maven-settings.xml
  env:
    GITHUB_ACTOR: ${{ github.actor }}
    GITHUB_TOKEN: ${{ secrets.READ_PACKAGES_PAT }}
```

> **Note:** Since the plugin repository is **private**, the default `GITHUB_TOKEN` may not have permission to read packages from another repo. Use a PAT with `read:packages` scope stored as a repository secret (e.g., `READ_PACKAGES_PAT`).
