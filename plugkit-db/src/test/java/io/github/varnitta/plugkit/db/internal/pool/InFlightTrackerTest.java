package io.github.varnitta.plugkit.db.internal.pool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InFlightTrackerTest {

    private final InFlightTracker tracker = new InFlightTracker("unit");

    @Test
    void closeWithNothingRunningReturnsImmediately() throws InterruptedException {
        assertThat(tracker.closeAndAwait(Duration.ofSeconds(5))).isTrue();
    }

    @Test
    void newWorkIsRejectedAfterClose() throws InterruptedException {
        tracker.closeAndAwait(Duration.ZERO);

        assertThatThrownBy(tracker::enter)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database pool 'unit' is closed");
    }

    @Test
    void closeWaitsForRunningWork() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> worker = CompletableFuture.runAsync(() -> {
            tracker.enter();
            entered.countDown();
            await(release);
            tracker.exit();
        });
        entered.await(5, TimeUnit.SECONDS);

        CompletableFuture<Boolean> closing = CompletableFuture.supplyAsync(() -> close(Duration.ofSeconds(10)));
        Thread.sleep(200);
        assertThat(closing).isNotDone();

        release.countDown();
        assertThat(closing.get(5, TimeUnit.SECONDS)).isTrue();
        worker.get(5, TimeUnit.SECONDS);
    }

    @Test
    void closeGivesUpAfterTheTimeout() throws Exception {
        tracker.enter();

        long start = System.nanoTime();
        boolean drained = tracker.closeAndAwait(Duration.ofMillis(200));

        assertThat(drained).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isBetween(Duration.ofMillis(150), Duration.ofSeconds(2));
        assertThat(tracker.active()).isEqualTo(1);
    }

    @Test
    void nestedCallsFromRunningWorkAreAllowedDuringClose() throws Exception {
        tracker.enter();
        CompletableFuture<Boolean> closing = CompletableFuture.supplyAsync(() -> close(Duration.ofSeconds(10)));
        Thread.sleep(100);

        tracker.enter();
        tracker.exit();
        tracker.exit();

        assertThat(closing.get(5, TimeUnit.SECONDS)).isTrue();
    }

    private boolean close(Duration timeout) {
        try {
            return tracker.closeAndAwait(timeout);
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
