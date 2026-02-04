package io.github.timhwang777.unisecretdemo.service;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Demonstrates versioned secret access.
 */
@Slf4j
@Service
public class VersionedSecretService {

    @SecretValue(value = "rotating-secret", version = "latest")
    private String latestSecret;

    public boolean isConfigured() {
        return latestSecret != null && !latestSecret.isEmpty();
    }

    public String getStatus() {
        if (!isConfigured()) {
            return "NOT CONFIGURED";
        }
        return "latest version resolved (length=" + latestSecret.length() + ")";
    }
}
