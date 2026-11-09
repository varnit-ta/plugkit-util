package io.github.varnitta.plugkit.db.internal.metrics;

/**
 * Creates the {@link MetricsRecorder} for a pool, given the pool's name.
 *
 * <p>Factories are passed to {@code PooledDatabase} by whoever creates the database: none for
 * {@code Databases}, Micrometer for {@code MeteredDatabases} and the Spring integration.
 */
@FunctionalInterface
public interface MetricsRecorderFactory {

    MetricsRecorder forPool(String poolName);

    static MetricsRecorderFactory none() {
        return poolName -> NoMetricsRecorder.INSTANCE;
    }
}
