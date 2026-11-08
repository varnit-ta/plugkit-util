package io.github.varnitta.plugkit.db.internal.spring;

import io.github.varnitta.plugkit.db.Database;
import io.github.varnitta.plugkit.db.internal.metrics.MetricsRecorder;
import io.github.varnitta.plugkit.db.internal.pool.PooledDatabase;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;

/**
 * Uses the application's existing {@link DataSource} when {@code plugkit.db.url} isn't set.
 *
 * <p>Runs after Spring Boot's DataSource auto-configuration, so a pool configured with
 * {@code spring.datasource.*} or defined by the application is picked up. That pool is never closed by
 * plugkit-db, and plugkit-db's driver and session settings don't apply to it.
 */
@AutoConfiguration(
        after = PlugkitDbAutoConfiguration.class,
        afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.jdbc.datasource.DataSourceUtils")
@ConditionalOnSingleCandidate(DataSource.class)
@ConditionalOnMissingBean(Database.class)
public class PlugkitDbDataSourceAutoConfiguration {

    @Bean(destroyMethod = "close")
    PooledDatabase plugkitDatabase(DataSource dataSource, ObjectProvider<MetricsRecorder.Factory> metrics) {
        return PooledDatabase.wrap(dataSource, PlugkitDbAutoConfiguration.metricsOrNone(metrics),
                SpringConnectionProvider::new);
    }
}
