package io.github.timhwang777.unisecretdemo.controller;

import io.github.timhwang777.unisecretdemo.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DatabaseService databaseService;
    private final ExternalApiService externalApiService;
    private final FeatureFlagService featureFlagService;
    private final MultiCloudService multiCloudService;
    private final VersionedSecretService versionedSecretService;
    private final NestedConfigService nestedConfigService;
    private final VaultMultiProviderService vaultMultiProviderService;

    @GetMapping("/db-status")
    public ResponseEntity<Map<String, Object>> getDbStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", databaseService.isConfigured());
        result.put("connection", databaseService.getConnectionSummary());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api-status")
    public ResponseEntity<Map<String, Object>> getApiStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", externalApiService.isConfigured());
        result.put("status", externalApiService.getStatus());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllStatus() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("database", Map.of(
                "configured", databaseService.isConfigured(),
                "summary", databaseService.getConnectionSummary()
        ));
        result.put("externalApi", Map.of(
                "configured", externalApiService.isConfigured(),
                "status", externalApiService.getStatus()
        ));
        result.put("featureFlag", Map.of(
                "flag", featureFlagService.getFeatureFlag(),
                "enabled", featureFlagService.isFeatureEnabled()
        ));
        result.put("multiCloud", Map.of(
                "configured", multiCloudService.isConfigured(),
                "source", multiCloudService.getSharedSecretSource()
        ));
        result.put("versionedSecret", Map.of(
                "configured", versionedSecretService.isConfigured(),
                "status", versionedSecretService.getStatus()
        ));
        result.put("nestedConfig", Map.of(
                "configured", nestedConfigService.isConfigured(),
                "summary", nestedConfigService.getSummary()
        ));
        result.put("vaultMultiProvider", Map.of(
                "configured", vaultMultiProviderService.isConfigured(),
                "vaultApiKey", vaultMultiProviderService.getVaultApiKeyStatus(),
                "sharedSecret", vaultMultiProviderService.getSharedSecretSource()
        ));

        return ResponseEntity.ok(result);
    }
}
