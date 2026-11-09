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
        return NoMetricsRecorder.INSTANCE;
    }
}
