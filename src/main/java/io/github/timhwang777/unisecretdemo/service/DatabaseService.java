package io.github.timhwang777.unisecretdemo.service;

import io.github.timhwang777.unisecret.annotation.SecretValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Demonstrates JSON field extraction from a structured secret.
 */
@Slf4j
@Service
public class DatabaseService {

    @SecretValue(value = "db-credentials", field = "password")
    private String dbPassword;

    @SecretValue(value = "db-credentials", field = "username")
    private String dbUsername;

    @SecretValue(value = "db-credentials", field = "host")
    private String dbHost;

    public boolean isConfigured() {
        return dbPassword != null && !dbPassword.isEmpty()
                && dbUsername != null && !dbUsername.isEmpty()
                && dbHost != null && !dbHost.isEmpty();
    }

    public String getConnectionSummary() {
        if (!isConfigured()) {
            return "NOT CONFIGURED";
        }
        // Never expose actual credentials in logs or responses
        return String.format("user=%s, host=%s, password=%s",
                dbUsername, dbHost, maskValue(dbPassword));
    }

    private String maskValue(String value) {
        if (value == null || value.length() <= 3) {
            return "***";
        }
        return value.substring(0, 2) + "***";
    }
}
