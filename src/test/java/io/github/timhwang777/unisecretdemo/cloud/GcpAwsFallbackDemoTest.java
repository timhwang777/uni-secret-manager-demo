package io.github.timhwang777.unisecretdemo.cloud;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
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
 * Verifies the GCP -> AWS fallback chain: GCP is tried first (real),
 * and AWS (LocalStack) is used for secrets only available in AWS.
 * Requires GCP_PROJECT_ID env var and Application Default Credentials.
 */
@Tag("cloud")
@EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")
@Testcontainers
@SpringBootTest(classes = {GcpAwsFallbackDemoTest.FallbackTestBean.class, SecretManagerAutoConfiguration.class})
class GcpAwsFallbackDemoTest {

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
        registry.add("secrets.provider-order", () -> "gcp,aws");
        registry.add("secrets.fail-on-missing", () -> "true");
        registry.add("secrets.gcp.enabled", () -> "true");
        registry.add("secrets.gcp.project-id", () -> System.getenv("GCP_PROJECT_ID"));
        registry.add("secrets.aws.enabled", () -> "true");
        registry.add("secrets.aws.region", localstack::getRegion);
        registry.add("secrets.aws.endpoint", () ->
                localstack.getEndpointOverride(LocalStackContainer.Service.SECRETSMANAGER).toString());
    }

    static void seedAwsSecrets() {
        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SECRETSMANAGER))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion()))
                .build()) {

            // This secret only exists in AWS, not in GCP
            client.createSecret(CreateSecretRequest.builder()
                    .name("aws-only-secret")
                    .secretString("aws-only-value")
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
        // This secret exists in GCP — should come from GCP
        @SecretValue("gcp-api-key")
        private String gcpSecret;

        // This secret only exists in AWS — GCP miss, then AWS hit
        @SecretValue("aws-only-secret")
        private String awsFallbackSecret;

        public String getGcpSecret() { return gcpSecret; }
        public String getAwsFallbackSecret() { return awsFallbackSecret; }
    }

    @Autowired
    private FallbackTestBean testBean;

    @Test
    void shouldResolveFromGcpWhenAvailable() {
        assertThat(testBean.getGcpSecret()).isEqualTo("gcp-test-api-key-12345");
    }

    @Test
    void shouldFallbackToAwsWhenNotInGcp() {
        assertThat(testBean.getAwsFallbackSecret()).isEqualTo("aws-only-value");
    }
}
