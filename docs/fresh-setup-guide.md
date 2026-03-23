# Fresh Setup Guide: uni-secret-manager-demo

This guide covers how to set up the demo project from scratch. The starter dependency is resolved automatically from [JitPack](https://jitpack.io) — no authentication or extra configuration needed.

> If you want to use GitHub Packages instead, or need local development setup, see [Migration Guide](migration-from-local-to-remote-packages.md).

## Prerequisites

- Java 21
- Maven 3.8+

## Step 1: Clone and build the demo

```bash
git clone https://github.com/timhwang777/uni-secret-manager-demo.git
cd uni-secret-manager-demo
mvn clean compile
```

Maven will pull `uni-secret-manager-spring-boot-starter` from JitPack automatically using the `<repositories>` block already configured in `pom.xml`.

## Step 2: Run the application

```bash
mvn spring-boot:run
```

## CI/CD with GitHub Actions

JitPack requires no authentication, so CI workflows need no extra configuration:

```yaml
- uses: actions/setup-java@v4
  with:
    java-version: '21'
    distribution: 'temurin'

- name: Build
  run: mvn clean compile
```

> For GitHub Packages as an alternative (requires PAT authentication), see the [Migration Guide](migration-from-local-to-remote-packages.md#part-3-alternative---github-packages).
