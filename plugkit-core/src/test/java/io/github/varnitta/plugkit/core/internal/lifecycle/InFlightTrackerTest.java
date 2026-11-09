package io.github.varnitta.plugkit.core.internal.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InFlightTrackerTest {

    private final InFlightTracker tracker = new InFlightTracker("Database pool 'unit'");

    @Test
    void stoppingWithNothingRunningReturnsImmediately() throws InterruptedException {
        assertThat(tracker.stopNewWorkAndWait(Duration.ofSeconds(5))).isTrue();
    }

    @Test
    void newWorkIsRefusedAfterStopping() throws InterruptedException {
        tracker.stopNewWorkAndWait(Duration.ZERO);

        assertThatThrownBy(tracker::startWork)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database pool 'unit' is closed");
    }

    @Test
    void stoppingWaitsForRunningWork() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> worker = CompletableFuture.runAsync(() -> {
            tracker.startWork();
            entered.countDown();
            await(release);
            tracker.finishWork();
        });
        entered.await(5, TimeUnit.SECONDS);

        CompletableFuture<Boolean> closing = CompletableFuture.supplyAsync(() -> stopAndWait(Duration.ofSeconds(10)));
        Thread.sleep(200);
        assertThat(closing).isNotDone();

        release.countDown();
        assertThat(closing.get(5, TimeUnit.SECONDS)).isTrue();
        worker.get(5, TimeUnit.SECONDS);
    }

    @Test
    void stoppingGivesUpAfterTheTimeout() throws Exception {
        tracker.startWork();

        long start = System.nanoTime();
        boolean drained = tracker.stopNewWorkAndWait(Duration.ofMillis(200));

        assertThat(drained).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isBetween(Duration.ofMillis(150), Duration.ofSeconds(2));
        assertThat(tracker.runningCount()).isEqualTo(1);
    }

    @Test
    void nestedWorkFromARunningThreadIsAllowedWhileStopping() throws Exception {
        tracker.startWork();
        CompletableFuture<Boolean> closing = CompletableFuture.supplyAsync(() -> stopAndWait(Duration.ofSeconds(10)));
        Thread.sleep(100);

        tracker.startWork();
        tracker.finishWork();
        tracker.finishWork();

        assertThat(closing.get(5, TimeUnit.SECONDS)).isTrue();
    }

    private boolean stopAndWait(Duration timeout) {
        try {
            return tracker.stopNewWorkAndWait(timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
