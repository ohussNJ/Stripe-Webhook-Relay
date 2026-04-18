package com.hookrelay.delivery;

import com.hookrelay.config.HookRelayProperties;
import com.hookrelay.model.Delivery;
import com.hookrelay.repository.DeliveryAttemptRepository;
import com.hookrelay.repository.DeliveryRepository;
import com.hookrelay.repository.EndpointRepository;
import com.hookrelay.repository.EventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class DeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);

    private final HookRelayProperties props;
    private final DeliveryRepository deliveryRepo;
    private final DeliveryAttemptRepository attemptRepo;
    private final EndpointRepository endpointRepo;
    private final EventRepository eventRepo;
    private final HttpClient httpClient;

    private final Counter successCounter;
    private final Counter dead4xxCounter;
    private final Counter dead5xxCounter;
    private final Counter deadTimeoutCounter;
    private final Timer dispatchTimer;

    public DeliveryWorker(HookRelayProperties props,
                          DeliveryRepository deliveryRepo,
                          DeliveryAttemptRepository attemptRepo,
                          EndpointRepository endpointRepo,
                          EventRepository eventRepo,
                          HttpClient httpClient,
                          JdbcTemplate jdbc,
                          MeterRegistry meterRegistry) {
        this.props = props;
        this.deliveryRepo = deliveryRepo;
        this.attemptRepo = attemptRepo;
        this.endpointRepo = endpointRepo;
        this.eventRepo = eventRepo;
        this.httpClient = httpClient;

        successCounter     = Counter.builder("hookrelay.deliveries.succeeded").register(meterRegistry);
        dead4xxCounter     = Counter.builder("hookrelay.deliveries.dead_lettered").tag("reason", "http_4xx").register(meterRegistry);
        dead5xxCounter     = Counter.builder("hookrelay.deliveries.dead_lettered").tag("reason", "http_5xx").register(meterRegistry);
        deadTimeoutCounter = Counter.builder("hookrelay.deliveries.dead_lettered").tag("reason", "timeout").register(meterRegistry);
        dispatchTimer      = Timer.builder("hookrelay.dispatch.latency").register(meterRegistry);

        Gauge.builder("hookrelay.deliveries.pending", jdbc,
                        j -> j.queryForObject(
                                "SELECT COUNT(*) FROM deliveries WHERE status = 'pending'", Long.class))
                .register(meterRegistry);
    }

    @PostConstruct
    public void start() {
        int recovered = deliveryRepo.resetInProgressToPending();
        if (recovered > 0) {
            log.warn("Recovered {} in_progress deliveries from previous run", recovered);
        }

        var executor = Executors.newFixedThreadPool(
                props.worker().threadCount(),
                Thread.ofVirtual().name("delivery-worker-", 0).factory()
        );
        for (int i = 0; i < props.worker().threadCount(); i++) {
            executor.submit(this::pollLoop);
        }
        log.info("Started {} delivery workers", props.worker().threadCount());
    }

    private void pollLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                var claimed = deliveryRepo.claimNextPending();
                if (claimed.isEmpty()) {
                    Thread.sleep(props.worker().pollIntervalMs());
                    continue;
                }
                dispatch(claimed.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Unhandled worker error", e);
            }
        }
    }

    private void dispatch(Delivery delivery) {
        var endpoint = endpointRepo.findById(delivery.endpointId()).orElseThrow();
        var event    = eventRepo.findById(delivery.eventId()).orElseThrow();

        var request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint.url()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(event.payload()))
                .timeout(Duration.ofMillis(props.worker().httpTimeoutMs()))
                .build();

        int nextAttempts = delivery.attempts() + 1;
        long startMs = System.currentTimeMillis();

        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long latencyMs = System.currentTimeMillis() - startMs;
            dispatchTimer.record(latencyMs, TimeUnit.MILLISECONDS);

            int status = response.statusCode();

            if (status >= 200 && status < 300) {
                deliveryRepo.markDelivered(delivery.id());
                attemptRepo.save(delivery.id(), status, latencyMs, "succeeded");
                successCounter.increment();
                log.info("event_id={} delivery_id={} url={} attempt={} result=delivered status={} latency_ms={}",
                        event.id(), delivery.id(), endpoint.url(), nextAttempts, status, latencyMs);

            } else if (status >= 400 && status < 500) {
                // 4xx means the endpoint rejected the payload; retrying will not help
                deliveryRepo.markDeadLettered(delivery.id());
                attemptRepo.save(delivery.id(), status, latencyMs, "dead_lettered");
                dead4xxCounter.increment();
                log.warn("event_id={} delivery_id={} url={} attempt={} result=dead_lettered status={} latency_ms={}",
                        event.id(), delivery.id(), endpoint.url(), nextAttempts, status, latencyMs);

            } else {
                handleRetryOrDeadLetter(delivery, nextAttempts, "http_5xx", event.id(), endpoint.url(), status, latencyMs);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.io.IOException e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            handleRetryOrDeadLetter(delivery, nextAttempts, "timeout", event.id(), endpoint.url(), null, latencyMs);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            log.error("event_id={} delivery_id={} dispatch error", event.id(), delivery.id(), e);
            handleRetryOrDeadLetter(delivery, nextAttempts, "timeout", event.id(), endpoint.url(), null, latencyMs);
        }
    }

    private void handleRetryOrDeadLetter(Delivery delivery, int nextAttempts, String reason,
                                         long eventId, String url, Integer status, long latencyMs) {
        var retryConfig = props.retry();

        if (nextAttempts >= retryConfig.maxAttempts()) {
            deliveryRepo.markDeadLettered(delivery.id());
            attemptRepo.save(delivery.id(), status, latencyMs, "dead_lettered");
            Counter deadCounter = "http_5xx".equals(reason) ? dead5xxCounter : deadTimeoutCounter;
            deadCounter.increment();
            log.warn("event_id={} delivery_id={} url={} attempt={} result=dead_lettered reason={} status={}",
                    eventId, delivery.id(), url, nextAttempts, reason, status);
        } else {
            var nextRetry = RetryCalculator.nextRetryAt(
                    nextAttempts, retryConfig.baseDelayMs(), retryConfig.jitterFactor()
            );
            deliveryRepo.markForRetry(delivery.id(), nextRetry);
            attemptRepo.save(delivery.id(), status, latencyMs, "retrying");
            log.info("event_id={} delivery_id={} url={} attempt={} result=retry reason={} next_retry_at={}",
                    eventId, delivery.id(), url, nextAttempts, reason, nextRetry);
        }
    }
}
