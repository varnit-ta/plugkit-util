package io.github.varnitta.plugkit.db.internal.metrics;

import com.zaxxer.hikari.HikariConfig;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * {@link MetricsRecorder} that discards every event; the default when no metrics library is used.
 */
public enum NoMetricsRecorder implements MetricsRecorder {
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
