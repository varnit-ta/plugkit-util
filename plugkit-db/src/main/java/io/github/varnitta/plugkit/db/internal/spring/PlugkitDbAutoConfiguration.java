package io.github.varnitta.plugkit.db.internal.spring;

import io.github.varnitta.plugkit.db.Database;
import io.github.varnitta.plugkit.db.internal.metrics.MetricsRecorder;
import io.github.varnitta.plugkit.db.internal.metrics.MicrometerMetricsRecorder;
import io.github.varnitta.plugkit.db.internal.pool.PooledDatabase;
import io.github.varnitta.plugkit.db.spring.PlugkitDbProperties;
import io.micrometer.core.instrument.MeterRegistry;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds plugkit-db's own pool when {@code plugkit.db.url} is set.
 *
 * <p>Registers the {@link Database} and exposes its pool as the application's {@link DataSource}, so
 * Spring's transaction manager, {@code @Transactional} and tools such as Flyway use the same pool. Runs
 * before Spring Boot's DataSource auto-configuration, which then backs off.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration"})
@ConditionalOnClass(name = "org.springframework.jdbc.datasource.DataSourceUtils")
@EnableConfigurationProperties(PlugkitDbProperties.class)
public class PlugkitDbAutoConfiguration {

    static MetricsRecorder.Factory metricsOrNone(ObjectProvider<MetricsRecorder.Factory> metrics) {
        return metrics.getIfAvailable(MetricsRecorder.Factory::none);
    }

    /**
     * The plugkit-db pool and the {@link DataSource} bean that exposes it.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "plugkit.db", name = "url")
    static class OwnPoolConfiguration {

        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean(Database.class)
        PooledDatabase plugkitDatabase(PlugkitDbProperties properties,
                                       ObjectProvider<MetricsRecorder.Factory> metrics) {
            return PooledDatabase.start(properties.toDatabaseConfig(), metricsOrNone(metrics),
                    SpringConnectionProvider::new);
        }

        @Bean(destroyMethod = "")
        @ConditionalOnMissingBean(DataSource.class)
        DataSource dataSource(PooledDatabase plugkitDatabase) {
            return plugkitDatabase.dataSource();
        }
    }

    /**
     * Records metrics in the application's Micrometer registry when there is one.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        MetricsRecorder.Factory plugkitDbMetrics(ObjectProvider<MeterRegistry> registries) {
            return poolName -> {
                MeterRegistry registry = registries.getIfUnique();
                return registry == null ? MetricsRecorder.none() : new MicrometerMetricsRecorder(registry, poolName);
            };
        }
    }
}
