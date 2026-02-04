package io.github.timhwang777.unisecretdemo.local;

import io.github.timhwang777.unisecret.cache.SecretCache;
import io.github.timhwang777.unisecret.config.SecretManagerAutoConfiguration;
import io.github.timhwang777.unisecret.service.SecretRefreshService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies cache invalidation via SecretRefreshService.
 */
@Tag("local")
@SpringBootTest(classes = SecretManagerAutoConfiguration.class)
@TestPropertySource(properties = {
        "secrets.enabled=true",
        "secrets.provider-order=local",
        "secrets.fail-on-missing=false",
        "secrets.local.enabled=true",
        "secrets.local.secrets.cache-test-secret=cached-value",
        "secrets.cache.enabled=true",
        "secrets.cache.ttl=10m"
})
class CacheRefreshDemoTest {

    @Autowired
    private SecretCache cache;

    @Autowired
    private SecretRefreshService refreshService;

    @Test
    void shouldInvalidateSingleKey() {
        // Manually put a value in cache
        String cacheKey = cache.buildKey("manual-key", "latest", null);
        cache.put(cacheKey, "manual-value");

        // Verify it's cached
        Optional<String> cached = cache.get(cacheKey);
        assertThat(cached).isPresent().hasValue("manual-value");

        // Refresh (invalidate)
        refreshService.refresh("manual-key");

        // Verify it's gone
        Optional<String> afterRefresh = cache.get(cacheKey);
        assertThat(afterRefresh).isEmpty();
    }

    @Test
    void shouldInvalidateAllKeys() {
        // Populate cache with multiple entries
        cache.put(cache.buildKey("key-1", "latest", null), "value-1");
        cache.put(cache.buildKey("key-2", "latest", null), "value-2");
        cache.put(cache.buildKey("key-3", "latest", "field"), "value-3");

        // Refresh all
        refreshService.refreshAll();

        // All should be gone
        assertThat(cache.get(cache.buildKey("key-1", "latest", null))).isEmpty();
        assertThat(cache.get(cache.buildKey("key-2", "latest", null))).isEmpty();
        assertThat(cache.get(cache.buildKey("key-3", "latest", "field"))).isEmpty();
    }

    @Test
    void shouldTrackRefreshStatistics() {
        refreshService.resetStats();

        refreshService.refresh("some-key");
        refreshService.refresh("another-key");
        refreshService.refreshAll();

        SecretRefreshService.RefreshStats stats = refreshService.getRefreshStats();
        assertThat(stats.singleRefreshCount()).isEqualTo(2);
        assertThat(stats.fullRefreshCount()).isEqualTo(1);
        assertThat(stats.lastRefreshTime()).isNotNull();
    }
}
