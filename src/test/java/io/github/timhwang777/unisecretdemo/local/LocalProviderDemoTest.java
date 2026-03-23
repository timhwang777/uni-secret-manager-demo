package io.github.timhwang777.unisecretdemo.local;

import io.github.timhwang777.unisecretdemo.service.DatabaseService;
import io.github.timhwang777.unisecretdemo.service.FeatureFlagService;
import io.github.timhwang777.unisecretdemo.service.NestedConfigService;
import io.github.timhwang777.unisecretdemo.service.VersionedSecretService;
import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies secrets resolve correctly from the local provider.
 */
@Tag("local")
@SpringBootTest(classes = {
        DatabaseService.class,
        FeatureFlagService.class,
        VersionedSecretService.class,
        NestedConfigService.class,
        SecretManagerAutoConfiguration.class
})
@ActiveProfiles("test")
class LocalProviderDemoTest {

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private FeatureFlagService featureFlagService;

    @Autowired
    private VersionedSecretService versionedSecretService;

    @Autowired
    private NestedConfigService nestedConfigService;

    @Test
    void databaseServiceShouldBeConfigured() {
        assertThat(databaseService.isConfigured()).isTrue();
        assertThat(databaseService.getConnectionSummary()).contains("test-admin");
        assertThat(databaseService.getConnectionSummary()).contains("test-db.local");
    }

    @Test
    void featureFlagShouldResolveFromLocal() {
        assertThat(featureFlagService.getFeatureFlag()).isEqualTo("enabled-in-test");
        assertThat(featureFlagService.isFeatureEnabled()).isTrue();
    }

    @Test
    void versionedSecretShouldResolveFromLocal() {
        assertThat(versionedSecretService.isConfigured()).isTrue();
    }

    @Test
    void nestedConfigShouldResolveFromLocal() {
        assertThat(nestedConfigService.isConfigured()).isTrue();
        assertThat(nestedConfigService.getSummary()).contains("test-db.local");
        assertThat(nestedConfigService.getSummary()).contains("test-redis.local");
    }
}
