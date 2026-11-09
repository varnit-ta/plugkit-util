package io.github.varnitta.plugkit.db;

import io.github.varnitta.plugkit.db.config.DatabaseConfig;
import io.github.varnitta.plugkit.db.internal.connection.DataSourceConnectionProvider;
import io.github.varnitta.plugkit.db.internal.metrics.MetricsRecorder;
import io.github.varnitta.plugkit.db.internal.metrics.MetricsRecorderFactory;
import io.github.varnitta.plugkit.db.internal.pool.PooledDatabase;
import javax.sql.DataSource;

/**
 * Entry point for creating {@link Database} instances.
 *
 * <pre>{@code
 * Database db = Databases.create(DatabaseConfig.builder()
 *         .url("jdbc:postgresql://localhost:5432/app")
 *         .user("app")
 *         .password(secret)
 *         .build());
 * }</pre>
 *
 * <p>To record metrics with Micrometer, use
 * {@link io.github.varnitta.plugkit.db.metrics.MeteredDatabases} instead.
 */
public final class Databases {

    private Databases() {
    }

    /**
     * Builds and starts a connection pool from {@code config}. Closing the returned database closes the
     * pool.
     */
    public static Database create(DatabaseConfig config) {
        return PooledDatabase.start(config, MetricsRecorderFactory.none(), DataSourceConnectionProvider::new);
    }

    /**
     * Uses a pool the application already has. The database type is read from a connection's metadata.
     * plugkit-db can't apply its driver and session settings to this pool, and closing the returned
     * database leaves the pool open.
     */
    public static Database create(DataSource dataSource) {
        return PooledDatabase.wrap(dataSource, MetricsRecorderFactory.none(), DataSourceConnectionProvider::new);
    }
}
