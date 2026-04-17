package com.hookrelay.model;

import java.time.Instant;

public record Delivery(
        Long id,
        Long eventId,
        Long endpointId,
        DeliveryStatus status,
        int attempts,
        Instant nextRetryAt,
        Instant lastAttemptedAt
) {}
