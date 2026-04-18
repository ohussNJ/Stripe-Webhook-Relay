package com.hookrelay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hookrelay")
public record HookRelayProperties(
        Stripe stripe,
        Worker worker,
        Retry retry,
        Admin admin
) {
    public record Stripe(String webhookSecret) {}

    public record Worker(int threadCount, int pollIntervalMs, int httpTimeoutMs) {}

    public record Retry(int maxAttempts, long baseDelayMs, double jitterFactor) {}

    public record Admin(String apiKey) {}
}
