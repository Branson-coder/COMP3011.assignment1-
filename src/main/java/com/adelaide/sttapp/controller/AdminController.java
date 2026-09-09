package com.adelaide.sttapp.controller;

import com.adelaide.sttapp.service.AppStatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Uptime / runtime stats / graceful shutdown endpoints.
 *
 * IMPORTANT: the assignment's actual YAML API spec for these endpoints was
 * not included in the brief text provided to this assistant (the brief
 * only contains a placeholder line, "Additional endpoints yaml
 * specification here"). The paths, field names and status codes below are
 * a reasonable best guess based on the one path the brief does name
 * explicitly (/api/v1/admin/shutdown, mentioned under Advanced Topics) and
 * standard conventions. Since TITAN grades these endpoints by exact
 * machine testing, get the real YAML from the subject site/Assignment 1
 * launch slides and line these up exactly before relying on TITAN feedback.
 */
@RestController
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final AppStatsService stats;
    private final ConfigurableApplicationContext context;

    public AdminController(AppStatsService stats, ConfigurableApplicationContext context) {
        this.stats = stats;
        this.context = context;
    }

    @GetMapping("/api/v1/admin/uptime")
    public ResponseEntity<Map<String, Object>> uptime() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startTime", stats.getStartTime().toString());
        body.put("uptimeSeconds", stats.getUptimeSeconds());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/api/v1/admin/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("totalRequests", stats.getTotalRequests());
        body.put("inFlightRequests", stats.getInFlightRequests());
        body.put("successfulTranscriptions", stats.getSuccessfulTranscriptions());
        body.put("failedTranscriptions", stats.getFailedTranscriptions());
        body.put("uptimeSeconds", stats.getUptimeSeconds());
        return ResponseEntity.ok(body);
    }

    /**
     * Graceful shutdown.
     *
     * SECURITY NOTE (Advanced Topics question, not assessed but worth
     * having an actual answer to): exposing an unauthenticated shutdown
     * endpoint on the public internet lets anyone take the service down
     * with one HTTP request - trivial denial of service. A safer real-world
     * version would (a) require an authenticated/admin-only caller, e.g. a
     * separate management port bound to localhost only, or a bearer token
     * distinct from the STT key, and (b) let a cloud orchestrator (Kubernetes,
     * systemd, etc.) manage lifecycle via SIGTERM rather than exposing an
     * HTTP verb for it at all. This assignment exposes it unauthenticated
     * only because the brief explicitly calls for it.
     */
    @PostMapping("/api/v1/admin/shutdown")
    public ResponseEntity<Map<String, String>> shutdown() {
        log.info("Graceful shutdown requested via /api/v1/admin/shutdown");
        // Let this response actually reach the client before the JVM exits.
        Executors.newSingleThreadScheduledExecutor().schedule(
                () -> System.exit(SpringApplication.exit(context, () -> 0)),
                500, TimeUnit.MILLISECONDS);
        return ResponseEntity.ok(Map.of("status", "shutting down"));
    }
}
