package io.github.timhwang777.unisecretdemo.service;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Demonstrates a custom provider chain — GCP first, then AWS fallback.
 */
@Slf4j
@Service
public class MultiCloudService {

    @SecretValue(value = "shared-secret", providers = {"gcp", "aws"})
    private String sharedSecret;

    public boolean isConfigured() {
        return sharedSecret != null && !sharedSecret.isEmpty();
    }

    public String getSharedSecretSource() {
        if (!isConfigured()) {
            return "NOT CONFIGURED";
        }
        // In a real app you wouldn't expose this — here it helps verify which provider won
        return "resolved (length=" + sharedSecret.length() + ")";
    }
}
