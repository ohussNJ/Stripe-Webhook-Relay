package com.hookrelay.ingest;

import com.hookrelay.config.HookRelayProperties;
import com.hookrelay.model.Endpoint;
import com.hookrelay.model.Event;
import com.hookrelay.repository.DeliveryRepository;
import com.hookrelay.repository.EndpointRepository;
import com.hookrelay.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestServiceTest {

    @Mock EventRepository eventRepo;
    @Mock EndpointRepository endpointRepo;
    @Mock DeliveryRepository deliveryRepo;

    IngestService service;

    @BeforeEach
    void setup() {
        var props = new HookRelayProperties(
                new HookRelayProperties.Stripe("whsec_test"),
                new HookRelayProperties.Worker(1, 1000, 5000),
                new HookRelayProperties.Retry(3, 1000L, 0.1),
                new HookRelayProperties.Admin("test-key")
        );
        service = new IngestService(props, eventRepo, endpointRepo, deliveryRepo);
    }

    @Test
    void duplicateEventSkipsDeliveryCreation() {
        when(eventRepo.save(anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        service.processEvent("evt_dup", "payment_intent.succeeded", "{}");

        verify(deliveryRepo, never()).save(anyLong(), anyLong());
    }

    @Test
    void newEventCreatesOneDeliveryPerMatchingEndpoint() {
        var savedEvent = new Event(1L, "evt_123", "payment_intent.succeeded", "{}", Instant.now());
        when(eventRepo.save(anyString(), anyString(), anyString())).thenReturn(Optional.of(savedEvent));

        var endpoint1 = new Endpoint(10L, "http://a.test", List.of("payment_intent.succeeded"), Instant.now());
        var endpoint2 = new Endpoint(11L, "http://b.test", List.of(), Instant.now());
        when(endpointRepo.findMatchingEndpoints("payment_intent.succeeded"))
                .thenReturn(List.of(endpoint1, endpoint2));

        service.processEvent("evt_123", "payment_intent.succeeded", "{}");

        verify(deliveryRepo, times(2)).save(anyLong(), anyLong());
    }
}
