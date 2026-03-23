package io.github.timhwang777.unisecretdemo.local;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import io.github.timhwang777.unisecret.provider.ProviderType;
import io.github.timhwang777.unisecret.provider.SecretProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies fallback from an unavailable provider to the local provider.
 * AWS is listed first in provider-order, but the test provides a disabled AWS provider bean,
 * so local should be used.
 */
@Tag("local")
@SpringBootTest(classes = {
        FallbackChainDemoTest.FallbackTestBean.class,
        FallbackChainDemoTest.DisabledAwsProviderConfig.class,
        SecretManagerAutoConfiguration.class
})
@TestPropertySource(properties = {
        "secrets.enabled=true",
        "secrets.provider-order=aws,local",
        "secrets.fail-on-missing=true",
        "secrets.local.enabled=true",
        "secrets.local.secrets.fallback-secret=local-fallback-value"
})
class FallbackChainDemoTest {

    @Configuration
    static class DisabledAwsProviderConfig {
        @Bean
        SecretProvider disabledAwsProvider() {
            return new SecretProvider() {
                @Override
                public Optional<String> getSecret(String key) {
                    return Optional.empty();
                }

                @Override
                public Optional<String> getSecret(String key, String version) {
                    return Optional.empty();
                }

                @Override
                public ProviderType getProviderType() {
                    return ProviderType.AWS;
                }

                @Override
                public void validateConfiguration() {
                }

                @Override
                public boolean isEnabled() {
                    return false;
                }
            };
        }
    }

    @Component
    static class FallbackTestBean {
        @SecretValue("fallback-secret")
        private String secret;

        public String getSecret() { return secret; }
    }

    @Autowired
    private FallbackTestBean testBean;

    @Test
    void shouldFallbackToLocalWhenAwsIsDisabled() {
        assertThat(testBean.getSecret()).isEqualTo("local-fallback-value");
    }
}
