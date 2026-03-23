package io.github.timhwang777.unisecretdemo.local;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import io.github.timhwang777.unisecret.config.VaultAutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the Vault → AWS → Local fallback chain using Docker only (no GCP credentials).
 * Exercises provider precedence, cross-provider fallback, and default values.
 */
@Tag("local")
@Testcontainers
@SpringBootTest(classes = {
        VaultAwsFallbackDemoTest.FallbackTestBean.class,
        SecretManagerAutoConfiguration.class,
        VaultAutoConfiguration.class
})
class VaultAwsFallbackDemoTest {

    private static final String VAULT_TOKEN = "vault-fallback-test-token";
    private static final DockerImageName VAULT_IMAGE = DockerImageName.parse("hashicorp/vault:1.21.4");
    private static final DockerImageName LOCALSTACK_IMAGE = DockerImageName.parse("localstack/localstack:4.12.0");

    @Container
    private static final VaultContainer<?> vault = new VaultContainer<>(VAULT_IMAGE)
            .withVaultToken(VAULT_TOKEN);

    @Container
    private static final LocalStackContainer localstack = new LocalStackContainer(LOCALSTACK_IMAGE)
            .withServices(LocalStackContainer.Service.SECRETSMANAGER);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (!vault.isRunning()) {
            vault.start();
        }

        seedVaultSecrets();
        seedAwsSecrets();
        System.setProperty("aws.accessKeyId", localstack.getAccessKey());
        System.setProperty("aws.secretAccessKey", localstack.getSecretKey());

        // Provider order: vault -> aws -> local
        registry.add("secrets.enabled", () -> "true");
        registry.add("secrets.provider-order", () -> "vault,aws,local");
        registry.add("secrets.fail-on-missing", () -> "true");

        // Vault configuration
        registry.add("secrets.vault.enabled", () -> "true");
        registry.add("secrets.vault.host", vault::getHost);
        registry.add("secrets.vault.port", () -> vault.getMappedPort(8200));
        registry.add("secrets.vault.scheme", () -> "http");
        registry.add("secrets.vault.auth-method", () -> "TOKEN");
        registry.add("secrets.vault.token", () -> VAULT_TOKEN);
        registry.add("secrets.vault.mount", () -> "secret");
        registry.add("secrets.vault.kv-version", () -> "2");

        // AWS (LocalStack) configuration
        registry.add("secrets.aws.enabled", () -> "true");
        registry.add("secrets.aws.region", localstack::getRegion);
        registry.add("secrets.aws.endpoint", () ->
                localstack.getEndpointOverride(LocalStackContainer.Service.SECRETSMANAGER).toString());

        // Local fallback
        registry.add("secrets.local.enabled", () -> "true");
        registry.add("secrets.local.secrets.local-only-secret", () -> "local-only-value");
    }

    static void seedVaultSecrets() {
        try {
            // Shared secret — also seeded in AWS to test precedence
            vault.execInContainer("vault", "kv", "put", "secret/shared-secret", "value=vault-shared-value");
            // Vault-exclusive secret
            vault.execInContainer("vault", "kv", "put", "secret/vault-exclusive", "value=vault-exclusive-value");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to seed Vault secrets", e);
        }
    }

    static void seedAwsSecrets() {
        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SECRETSMANAGER))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion()))
                .build()) {

            // Shared secret — also in Vault; Vault should win
            client.createSecret(CreateSecretRequest.builder()
                    .name("shared-secret")
                    .secretString("aws-shared-value")
                    .build());

            // AWS-exclusive secret — not in Vault
            client.createSecret(CreateSecretRequest.builder()
                    .name("aws-exclusive")
                    .secretString("aws-exclusive-value")
                    .build());
        }
    }

    @AfterAll
    static void cleanupSystemProperties() {
        System.clearProperty("aws.accessKeyId");
        System.clearProperty("aws.secretAccessKey");
    }

    @Component
    static class FallbackTestBean {

        // In both Vault and AWS — Vault should win (first in provider order)
        @SecretValue(value = "shared-secret", providers = {"vault", "aws"}, version = "1", field = "value")
        private String vaultPreferred;

        // Only in AWS — Vault miss, then AWS hit
        @SecretValue("aws-exclusive")
        private String awsFallback;

        // Only in Vault — explicit provider override
        @SecretValue(value = "vault-exclusive", provider = "vault", version = "1", field = "value")
        private String vaultExclusive;

        // Only in local — both Vault and AWS miss
        @SecretValue("local-only-secret")
        private String localFallback;

        // Not in any provider — uses default
        @SecretValue(value = "phantom-secret", defaultValue = "phantom-default")
        private String withDefault;

        public String getVaultPreferred() { return vaultPreferred; }
        public String getAwsFallback() { return awsFallback; }
        public String getVaultExclusive() { return vaultExclusive; }
        public String getLocalFallback() { return localFallback; }
        public String getWithDefault() { return withDefault; }
    }

    @Autowired
    private FallbackTestBean testBean;

    @Test
    void shouldPreferVaultOverAws() {
        assertThat(testBean.getVaultPreferred()).isEqualTo("vault-shared-value");
    }

    @Test
    void shouldFallbackToAwsWhenNotInVault() {
        assertThat(testBean.getAwsFallback()).isEqualTo("aws-exclusive-value");
    }

    @Test
    void shouldResolveVaultExclusiveSecret() {
        assertThat(testBean.getVaultExclusive()).isEqualTo("vault-exclusive-value");
    }

    @Test
    void shouldFallbackToLocalWhenNotInVaultOrAws() {
        assertThat(testBean.getLocalFallback()).isEqualTo("local-only-value");
    }

    @Test
    void shouldUseDefaultWhenNotInAnyProvider() {
        assertThat(testBean.getWithDefault()).isEqualTo("phantom-default");
    }
}
