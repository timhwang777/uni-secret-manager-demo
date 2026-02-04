package io.github.timhwang777.unisecretdemo.service;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Demonstrates default value fallback when a secret is not found in any provider.
 */
@Slf4j
@Service
public class FeatureFlagService {

    @SecretValue(value = "optional-feature", defaultValue = "disabled")
    private String featureFlag;

    public String getFeatureFlag() {
        return featureFlag;
    }

    public boolean isFeatureEnabled() {
        return !"disabled".equals(featureFlag);
    }
}
