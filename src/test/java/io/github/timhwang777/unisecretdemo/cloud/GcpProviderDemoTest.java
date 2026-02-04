package io.github.timhwang777.unisecretdemo.cloud;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.provider.GcpSecretProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies secret resolution against a real GCP Secret Manager.
 * Requires GCP_PROJECT_ID env var and Application Default Credentials.
 */
@Tag("cloud")
@EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")
class GcpProviderDemoTest {

    private GcpSecretProvider createProvider() throws Exception {
        String projectId = System.getenv("GCP_PROJECT_ID");

        SecretManagerProperties.Gcp gcpProps = new SecretManagerProperties.Gcp();
        gcpProps.setEnabled(true);
        gcpProps.setProjectId(projectId);

        SecretManagerServiceClient client = SecretManagerServiceClient.create();
        return new GcpSecretProvider(client, gcpProps);
    }

    @Test
    void shouldRetrievePlainSecretFromGcp() throws Exception {
        GcpSecretProvider provider = createProvider();
        Optional<String> result = provider.getSecret("gcp-api-key");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("gcp-test-api-key-12345");
    }

    @Test
    void shouldRetrieveJsonSecretFromGcp() throws Exception {
        GcpSecretProvider provider = createProvider();
        Optional<String> result = provider.getSecret("db-credentials");

        assertThat(result).isPresent();
        assertThat(result.get()).contains("username");
        assertThat(result.get()).contains("password");
    }

    @Test
    void shouldReturnEmptyForNonExistentSecret() throws Exception {
        GcpSecretProvider provider = createProvider();
        Optional<String> result = provider.getSecret("non-existent-secret-" + System.currentTimeMillis());

        assertThat(result).isEmpty();
    }
}
