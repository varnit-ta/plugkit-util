package io.github.varnitta.plugkit.db.internal.metrics;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.github.varnitta.plugkit.db.exception.DatabaseException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * {@link MetricsRecorder} backed by a Micrometer {@link MeterRegistry}.
 *
 * <p>Meters: {@code plugkit.db.statement} (timer; tags {@code pool}, {@code operation},
 * {@code outcome}), {@code plugkit.db.transaction} (timer; {@code pool}, {@code outcome}) and
 * {@code plugkit.db.transaction.retries} (counter; {@code pool}, {@code reason}). The outcome is
 * {@code success}, the {@link DatabaseException} type, or {@code application_error} for any other
 * exception. Hikari's own {@code hikaricp.*} pool meters are registered too.
 */
public final class MicrometerMetricsRecorder implements MetricsRecorder {

    static final String SUCCESS = "success";
    static final String APPLICATION_ERROR = "application_error";

    private final MeterRegistry registry;
    private final String poolName;

    public MicrometerMetricsRecorder(MeterRegistry registry, String poolName) {
        this.registry = registry;
        this.poolName = poolName;
    }

    public static Factory factory(MeterRegistry registry) {
        return poolName -> new MicrometerMetricsRecorder(registry, poolName);
    }

    @Override
    public void statement(String operation, Duration elapsed, @Nullable Throwable failure) {
        Timer.builder("plugkit.db.statement")
                .description("Time to run a SQL statement, including waiting for a connection")
                .tag("pool", poolName)
                .tag("operation", operation)
                .tag("outcome", outcome(failure))
                .register(registry)
                .record(elapsed);
    }

    @Override
    public void transaction(Duration elapsed, @Nullable Throwable failure) {
        Timer.builder("plugkit.db.transaction")
                .description("Time to run one transaction attempt, from begin to commit or rollback")
                .tag("pool", poolName)
                .tag("outcome", outcome(failure))
                .register(registry)
                .record(elapsed);
    }

    @Override
    public void retry(Throwable reason) {
        Counter.builder("plugkit.db.transaction.retries")
                .description("Transactions run again after a transient failure")
                .tag("pool", poolName)
                .tag("reason", reason.getClass().getSimpleName())
                .register(registry)
                .increment();
    }

    @Override
    public void configurePool(HikariConfig hikari) {
        hikari.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(registry));
    }

    static String outcome(@Nullable Throwable failure) {
        if (failure == null) {
            return SUCCESS;
        }
        return failure instanceof DatabaseException ? failure.getClass().getSimpleName() : APPLICATION_ERROR;
    }
}
