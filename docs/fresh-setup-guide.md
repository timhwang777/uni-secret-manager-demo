# Fresh Setup Guide: uni-secret-manager-demo

This guide covers how to set up the demo project from scratch, pulling the `uni-secret-manager-spring-boot-starter` plugin from GitHub Packages.

> If you already have the plugin built locally and want to migrate to remote packages, see [Migration Guide](migration-from-local-to-remote-packages.md).

## Prerequisites

- Java 21
- Maven 3.8+
- A GitHub account with access to the private plugin repository

## Step 1: Create a GitHub Personal Access Token (PAT)

The plugin is hosted on a **private** GitHub Packages registry. You need a PAT to authenticate.

1. Go to **GitHub > Settings > Developer settings > Personal access tokens > Tokens (classic)**
2. Click **Generate new token**
3. Select the **`read:packages`** scope
4. Copy the token

## Step 2: Configure Maven credentials

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

> **Important:** Never commit this file to version control.

## Step 3: Clone and build the demo

```bash
git clone https://github.com/timhwang777/uni-secret-manager-demo.git
cd uni-secret-manager-demo
mvn clean install
```

Maven will pull `uni-secret-manager-spring-boot-starter` from GitHub Packages automatically using the `<repositories>` block already configured in `pom.xml`.

## Step 4: Run the application

```bash
mvn spring-boot:run
```

## CI/CD with GitHub Actions

For CI environments, store a PAT with `read:packages` scope as a repository secret (e.g., `READ_PACKAGES_PAT`), then use a custom Maven settings file.

Create `.github/maven-settings.xml`:

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

In your workflow:

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

> **Note:** Since the plugin repository is **private**, the default `GITHUB_TOKEN` may not have permission to read packages from another repo. Use a PAT with `read:packages` scope.
