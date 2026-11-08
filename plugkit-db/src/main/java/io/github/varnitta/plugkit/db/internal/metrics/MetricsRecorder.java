package io.github.varnitta.plugkit.db.internal.metrics;

import com.zaxxer.hikari.HikariConfig;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Receives the events plugkit-db measures for one pool.
 *
 * <p>The interface has no metrics-library types, so the engine can call it whether or not Micrometer
 * is on the classpath. {@link #none()} discards everything.
 */
public interface MetricsRecorder {

    void statement(String operation, Duration elapsed, @Nullable Throwable failure);

    void transaction(Duration elapsed, @Nullable Throwable failure);

    void retry(Throwable reason);

    void configurePool(HikariConfig hikari);

    static MetricsRecorder none() {
        return NoMetrics.INSTANCE;
    }

    /**
     * Creates the recorder for a pool, given the pool's name.
     */
    @FunctionalInterface
    interface Factory {

        MetricsRecorder forPool(String poolName);

        static Factory none() {
            return poolName -> NoMetrics.INSTANCE;
        }
    }

    /**
     * Recorder that discards every event.
     */
    enum NoMetrics implements MetricsRecorder {
        INSTANCE;

        @Override
        public void statement(String operation, Duration elapsed, @Nullable Throwable failure) {
        }

        @Override
        public void transaction(Duration elapsed, @Nullable Throwable failure) {
        }

        @Override
        public void retry(Throwable reason) {
        }

        @Override
        public void configurePool(HikariConfig hikari) {
        }
    }
}
