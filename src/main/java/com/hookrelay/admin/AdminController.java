package com.hookrelay.admin;

import com.hookrelay.model.Delivery;
import com.hookrelay.model.DeliveryAttempt;
import com.hookrelay.model.Endpoint;
import com.hookrelay.model.Event;
import com.hookrelay.repository.DeliveryAttemptRepository;
import com.hookrelay.repository.DeliveryRepository;
import com.hookrelay.repository.EndpointRepository;
import com.hookrelay.repository.EventRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final EndpointRepository endpointRepo;
    private final DeliveryRepository deliveryRepo;
    private final DeliveryAttemptRepository attemptRepo;
    private final EventRepository eventRepo;

    public AdminController(EndpointRepository endpointRepo,
                           DeliveryRepository deliveryRepo,
                           DeliveryAttemptRepository attemptRepo,
                           EventRepository eventRepo) {
        this.endpointRepo = endpointRepo;
        this.deliveryRepo = deliveryRepo;
        this.attemptRepo  = attemptRepo;
        this.eventRepo    = eventRepo;
    }

    // Endpoints

    @PostMapping("/endpoints")
    public ResponseEntity<Endpoint> registerEndpoint(@Valid @RequestBody RegisterEndpointRequest req) {
        var endpoint = endpointRepo.save(req.url(), req.eventTypes() != null ? req.eventTypes() : List.of());
        return ResponseEntity.ok(endpoint);
    }

    @GetMapping("/endpoints")
    public List<Endpoint> listEndpoints() {
        return endpointRepo.findAll();
    }

    @DeleteMapping("/endpoints/{id}")
    public ResponseEntity<Void> deleteEndpoint(@PathVariable long id) {
        endpointRepo.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Events

    @GetMapping("/events")
    public List<Event> listRecentEvents(@RequestParam(defaultValue = "50") int limit) {
        return eventRepo.findRecent(Math.min(limit, 200));
    }

    @GetMapping("/events/{eventId}/deliveries")
    public List<Delivery> listDeliveries(@PathVariable long eventId) {
        return deliveryRepo.findByEventId(eventId);
    }

    @PostMapping("/events/{eventId}/replay")
    public ResponseEntity<ReplayResult> replayEvent(@PathVariable long eventId) {
        int count = deliveryRepo.resetAllToPending(eventId);
        return ResponseEntity.ok(new ReplayResult(count));
    }

    // Deliveries

    @GetMapping("/deliveries/{id}")
    public ResponseEntity<Delivery> getDelivery(@PathVariable long id) {
        return deliveryRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/deliveries/{id}/attempts")
    public List<DeliveryAttempt> listAttempts(@PathVariable long id) {
        return attemptRepo.findByDeliveryId(id);
    }

    @PostMapping("/deliveries/{id}/replay")
    public ResponseEntity<Void> replayDelivery(@PathVariable long id) {
        int updated = deliveryRepo.resetToPending(id);
        return updated > 0 ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    // DTOs

    record RegisterEndpointRequest(
            @NotBlank @URL String url,
            List<String> eventTypes
    ) {}

    record ReplayResult(int queued) {}
}
