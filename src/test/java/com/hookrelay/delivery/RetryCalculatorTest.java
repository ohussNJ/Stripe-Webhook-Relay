package com.hookrelay.delivery;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RetryCalculatorTest {

    private static final long BASE_DELAY_MS = 30_000L;
    private static final double JITTER_FACTOR = 0.25;

    @Test
    void firstRetryIsAtLeastBaseDelay() {
        Instant before = Instant.now();
        Instant next = RetryCalculator.nextRetryAt(1, BASE_DELAY_MS, JITTER_FACTOR);
        assertThat(next).isAfter(before.plusMillis(BASE_DELAY_MS - 1));
    }

    @Test
    void delayDoublesWithEachAttempt() {
        Instant now = Instant.now();
        Instant attempt1 = RetryCalculator.nextRetryAt(1, BASE_DELAY_MS, 0);
        Instant attempt2 = RetryCalculator.nextRetryAt(2, BASE_DELAY_MS, 0);
        Instant attempt3 = RetryCalculator.nextRetryAt(3, BASE_DELAY_MS, 0);

        long delay1 = attempt1.toEpochMilli() - now.toEpochMilli();
        long delay2 = attempt2.toEpochMilli() - now.toEpochMilli();
        long delay3 = attempt3.toEpochMilli() - now.toEpochMilli();

        assertThat(delay2).isGreaterThan(delay1);
        assertThat(delay3).isGreaterThan(delay2);
    }

    @Test
    void delayIsCapeedAtTwoHours() {
        // attempt 100 would overflow without the cap
        Instant next = RetryCalculator.nextRetryAt(100, BASE_DELAY_MS, 0);
        long maxMs = 7_200_000L;
        assertThat(next.toEpochMilli() - Instant.now().toEpochMilli()).isLessThanOrEqualTo(maxMs + 100);
    }

    @RepeatedTest(20)
    void jitterKeepsDelayWithinExpectedBounds() {
        long delay = BASE_DELAY_MS * 2; // attempt 1 base
        Instant next = RetryCalculator.nextRetryAt(1, BASE_DELAY_MS, JITTER_FACTOR);
        long maxWithJitter = delay + (long) (delay * JITTER_FACTOR) + 100;

        assertThat(next.toEpochMilli() - Instant.now().toEpochMilli())
                .isLessThanOrEqualTo(maxWithJitter);
    }
}
