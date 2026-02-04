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
 * Verifies JSON field extraction including nested dot-notation paths.
 */
@Tag("local")
@SpringBootTest(classes = {JsonFieldExtractionDemoTest.JsonTestBean.class, SecretManagerAutoConfiguration.class})
@TestPropertySource(properties = {
        "secrets.enabled=true",
        "secrets.provider-order=local",
        "secrets.fail-on-missing=true",
        "secrets.local.enabled=true",
        "secrets.local.secrets.flat-json={\"username\":\"admin\",\"password\":\"flat-pass\"}",
        "secrets.local.secrets.nested-json={\"database\":{\"connection\":{\"host\":\"db.test\",\"password\":\"nested-pass\",\"port\":5432}}}"
})
class JsonFieldExtractionDemoTest {

    @Component
    static class JsonTestBean {
        @SecretValue(value = "flat-json", field = "username")
        private String flatUsername;

        @SecretValue(value = "flat-json", field = "password")
        private String flatPassword;

        @SecretValue(value = "nested-json", field = "database.connection.host")
        private String nestedHost;

        @SecretValue(value = "nested-json", field = "database.connection.password")
        private String nestedPassword;

        public String getFlatUsername() { return flatUsername; }
        public String getFlatPassword() { return flatPassword; }
        public String getNestedHost() { return nestedHost; }
        public String getNestedPassword() { return nestedPassword; }
    }

    @Autowired
    private JsonTestBean testBean;

    @Test
    void shouldExtractFlatJsonField() {
        assertThat(testBean.getFlatUsername()).isEqualTo("admin");
        assertThat(testBean.getFlatPassword()).isEqualTo("flat-pass");
    }

    @Test
    void shouldExtractNestedJsonField() {
        assertThat(testBean.getNestedHost()).isEqualTo("db.test");
        assertThat(testBean.getNestedPassword()).isEqualTo("nested-pass");
    }
}
