package com.hookrelay.ingest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IngestController {

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/webhooks/stripe")
    public ResponseEntity<Void> receive(
            @RequestBody String rawPayload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        boolean accepted = ingestService.ingest(rawPayload, sigHeader);
        return accepted ? ResponseEntity.ok().build() : ResponseEntity.status(400).build();
    }
}
