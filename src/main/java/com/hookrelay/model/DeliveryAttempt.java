package com.hookrelay.model;

import java.time.Instant;

public record DeliveryAttempt(
        Long id,
        Long deliveryId,
        Instant attemptedAt,
        Integer httpStatus,
        long latencyMs,
        String outcome
) {}
