package io.github.timhwang777.unisecretdemo.service;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Demonstrates nested JSON dot-notation field extraction.
 */
@Slf4j
@Service
public class NestedConfigService {

    @SecretValue(value = "nested-config", field = "database.connection.password")
    private String nestedDbPassword;

    @SecretValue(value = "nested-config", field = "database.connection.host")
    private String nestedDbHost;

    @SecretValue(value = "nested-config", field = "cache.redis.host")
    private String redisHost;

    public boolean isConfigured() {
        return nestedDbPassword != null && !nestedDbPassword.isEmpty()
                && nestedDbHost != null && !nestedDbHost.isEmpty()
                && redisHost != null && !redisHost.isEmpty();
    }

    public String getSummary() {
        if (!isConfigured()) {
            return "NOT CONFIGURED";
        }
        return String.format("db=%s, redis=%s", nestedDbHost, redisHost);
    }
}
