package io.github.varnitta.plugkit.db.internal.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import io.github.varnitta.plugkit.db.Database;
import io.github.varnitta.plugkit.db.TestPostgres;
import io.github.varnitta.plugkit.db.exception.SerializationFailure;
import io.github.varnitta.plugkit.db.exception.StatementTimeout;
import io.github.varnitta.plugkit.db.internal.pool.PooledDatabase;
import io.github.varnitta.plugkit.db.spring.RetryOnTransient;
import io.github.varnitta.plugkit.db.spring.Timeouts;
import io.github.varnitta.plugkit.db.sql.Params;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class SpringIntegrationIT {

    private static final AutoConfigurations PLUGKIT = AutoConfigurations.of(PlugkitDbAutoConfiguration.class,
            PlugkitDbDataSourceAutoConfiguration.class, PlugkitDbSupportAutoConfiguration.class);
    private static final AutoConfigurations BOOT_JDBC = AutoConfigurations.of(DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class, TransactionAutoConfiguration.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(PLUGKIT)
            .withConfiguration(BOOT_JDBC);

    @Test
    void plugkitUrlBuildsItsOwnPoolAndExposesItAsTheDataSource() {
        runner.withPropertyValues(plugkitProperties("own")).run(context -> {
            Database database = context.getBean(Database.class);
            DataSource dataSource = context.getBean(DataSource.class);

            assertThat(context).hasSingleBean(DataSource.class);
            assertThat(database.poolName()).isEqualTo("own");
            assertThat(dataSource).isSameAs(((PooledDatabase) database).dataSource());
            assertThat(((HikariDataSource) dataSource).getPoolName()).isEqualTo("own");
            assertThat(context.getBean("plugkitDbHealthIndicator", HealthIndicator.class).health().getStatus())
                    .isEqualTo(Status.UP);
        });
    }

    @Test
    void springDatasourcePropertiesAreWrapped() {
        runner.withPropertyValues(
                "spring.datasource.url=" + TestPostgres.container().getJdbcUrl(),
                "spring.datasource.username=" + TestPostgres.container().getUsername(),
                "spring.datasource.password=" + TestPostgres.container().getPassword(),
                "spring.datasource.hikari.pool-name=boot-pool").run(context -> {
            Database database = context.getBean(Database.class);

            assertThat(database.poolName()).isEqualTo("boot-pool");
            assertThat(((PooledDatabase) database).dataSource()).isSameAs(context.getBean(DataSource.class));
            assertThat(database.query("select 1 as one", Params.empty(), row -> row.getInt("one"))).containsExactly(1);
        });
    }

    @Test
    void nothingIsCreatedWithoutConfiguration() {
        new ApplicationContextRunner().withConfiguration(PLUGKIT).run(context ->
                assertThat(context).doesNotHaveBean(Database.class).doesNotHaveBean(DataSource.class));
    }

    @Test
    void statementsJoinSpringTransactions() {
        runner.withPropertyValues(plugkitProperties("joined")).withUserConfiguration(Services.class).run(context -> {
            Database database = context.getBean(Database.class);
            AccountService service = context.getBean(AccountService.class);
            createAccounts(database);

            assertThatThrownBy(() -> service.insertTwiceThenFail(database)).hasMessage("boom");
            assertThat(countAccounts(database)).isZero();

            service.insertTwice(database);
            assertThat(countAccounts(database)).isEqualTo(2);
        });
    }

    @Test
    void retryOnTransientRerunsTheTransaction() {
        runner.withPropertyValues(plugkitProperties("retry")).withUserConfiguration(Services.class).run(context -> {
            Database database = context.getBean(Database.class);
            AccountService service = context.getBean(AccountService.class);
            createAccounts(database);

            service.failOnceWithSerializationFailure(database);

            assertThat(service.attempts()).isEqualTo(2);
            assertThat(countAccounts(database)).isEqualTo(1);
        });
    }

    @Test
    void retryIsSkippedInsideAnOuterTransaction() {
        runner.withPropertyValues(plugkitProperties("outer")).withUserConfiguration(Services.class).run(context -> {
            Database database = context.getBean(Database.class);
            OuterService outer = context.getBean(OuterService.class);
            AccountService service = context.getBean(AccountService.class);
            createAccounts(database);

            assertThatThrownBy(() -> outer.callRetryingService(database)).isInstanceOf(SerializationFailure.class);

            assertThat(service.attempts()).isEqualTo(1);
        });
    }

    @Test
    void timeoutsAnnotationAllowsLongerStatements() {
        runner.withPropertyValues(plugkitProperties("timeouts"))
                .withPropertyValues("plugkit.db.session.statement-timeout=1s")
                .withUserConfiguration(Services.class).run(context -> {
                    Database database = context.getBean(Database.class);
                    ReportService reports = context.getBean(ReportService.class);

                    assertThatThrownBy(() -> reports.slowQuery(database)).isInstanceOf(StatementTimeout.class);
                    assertThat(reports.slowQueryWithLongerTimeout(database)).containsExactly(1);
                    assertThat(reports.slowQueryInTransactionWithLongerTimeout(database)).containsExactly(1);
                    assertThatThrownBy(() -> reports.slowQuery(database)).isInstanceOf(StatementTimeout.class);
                });
    }

    @Test
    void metricsAreRecordedWhenAMeterRegistryExists() {
        runner.withPropertyValues(plugkitProperties("metered")).withUserConfiguration(Metrics.class).run(context -> {
            context.getBean(Database.class).query("select 1 as one", Params.empty(), row -> row.getInt("one"));

            MeterRegistry registry = context.getBean(MeterRegistry.class);
            assertThat(registry.get("plugkit.db.statement").tags("pool", "metered", "outcome", "success").timer().count())
                    .isEqualTo(1);
        });
    }

    private static String[] plugkitProperties(String poolName) {
        return new String[] {
                "plugkit.db.url=" + TestPostgres.container().getJdbcUrl(),
                "plugkit.db.user=" + TestPostgres.container().getUsername(),
                "plugkit.db.password=" + TestPostgres.container().getPassword(),
                "plugkit.db.pool-name=" + poolName,
                "plugkit.db.pool.max-size=2"
        };
    }

    private static void createAccounts(Database database) {
        database.update("create table if not exists spring_accounts (id serial primary key, name text)", Params.empty());
        database.update("truncate spring_accounts", Params.empty());
    }

    private static long countAccounts(Database database) {
        return database.queryOne("select count(*) as c from spring_accounts", Params.empty(),
                row -> row.require("c", Long.class)).orElseThrow();
    }

    @Configuration(proxyBeanMethods = false)
    static class Metrics {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Services {

        @Bean
        AccountService accountService() {
            return new AccountService();
        }

        @Bean
        OuterService outerService(AccountService accountService) {
            return new OuterService(accountService);
        }

        @Bean
        ReportService reportService() {
            return new ReportService();
        }
    }

    public static class AccountService {

        private final AtomicInteger attempts = new AtomicInteger();

        public int attempts() {
            return attempts.get();
        }

        @Transactional
        public void insertTwiceThenFail(Database database) {
            insertTwice(database);
            throw new IllegalStateException("boom");
        }

        @Transactional
        public void insertTwice(Database database) {
            database.update("insert into spring_accounts (name) values ('a')", Params.empty());
            database.inTransaction(tx -> tx.update("insert into spring_accounts (name) values ('b')", Params.empty()));
        }

        @RetryOnTransient(maxAttempts = 3)
        @Transactional
        public void failOnceWithSerializationFailure(Database database) {
            database.update("insert into spring_accounts (name) values ('x')", Params.empty());
            if (attempts.incrementAndGet() == 1) {
                throw new SerializationFailure("simulated", "40001", null);
            }
        }
    }

    public static class OuterService {

        private final AccountService accounts;

        OuterService(AccountService accounts) {
            this.accounts = accounts;
        }

        @Transactional
        public void callRetryingService(Database database) {
            accounts.failOnceWithSerializationFailure(database);
        }
    }

    public static class ReportService {

        public List<Integer> slowQuery(Database database) {
            return database.query("select 1 as one from pg_sleep(2)", Params.empty(), row -> row.getInt("one"));
        }

        @Timeouts(statement = "10s")
        public List<Integer> slowQueryWithLongerTimeout(Database database) {
            return slowQuery(database);
        }

        @Timeouts(statement = "10s")
        @Transactional
        public List<Integer> slowQueryInTransactionWithLongerTimeout(Database database) {
            return slowQuery(database);
        }
    }
}
