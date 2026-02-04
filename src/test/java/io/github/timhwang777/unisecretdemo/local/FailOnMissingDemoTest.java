package io.github.timhwang777.unisecretdemo.local;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the application fails to start when a required secret is missing
 * and fail-on-missing is true.
 */
@Tag("local")
class FailOnMissingDemoTest {

    @SpringBootApplication
    @org.springframework.context.annotation.Import(SecretManagerAutoConfiguration.class)
    static class FailOnMissingApp {
        @Component
        static class MissingSecretBean {
            @SecretValue("this-secret-does-not-exist")
            private String missing;
        }
    }

    @Test
    void shouldFailStartupWhenRequiredSecretIsMissing() {
        SpringApplication app = new SpringApplication(FailOnMissingApp.class);
        app.setDefaultProperties(java.util.Map.of(
                "secrets.enabled", "true",
                "secrets.provider-order", "local",
                "secrets.fail-on-missing", "true",
                "secrets.local.enabled", "true",
                "spring.main.web-application-type", "none"
        ));

        assertThatThrownBy(() -> app.run())
                .rootCause()
                .isInstanceOf(io.github.timhwang777.unisecret.exception.SecretNotFoundException.class)
                .hasMessageContaining("this-secret-does-not-exist");
    }
}
