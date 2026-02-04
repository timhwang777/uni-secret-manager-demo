package io.github.timhwang777.unisecretdemo.local;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that default values are used when secrets are not found in any provider.
 */
@Tag("local")
@SpringBootTest(classes = {DefaultValueDemoTest.DefaultValueTestBean.class, SecretManagerAutoConfiguration.class})
@TestPropertySource(properties = {
        "secrets.enabled=true",
        "secrets.provider-order=local",
        "secrets.fail-on-missing=true",
        "secrets.local.enabled=true",
        "secrets.local.secrets.existing-secret=real-value"
})
class DefaultValueDemoTest {

    @Component
    static class DefaultValueTestBean {
        @SecretValue(value = "non-existent-secret", defaultValue = "my-default")
        private String withDefault;

        @SecretValue("existing-secret")
        private String existing;

        public String getWithDefault() { return withDefault; }
        public String getExisting() { return existing; }
    }

    @Autowired
    private DefaultValueTestBean testBean;

    @Test
    void shouldUseDefaultValueWhenSecretNotFound() {
        assertThat(testBean.getWithDefault()).isEqualTo("my-default");
    }

    @Test
    void shouldUseActualValueWhenSecretExists() {
        assertThat(testBean.getExisting()).isEqualTo("real-value");
    }
}
