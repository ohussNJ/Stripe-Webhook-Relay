package com.hookrelay.ingest;

import com.hookrelay.config.HookRelayProperties;
import com.hookrelay.repository.DeliveryRepository;
import com.hookrelay.repository.EndpointRepository;
import com.hookrelay.repository.EventRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final HookRelayProperties props;
    private final EventRepository eventRepo;
    private final EndpointRepository endpointRepo;
    private final DeliveryRepository deliveryRepo;

    public IngestService(HookRelayProperties props,
                         EventRepository eventRepo,
                         EndpointRepository endpointRepo,
                         DeliveryRepository deliveryRepo) {
        this.props = props;
        this.eventRepo = eventRepo;
        this.endpointRepo = endpointRepo;
        this.deliveryRepo = deliveryRepo;
    }

    // Returns false if signature is invalid
    @Transactional
    public boolean ingest(String rawPayload, String sigHeader) {
        Event stripeEvent;
        try {
            stripeEvent = Webhook.constructEvent(rawPayload, sigHeader, props.stripe().webhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe signature");
            return false;
        }
        return processEvent(stripeEvent.getId(), stripeEvent.getType(), rawPayload);
    }

    // Package-private so tests can exercise fan-out and idempotency logic without a valid Stripe signature
    @Transactional
    boolean processEvent(String stripeEventId, String eventType, String rawPayload) {
        var savedEvent = eventRepo.save(stripeEventId, eventType, rawPayload);
        if (savedEvent.isEmpty()) {
            log.info("Duplicate event {} skipping", stripeEventId);
            return true;
        }

        long eventId = savedEvent.get().id();
        var matchingEndpoints = endpointRepo.findMatchingEndpoints(eventType);

        for (var endpoint : matchingEndpoints) {
            deliveryRepo.save(eventId, endpoint.id());
        }

        log.info("Ingested event={} type={} deliveries={}", stripeEventId, eventType, matchingEndpoints.size());
        return true;
    }
}
