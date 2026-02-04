package io.github.timhwang777.unisecretdemo.cloud;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies JSON field extraction from real GCP secrets.
 * Requires GCP_PROJECT_ID env var and Application Default Credentials.
 */
@Tag("cloud")
@EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")
@SpringBootTest(classes = {GcpJsonFieldDemoTest.GcpJsonTestBean.class, SecretManagerAutoConfiguration.class})
@ActiveProfiles("test-cloud")
class GcpJsonFieldDemoTest {

    @Component
    static class GcpJsonTestBean {
        @SecretValue(value = "db-credentials", field = "password")
        private String dbPassword;

        @SecretValue(value = "db-credentials", field = "username")
        private String dbUsername;

        @SecretValue(value = "nested-config", field = "database.connection.password")
        private String nestedPassword;

        @SecretValue(value = "nested-config", field = "cache.redis.host")
        private String redisHost;

        public String getDbPassword() { return dbPassword; }
        public String getDbUsername() { return dbUsername; }
        public String getNestedPassword() { return nestedPassword; }
        public String getRedisHost() { return redisHost; }
    }

    @Autowired
    private GcpJsonTestBean testBean;

    @Test
    void shouldExtractFlatJsonFieldFromGcp() {
        assertThat(testBean.getDbUsername()).isEqualTo("admin");
        assertThat(testBean.getDbPassword()).isEqualTo("s3cret");
    }

    @Test
    void shouldExtractNestedJsonFieldFromGcp() {
        assertThat(testBean.getNestedPassword()).isEqualTo("nested-pass");
        assertThat(testBean.getRedisHost()).isEqualTo("redis.example.com");
    }
}
