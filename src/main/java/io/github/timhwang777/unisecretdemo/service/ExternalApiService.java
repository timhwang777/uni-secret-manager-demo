package io.github.timhwang777.unisecretdemo.service;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Demonstrates single provider override — forces GCP as the sole provider.
 */
@Slf4j
@Service
public class ExternalApiService {

    @SecretValue(value = "gcp-api-key", provider = "gcp")
    private String apiKey;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    public String getStatus() {
        if (!isConfigured()) {
            return "API key NOT configured";
        }
        return "API key configured (length=" + apiKey.length() + ")";
    }
}
