package com.hookrelay.model;

import java.time.Instant;
import java.util.List;

public record Endpoint(
        Long id,
        String url,
        List<String> eventTypes,
        Instant createdAt
) {}
