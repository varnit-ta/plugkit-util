package io.github.varnitta.plugkit.db.internal.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import io.github.varnitta.plugkit.db.exception.Deadlock;
import io.github.varnitta.plugkit.db.exception.UniqueViolation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MicrometerMetricsRecorderTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MetricsRecorder recorder = MicrometerMetricsRecorder.factory(registry).forPool("orders");

    @Test
    void recordsStatementsByOperationAndOutcome() {
        recorder.statement("query", Duration.ofMillis(5), null);
        recorder.statement("query", Duration.ofMillis(7), null);
        recorder.statement("update", Duration.ofMillis(3), new UniqueViolation("dup", null, "23505", null));

        assertThat(registry.get("plugkit.db.statement").tags("pool", "orders", "operation", "query", "outcome", "success")
                .timer().count()).isEqualTo(2);
        assertThat(registry.get("plugkit.db.statement").tags("operation", "update", "outcome", "UniqueViolation")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void recordsTransactionsAndRetries() {
        Deadlock deadlock = new Deadlock("deadlock", "40P01", null);
        recorder.transaction(Duration.ofMillis(20), deadlock);
        recorder.retry(deadlock);
        recorder.transaction(Duration.ofMillis(10), null);
        recorder.transaction(Duration.ofMillis(1), new IllegalStateException("app"));

        assertThat(registry.get("plugkit.db.transaction").tags("pool", "orders", "outcome", "Deadlock").timer().count())
                .isEqualTo(1);
        assertThat(registry.get("plugkit.db.transaction").tags("outcome", "success").timer().count()).isEqualTo(1);
        assertThat(registry.get("plugkit.db.transaction").tags("outcome", "application_error").timer().count())
                .isEqualTo(1);
        assertThat(registry.get("plugkit.db.transaction.retries").tags("pool", "orders", "reason", "Deadlock")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void registersHikariPoolMetrics() {
        HikariConfig hikari = new HikariConfig();

        recorder.configurePool(hikari);

        assertThat(hikari.getMetricsTrackerFactory()).isNotNull();
    }

    @Test
    void noMetricsIgnoresEverything() {
        MetricsRecorder none = MetricsRecorder.Factory.none().forPool("x");
        none.statement("query", Duration.ZERO, null);
        none.transaction(Duration.ZERO, null);
        none.retry(new IllegalStateException());
        HikariConfig hikari = new HikariConfig();
        none.configurePool(hikari);

        assertThat(hikari.getMetricsTrackerFactory()).isNull();
    }
}
