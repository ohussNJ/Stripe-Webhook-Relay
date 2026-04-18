package com.hookrelay.chaos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/chaos")
public class ChaosController {

    private static final Logger log = LoggerFactory.getLogger(ChaosController.class);

    private static final int LATENCY_MS = 5000;

    // {service} is cosmetic so each demo consumer has a distinct URL
    @PostMapping("/{service}")
    public ResponseEntity<String> receive(
            @PathVariable String service,
            @RequestParam(defaultValue = "0.4") double failureRate,
            @RequestParam(defaultValue = "0.2") double latencyRate
    ) throws InterruptedException {
        var rng = ThreadLocalRandom.current();

        if (rng.nextDouble() < latencyRate) {
            log.info("chaos service={} injecting {}ms latency", service, LATENCY_MS);
            Thread.sleep(LATENCY_MS);
        }

        if (rng.nextDouble() < failureRate) {
            log.info("chaos service={} returning 500", service);
            return ResponseEntity.internalServerError().body("simulated failure");
        }

        log.info("chaos service={} returning 200", service);
        return ResponseEntity.ok("ok");
    }
}
