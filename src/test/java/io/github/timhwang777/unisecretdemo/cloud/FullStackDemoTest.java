package io.github.timhwang777.unisecretdemo.cloud;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import io.github.timhwang777.unisecret.service.SecretRefreshService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full end-to-end test with all providers: GCP (real) + AWS (LocalStack) + Local.
 * Exercises JSON extraction, fallback chains, default values, and cache refresh.
 * Requires GCP_PROJECT_ID env var and Application Default Credentials.
 */
@Tag("cloud")
@EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")
@Testcontainers
@SpringBootTest(classes = {FullStackDemoTest.FullTestBean.class, SecretManagerAutoConfiguration.class})
class FullStackDemoTest {

    private static final DockerImageName LOCALSTACK_IMAGE = DockerImageName.parse("localstack/localstack:4.12.0");

    @Container
    private static final LocalStackContainer localstack = new LocalStackContainer(LOCALSTACK_IMAGE)
            .withServices(LocalStackContainer.Service.SECRETSMANAGER);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        seedAwsSecrets();
        System.setProperty("aws.accessKeyId", localstack.getAccessKey());
        System.setProperty("aws.secretAccessKey", localstack.getSecretKey());

        registry.add("secrets.enabled", () -> "true");
        registry.add("secrets.provider-order", () -> "gcp,aws,local");
        registry.add("secrets.fail-on-missing", () -> "true");
        registry.add("secrets.gcp.enabled", () -> "true");
        registry.add("secrets.gcp.project-id", () -> System.getenv("GCP_PROJECT_ID"));
        registry.add("secrets.aws.enabled", () -> "true");
        registry.add("secrets.aws.region", localstack::getRegion);
        registry.add("secrets.aws.endpoint", () ->
                localstack.getEndpointOverride(LocalStackContainer.Service.SECRETSMANAGER).toString());
        registry.add("secrets.local.enabled", () -> "true");
        registry.add("secrets.local.secrets.local-only-secret", () -> "local-only-value");
        registry.add("secrets.cache.enabled", () -> "true");
        registry.add("secrets.cache.ttl", () -> "1m");
    }

    static void seedAwsSecrets() {
        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SECRETSMANAGER))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion()))
                .build()) {

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
    static class FullTestBean {
        // GCP primary — JSON field extraction
        @SecretValue(value = "db-credentials", field = "password")
        private String dbPasswordFromGcp;

        // GCP primary — nested JSON extraction
        @SecretValue(value = "nested-config", field = "database.connection.host")
        private String nestedHostFromGcp;

        // AWS fallback — only in LocalStack
        @SecretValue("aws-exclusive")
        private String awsExclusive;

        // Local fallback — only in local
        @SecretValue("local-only-secret")
        private String localOnly;

        // Default value — not in any provider
        @SecretValue(value = "phantom-secret", defaultValue = "phantom-default")
        private String withDefault;

        public String getDbPasswordFromGcp() { return dbPasswordFromGcp; }
        public String getNestedHostFromGcp() { return nestedHostFromGcp; }
        public String getAwsExclusive() { return awsExclusive; }
        public String getLocalOnly() { return localOnly; }
        public String getWithDefault() { return withDefault; }
    }

    @Autowired
    private FullTestBean testBean;

    @Autowired
    private SecretRefreshService refreshService;

    @Test
    void shouldResolveJsonFieldFromGcp() {
        assertThat(testBean.getDbPasswordFromGcp()).isEqualTo("s3cret");
    }

    @Test
    void shouldResolveNestedJsonFromGcp() {
        assertThat(testBean.getNestedHostFromGcp()).isEqualTo("db.example.com");
    }

    @Test
    void shouldFallbackToAwsWhenNotInGcp() {
        assertThat(testBean.getAwsExclusive()).isEqualTo("aws-exclusive-value");
    }

    @Test
    void shouldFallbackToLocalWhenNotInGcpOrAws() {
        assertThat(testBean.getLocalOnly()).isEqualTo("local-only-value");
    }

    @Test
    void shouldUseDefaultWhenNotInAnyProvider() {
        assertThat(testBean.getWithDefault()).isEqualTo("phantom-default");
    }

    @Test
    void refreshServiceShouldBeAvailable() {
        assertThat(refreshService).isNotNull();
        refreshService.refreshAll();
        assertThat(refreshService.getRefreshStats().fullRefreshCount()).isGreaterThanOrEqualTo(1);
    }
}
