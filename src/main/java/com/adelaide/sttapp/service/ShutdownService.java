package com.adelaide.sttapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the actual graceful-shutdown mechanism, separated out of
 * AdminController for one specific reason: calling the real shutdown path
 * terminates the JVM (System.exit), which makes it unsafe to exercise
 * directly from an automated test - a test that called the real thing
 * would kill the test runner's own JVM mid-suite. Pulling this into its
 * own bean lets tests substitute a no-op version via @MockBean, so
 * AdminController's conflict-handling logic (already-shutting-down -> 409)
 * can still be regression-tested safely.
 */
@Service
public class ShutdownService {

    private static final Logger log = LoggerFactory.getLogger(ShutdownService.class);

    private final ConfigurableApplicationContext context;
    private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);

    public ShutdownService(ConfigurableApplicationContext context) {
        this.context = context;
    }

    /**
     * Attempts to start a graceful shutdown.
     *
     * @return true if this call initiated the shutdown, false if a
     *         shutdown was already in progress (caller should respond 409).
     */
    public boolean tryInitiateShutdown() {
        if (!shutdownInProgress.compareAndSet(false, true)) {
            return false;
        }

        log.info("Graceful shutdown requested via /api/v1/admin/shutdown");
        // Let the HTTP response actually reach the client before the JVM exits.
        Executors.newSingleThreadScheduledExecutor().schedule(
                () -> System.exit(SpringApplication.exit(context, () -> 0)),
                500, TimeUnit.MILLISECONDS);
        return true;
    }
}
