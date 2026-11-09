package io.github.varnitta.plugkit.db.metrics;

import io.github.varnitta.plugkit.db.Database;
import io.github.varnitta.plugkit.db.config.DatabaseConfig;
import io.github.varnitta.plugkit.db.internal.connection.DataSourceConnectionProvider;
import io.github.varnitta.plugkit.db.internal.metrics.MicrometerMetricsRecorder;
import io.github.varnitta.plugkit.db.internal.pool.PooledDatabase;
import io.micrometer.core.instrument.MeterRegistry;
import javax.sql.DataSource;

/**
 * Creates {@link Database} instances that record metrics in a Micrometer {@link MeterRegistry}.
 *
 * <p>This is the only public class that uses Micrometer, so applications without it never load
 * Micrometer classes. Recorded meters:
 *
 * <ul>
 *   <li>{@code plugkit.db.statement}: timer per statement, tagged {@code pool}, {@code operation}
 *       ({@code query}, {@code queryOne}, {@code update}, {@code batch}) and {@code outcome}</li>
 *   <li>{@code plugkit.db.transaction}: timer per transaction attempt, tagged {@code pool} and
 *       {@code outcome}</li>
 *   <li>{@code plugkit.db.transaction.retries}: counter, tagged {@code pool} and {@code reason}</li>
 *   <li>{@code hikaricp.*}: HikariCP's pool meters, for pools plugkit-db builds</li>
 * </ul>
 *
 * <p>{@code outcome} is {@code success}, the
 * {@link io.github.varnitta.plugkit.db.exception.DatabaseException} type such as
 * {@code UniqueViolation}, or {@code application_error}.
 */
public final class MeteredDatabases {

    private MeteredDatabases() {
    }

    public static Database create(DatabaseConfig config, MeterRegistry registry) {
        return PooledDatabase.start(config, MicrometerMetricsRecorder.factory(registry),
                DataSourceConnectionProvider::new);
    }

    public static Database create(DataSource dataSource, MeterRegistry registry) {
        return PooledDatabase.wrap(dataSource, MicrometerMetricsRecorder.factory(registry),
                DataSourceConnectionProvider::new);
    }
}
