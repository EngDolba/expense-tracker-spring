package com.dolbaia.ledger.common;

import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Database health check for the load balancer: 200 when SELECT 1 works, else 503. */
@RestController
public class HealthController {

    public record Health(String status, String database, long responseMs, String timestamp) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public HealthController(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @GetMapping("/api/health")
    ResponseEntity<Health> health() {
        long started = clock.millis();
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(health("ok", "connected", started));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(health("degraded", "unavailable", started));
        }
    }

    private Health health(String status, String database, long started) {
        return new Health(status, database, clock.millis() - started, clock.instant().toString());
    }
}
