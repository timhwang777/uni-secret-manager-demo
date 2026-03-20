package io.github.timhwang777.unisecretdemo.controller;

import io.github.timhwang777.unisecret.config.SecretManagerProperties;
import io.github.timhwang777.unisecret.service.SecretRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/secrets")
@RequiredArgsConstructor
public class SecretManagementController {

    private final SecretManagerProperties properties;
    private final SecretRefreshService refreshService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("providerOrder", properties.getProviderOrder());
        status.put("failOnMissing", properties.isFailOnMissing());

        Map<String, Boolean> providers = new LinkedHashMap<>();
        providers.put("aws", properties.getAws().isEnabled());
        providers.put("gcp", properties.getGcp().isEnabled());
        providers.put("vault", properties.getVault().isEnabled());
        providers.put("local", properties.getLocal().isEnabled());
        status.put("providers", providers);

        Map<String, Object> cache = new LinkedHashMap<>();
        cache.put("enabled", properties.getCache().isEnabled());
        cache.put("ttl", properties.getCache().getTtl().toString());
        cache.put("maxSize", properties.getCache().getMaxSize());
        status.put("cache", cache);

        return ResponseEntity.ok(status);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> getHealth() {
        Map<String, String> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("message", "Secret manager is operational");
        return ResponseEntity.ok(health);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshAll() {
        refreshService.refreshAll();
        return ResponseEntity.ok(Map.of("result", "All cached secrets invalidated"));
    }

    @PostMapping("/refresh/{key}")
    public ResponseEntity<Map<String, String>> refreshKey(@PathVariable String key) {
        refreshService.refresh(key);
        return ResponseEntity.ok(Map.of("result", "Cache invalidated for key: " + key));
    }
}
