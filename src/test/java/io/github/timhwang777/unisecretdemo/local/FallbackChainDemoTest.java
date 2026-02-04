package io.github.timhwang777.unisecretdemo.local;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies fallback from an unavailable provider to the local provider.
 * AWS is listed first in provider-order but is disabled, so local should be used.
 */
@Tag("local")
@SpringBootTest(classes = {FallbackChainDemoTest.FallbackTestBean.class, SecretManagerAutoConfiguration.class})
@TestPropertySource(properties = {
        "secrets.enabled=true",
        "secrets.provider-order=aws,local",
        "secrets.fail-on-missing=true",
        "secrets.aws.enabled=false",
        "secrets.local.enabled=true",
        "secrets.local.secrets.fallback-secret=local-fallback-value"
})
class FallbackChainDemoTest {

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
