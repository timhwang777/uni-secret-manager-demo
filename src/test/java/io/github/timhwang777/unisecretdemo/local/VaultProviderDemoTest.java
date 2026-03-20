package io.github.timhwang777.unisecretdemo.local;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import io.github.timhwang777.unisecret.config.VaultAutoConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Vault integration using a local Docker-based dev container.
 * Covers provider precedence, JSON field extraction, KV v2 version reads, and local fallback.
 */
@Tag("local")
@Testcontainers
@SpringBootTest(classes = {
        VaultProviderDemoTest.VaultTestBean.class,
        SecretManagerAutoConfiguration.class,
        VaultAutoConfiguration.class
})
class VaultProviderDemoTest {

    private static final String VAULT_TOKEN = "vault-demo-token";
    private static final DockerImageName VAULT_IMAGE = DockerImageName.parse("hashicorp/vault:1.21.4");

    @Container
    private static final VaultContainer<?> vault = new VaultContainer<>(VAULT_IMAGE)
            .withVaultToken(VAULT_TOKEN);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (!vault.isRunning()) {
            vault.start();
        }

        // Seed test data before Spring resolves @SecretValue fields.
        seedInitialSecrets();
        seedVersionedSecret();

        registry.add("secrets.enabled", () -> "true");
        registry.add("secrets.provider-order", () -> "vault,local");
        registry.add("secrets.fail-on-missing", () -> "true");

        registry.add("secrets.vault.enabled", () -> "true");
        registry.add("secrets.vault.host", vault::getHost);
        registry.add("secrets.vault.port", () -> vault.getMappedPort(8200));
        registry.add("secrets.vault.scheme", () -> "http");
        registry.add("secrets.vault.auth-method", () -> "TOKEN");
        registry.add("secrets.vault.token", () -> VAULT_TOKEN);
        registry.add("secrets.vault.mount", () -> "secret");
        registry.add("secrets.vault.kv-version", () -> "2");

        registry.add("secrets.local.enabled", () -> "true");
        registry.add("secrets.local.secrets.local-only-secret", () -> "local-only-value");
        registry.add("secrets.local.secrets.shared-secret", () -> "local-shared-value");
    }

    static void seedVersionedSecret() {
        try {
            vault.execInContainer("vault", "kv", "put", "secret/rotating-secret", "value=vault-version-1");
            vault.execInContainer("vault", "kv", "put", "secret/rotating-secret", "value=vault-version-2");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to seed versioned Vault secret", e);
        }
    }

    static void seedInitialSecrets() {
        try {
            vault.execInContainer("vault", "kv", "put", "secret/vault-plain-secret", "value=vault-plain-value");
            vault.execInContainer("vault", "kv", "put", "secret/vault-json-secret",
                    "username=vault-admin", "password=vault-s3cret");
            vault.execInContainer("vault", "kv", "put", "secret/shared-secret", "value=vault-shared-value");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to seed initial Vault secrets", e);
        }
    }

    @Component
    static class VaultTestBean {

        @SecretValue(value = "vault-plain-secret", provider = "vault", version = "1", field = "value")
        private String vaultPlain;

        @SecretValue(value = "vault-json-secret", provider = "vault", version = "1", field = "password")
        private String vaultJsonPassword;

        @SecretValue(value = "rotating-secret", provider = "vault", version = "2", field = "value")
        private String vaultVersionTwo;

        // Version is set explicitly to keep KV v2 reads deterministic in this demo.
        @SecretValue(value = "local-only-secret", version = "1")
        private String localFallback;

        @SecretValue(value = "shared-secret", providers = {"vault", "local"}, version = "1", field = "value")
        private String vaultPreferred;

        public String getVaultPlain() {
            return vaultPlain;
        }

        public String getVaultJsonPassword() {
            return vaultJsonPassword;
        }

        public String getVaultVersionTwo() {
            return vaultVersionTwo;
        }

        public String getLocalFallback() {
            return localFallback;
        }

        public String getVaultPreferred() {
            return vaultPreferred;
        }
    }

    @Autowired
    private VaultTestBean testBean;

    @Test
    void shouldResolvePlainSecretFromVault() {
        assertThat(testBean.getVaultPlain()).isEqualTo("vault-plain-value");
    }

    @Test
    void shouldExtractJsonFieldFromVault() {
        assertThat(testBean.getVaultJsonPassword()).isEqualTo("vault-s3cret");
    }

    @Test
    void shouldResolveSpecificVaultKvVersion() {
        assertThat(testBean.getVaultVersionTwo()).isEqualTo("vault-version-2");
    }

    @Test
    void shouldFallbackToLocalWhenSecretNotInVault() {
        assertThat(testBean.getLocalFallback()).isEqualTo("local-only-value");
    }

    @Test
    void shouldPreferVaultWhenSecretExistsInVaultAndLocal() {
        assertThat(testBean.getVaultPreferred()).isEqualTo("vault-shared-value");
    }
}
