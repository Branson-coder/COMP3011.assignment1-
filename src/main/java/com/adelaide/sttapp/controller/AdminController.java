package com.adelaide.sttapp.controller;

import com.adelaide.sttapp.dto.AdminErrorResponse;
import com.adelaide.sttapp.dto.GlobalStatsResponse;
import com.adelaide.sttapp.dto.ShutdownResponse;
import com.adelaide.sttapp.dto.UptimeResponse;
import com.adelaide.sttapp.service.AppStatsService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server administration and global statistics endpoints.
 *
 * Implements the YAML spec ("Speech-to-Text Web Site Administration and
 * Statistics API", COMP3011 Assignment 1) exactly:
 *   GET  /api/v1/admin/uptime   -> UptimeResponse
 *   POST /api/v1/admin/shutdown -> 202 ShutdownResponse, or 409 if already shutting down
 *   GET  /api/v1/global/stats   -> GlobalStatsResponse (OpenAI token usage, NOT request counts)
 *
 * All three response schemas in the YAML have additionalProperties: false,
 * so every response here contains exactly the fields specified - nothing
 * extra.
 */
@RestController
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final AppStatsService stats;
    private final ConfigurableApplicationContext context;
    private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);

    public AdminController(AppStatsService stats, ConfigurableApplicationContext context) {
        this.stats = stats;
        this.context = context;
    }

    @GetMapping("/api/v1/admin/uptime")
    public ResponseEntity<UptimeResponse> uptime() {
        Instant now = Instant.now();
        double uptimeSeconds = Duration.between(stats.getStartTime(), now).toNanos() / 1_000_000_000.0;
        return ResponseEntity.ok(new UptimeResponse(
                stats.getStartTime().toString(),
                now.toString(),
                uptimeSeconds));
    }

    @GetMapping("/api/v1/global/stats")
    public ResponseEntity<GlobalStatsResponse> globalStats() {
        return ResponseEntity.ok(new GlobalStatsResponse(stats.getInputTokens(), stats.getOutputTokens()));
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
     * only because the YAML spec explicitly calls for it.
     */
    @PostMapping("/api/v1/admin/shutdown")
    public ResponseEntity<?> shutdown(HttpServletRequest request) {
        if (!shutdownInProgress.compareAndSet(false, true)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(errorBody(HttpStatus.CONFLICT,
                            "Graceful shutdown is already in progress.",
                            request.getRequestURI()));
        }

        log.info("Graceful shutdown requested via /api/v1/admin/shutdown");
        // Let this response actually reach the client before the JVM exits.
        Executors.newSingleThreadScheduledExecutor().schedule(
                () -> System.exit(SpringApplication.exit(context, () -> 0)),
                500, TimeUnit.MILLISECONDS);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ShutdownResponse("Graceful shutdown requested."));
    }

    /**
     * Catches any unexpected failure in this controller's endpoints and
     * reports it using the YAML spec's exact ErrorResponse shape, so a 500
     * from here is still machine-testable rather than falling through to
     * Spring's default Whitelabel error page.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<AdminErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error in admin endpoint {}: {}", request.getRequestURI(), ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected server error occurred.", request.getRequestURI()));
    }

    private AdminErrorResponse errorBody(HttpStatus status, String message, String path) {
        return new AdminErrorResponse(
                Instant.now().toString(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path);
    }
}
