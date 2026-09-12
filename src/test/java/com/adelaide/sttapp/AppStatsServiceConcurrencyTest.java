package com.adelaide.sttapp;

import com.adelaide.sttapp.service.AppStatsService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test aimed at surfacing race conditions in the shared stats
 * counters (rubric: "at least one regression test to surface race
 * conditions"). Fires 250 concurrent "requests" at AppStatsService and
 * checks the counters land on an exact, correct total - a naive
 * non-atomic counter implementation would flake this test under load.
 */
class AppStatsServiceConcurrencyTest {

    @Test
    void countersStayConsistentUnderConcurrentLoad() throws InterruptedException {
        AppStatsService stats = new AppStatsService();
        int concurrentRequests = 250;

        ExecutorService pool = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch ready = new CountDownLatch(concurrentRequests);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrentRequests);

        for (int i = 0; i < concurrentRequests; i++) {
            final boolean success = i % 2 == 0;
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    stats.recordRequestStarted();
                    stats.recordRequestFinished(success);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS), "workers failed to start in time");
        go.countDown(); // release every thread at once to maximise contention
        assertTrue(done.await(10, TimeUnit.SECONDS), "requests did not complete in time");
        pool.shutdown();

        assertEquals(concurrentRequests, stats.getTotalRequests());
        assertEquals(concurrentRequests / 2, stats.getSuccessfulTranscriptions());
        assertEquals(concurrentRequests / 2, stats.getFailedTranscriptions());
        assertEquals(0, stats.getInFlightRequests());
    }
}
