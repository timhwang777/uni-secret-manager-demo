package io.github.timhwang777.unisecretdemo.local;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
 * Verifies AWS Secrets Manager integration via LocalStack + Testcontainers.
 */
@Tag("local")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = {AwsProviderDemoTest.AwsTestBean.class, SecretManagerAutoConfiguration.class})
class AwsProviderDemoTest {

    private static final DockerImageName LOCALSTACK_IMAGE = DockerImageName.parse("localstack/localstack:4.12.0");

    @Container
    private static final LocalStackContainer localstack = new LocalStackContainer(LOCALSTACK_IMAGE)
            .withServices(LocalStackContainer.Service.SECRETSMANAGER);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Seed secrets and set credentials BEFORE Spring context loads
        seedSecrets();
        System.setProperty("aws.accessKeyId", localstack.getAccessKey());
        System.setProperty("aws.secretAccessKey", localstack.getSecretKey());

        registry.add("secrets.enabled", () -> "true");
        registry.add("secrets.provider-order", () -> "aws");
        registry.add("secrets.fail-on-missing", () -> "true");
        registry.add("secrets.aws.enabled", () -> "true");
        registry.add("secrets.aws.region", localstack::getRegion);
        registry.add("secrets.aws.endpoint", () ->
                localstack.getEndpointOverride(LocalStackContainer.Service.SECRETSMANAGER).toString());
    }

    static void seedSecrets() {
        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SECRETSMANAGER))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.of(localstack.getRegion()))
                .build()) {

            client.createSecret(CreateSecretRequest.builder()
                    .name("aws-plain-secret")
                    .secretString("aws-plain-value")
                    .build());

            client.createSecret(CreateSecretRequest.builder()
                    .name("aws-json-secret")
                    .secretString("{\"username\":\"aws-admin\",\"password\":\"aws-s3cret\"}")
                    .build());
        }
    }

    @AfterAll
    static void cleanupSystemProperties() {
        System.clearProperty("aws.accessKeyId");
        System.clearProperty("aws.secretAccessKey");
    }

    @Component
    static class AwsTestBean {
        @SecretValue("aws-plain-secret")
        private String plainSecret;

        @SecretValue(value = "aws-json-secret", field = "password")
        private String jsonPassword;

        public String getPlainSecret() { return plainSecret; }
        public String getJsonPassword() { return jsonPassword; }
    }

    @Autowired
    private AwsTestBean testBean;

    @Test
    void shouldResolvePlainSecretFromLocalStack() {
        assertThat(testBean.getPlainSecret()).isEqualTo("aws-plain-value");
    }

    @Test
    void shouldResolveJsonFieldFromLocalStack() {
        assertThat(testBean.getJsonPassword()).isEqualTo("aws-s3cret");
    }
}
