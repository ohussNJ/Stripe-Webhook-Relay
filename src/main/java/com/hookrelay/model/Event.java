package com.hookrelay.model;

import java.time.Instant;

public record Event(
        Long id,
        String stripeEventId,
        String type,
        String payload,
        Instant receivedAt
) {}
