package io.github.timhwang777.unisecretdemo.local;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import io.github.timhwang777.unisecret.exception.SecretConfigurationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that startup fails fast when a bean references a provider that is not
 * configured in application settings.
 */
@Tag("local")
class FailFastOnUnconfiguredProviderDemoTest {

    @SpringBootApplication
    @org.springframework.context.annotation.Import(SecretManagerAutoConfiguration.class)
    static class UnconfiguredProviderApp {
        @Component
        static class MissingProviderBean {
            @SecretValue(value = "aws-only-secret", provider = "aws")
            private String secret;
        }
    }

    @Test
    void shouldFailStartupWhenAnnotationReferencesUnconfiguredProvider() {
        SpringApplication app = new SpringApplication(UnconfiguredProviderApp.class);
        app.setDefaultProperties(java.util.Map.of(
                "secrets.enabled", "true",
                "secrets.provider-order", "local",
                "secrets.fail-on-missing", "true",
                "secrets.local.enabled", "true",
                "secrets.local.secrets.aws-only-secret", "local-value-that-should-not-be-used",
                "spring.main.web-application-type", "none"
        ));

        assertThatThrownBy(app::run)
                .rootCause()
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("Provider 'aws' is referenced but not configured");
    }
}
