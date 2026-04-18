package com.hookrelay.delivery;

import java.time.Instant;

public final class RetryCalculator {

    private static final long MAX_DELAY_MS = 7_200_000L; // 2 hours

    private RetryCalculator() {}

    public static Instant nextRetryAt(int attempts, long baseDelayMs, double jitterFactor) {
        long delay = Math.min(baseDelayMs * (1L << attempts), MAX_DELAY_MS);
        long jitter = (long) (delay * jitterFactor * Math.random());
        return Instant.now().plusMillis(delay + jitter);
    }
}
