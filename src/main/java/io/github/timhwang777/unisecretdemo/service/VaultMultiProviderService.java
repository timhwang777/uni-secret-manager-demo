package io.github.timhwang777.unisecretdemo.service;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Demonstrates Vault in provider chains — explicit Vault provider and Vault→AWS fallback.
 */
@Slf4j
@Service
public class VaultMultiProviderService {

    @SecretValue(value = "api-key", provider = "vault", field = "value")
    private String vaultApiKey;

    @SecretValue(value = "shared-secret", providers = {"vault", "aws"})
    private String sharedSecret;

    public boolean isConfigured() {
        return vaultApiKey != null && !vaultApiKey.isEmpty();
    }

    public String getVaultApiKeyStatus() {
        if (vaultApiKey == null || vaultApiKey.isEmpty()) {
            return "NOT CONFIGURED";
        }
        return "resolved (length=" + vaultApiKey.length() + ")";
    }

    public String getSharedSecretSource() {
        if (sharedSecret == null || sharedSecret.isEmpty()) {
            return "NOT CONFIGURED";
        }
        return "resolved (length=" + sharedSecret.length() + ")";
    }
}
