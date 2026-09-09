package com.adelaide.sttapp.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Tracks process uptime and simple runtime counters.
 *
 * All state is either immutable (startTime) or backed by
 * java.util.concurrent atomics, so this bean is safe to share across
 * every request thread without external locking - there is no
 * check-then-act sequence anywhere here, only atomic increments.
 */
@Service
public class AppStatsService {

    private final Instant startTime = Instant.now();

    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder successfulTranscriptions = new LongAdder();
    private final LongAdder failedTranscriptions = new LongAdder();
    private final AtomicLong inFlightRequests = new AtomicLong(0);

    public void recordRequestStarted() {
        totalRequests.increment();
        inFlightRequests.incrementAndGet();
    }

    public void recordRequestFinished(boolean success) {
        inFlightRequests.decrementAndGet();
        if (success) {
            successfulTranscriptions.increment();
        } else {
            failedTranscriptions.increment();
        }
    }

    public long getUptimeSeconds() {
        return Duration.between(startTime, Instant.now()).getSeconds();
    }

    public Instant getStartTime() {
        return startTime;
    }

    public long getTotalRequests() {
        return totalRequests.sum();
    }

    public long getSuccessfulTranscriptions() {
        return successfulTranscriptions.sum();
    }

    public long getFailedTranscriptions() {
        return failedTranscriptions.sum();
    }

    public long getInFlightRequests() {
        return inFlightRequests.get();
    }
}
