package io.github.varnitta.plugkit.db.internal.pool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.varnitta.plugkit.db.Database;
import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.db.Databases;
import io.github.varnitta.plugkit.db.Health;
import io.github.varnitta.plugkit.db.TestPostgres;
import io.github.varnitta.plugkit.db.config.DatabaseConfig;
import io.github.varnitta.plugkit.db.config.PoolConfig;
import io.github.varnitta.plugkit.db.config.SessionConfig;
import io.github.varnitta.plugkit.db.exception.ConnectionLost;
import io.github.varnitta.plugkit.db.exception.DatabaseException;
import io.github.varnitta.plugkit.db.exception.StatementTimeout;
import io.github.varnitta.plugkit.db.internal.dialect.postgres.PostgresDialect;
import io.github.varnitta.plugkit.db.sql.Params;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PooledDatabaseIT {

    @Test
    void startsAndReportsHealthy() {
        try (Database db = Databases.create(config().build())) {
            Health health = db.health();

            assertThat(db.type()).isEqualTo(DatabaseType.POSTGRES);
            assertThat(db.poolName()).isEqualTo("it");
            assertThat(health.healthy()).isTrue();
            assertThat(health.problem()).isNull();
            assertThat(health.responseTime()).isPositive();
        }
    }

    @Test
    void appliesDefaultSessionTimeouts() throws SQLException {
        try (PooledDatabase db = (PooledDatabase) Databases.create(config().build())) {
            assertThat(show(db, "statement_timeout")).isEqualTo("30s");
            assertThat(show(db, "lock_timeout")).isEqualTo("10s");
            assertThat(show(db, "idle_in_transaction_session_timeout")).isEqualTo("1min");
        }
    }

    @Test
    void appliesCustomSessionTimeoutsAndUserOptions() throws SQLException {
        DatabaseConfig config = config()
                .session(SessionConfig.builder().statementTimeout(Duration.ofMillis(2500)).build())
                .driverProperty("options", "-c search_path=custom")
                .build();

        try (PooledDatabase db = (PooledDatabase) Databases.create(config)) {
            assertThat(show(db, "statement_timeout")).isEqualTo("2500ms");
            assertThat(show(db, "search_path")).isEqualTo("custom");
        }
    }

    @Test
    void statementTimeoutStopsSlowQueries() {
        DatabaseConfig config = config()
                .session(SessionConfig.builder().statementTimeout(Duration.ofSeconds(1)).build())
                .build();

        try (PooledDatabase db = (PooledDatabase) Databases.create(config)) {
            assertThatThrownBy(() -> {
                try (Connection connection = db.dataSource().getConnection();
                     Statement statement = connection.createStatement()) {
                    statement.execute("select pg_sleep(3)");
                }
            }).isInstanceOf(SQLException.class)
                    .satisfies(e -> assertThat(new PostgresDialect().translate((SQLException) e, "it"))
                            .isInstanceOf(StatementTimeout.class));
        }
    }

    @Test
    void wrongPasswordFailsStartupWithoutLeakingIt() {
        DatabaseConfig config = config().password("not-the-password").build();

        assertThatThrownBy(() -> Databases.create(config))
                .isInstanceOf(DatabaseException.class)
                .hasMessageContaining("[pool=it, SQLState=28P01]")
                .hasMessageNotContaining("not-the-password");
    }

    @Test
    void unreachableDatabaseFailsFast() {
        DatabaseConfig config = DatabaseConfig.builder()
                .url("jdbc:postgresql://127.0.0.1:1/app")
                .user("app")
                .poolName("unreachable")
                .build();

        long start = System.nanoTime();
        assertThatThrownBy(() -> Databases.create(config))
                .isInstanceOf(ConnectionLost.class)
                .hasMessageContaining("pool=unreachable");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void closedDatabaseReportsUnhealthy() {
        Database db = Databases.create(config().build());
        db.close();
        db.close();

        Health health = db.health();

        assertThat(health.healthy()).isFalse();
        assertThat(health.problem()).contains("pool=it");
    }

    @Test
    void closeWaitsForRunningQueriesThenRejectsNewWork() throws Exception {
        Database db = Databases.create(config().poolName("graceful").build());
        CompletableFuture<List<Integer>> running = CompletableFuture.supplyAsync(() ->
                db.query("select 1 as one from pg_sleep(1)", Params.empty(), row -> row.getInt("one")));
        Thread.sleep(200);

        long start = System.nanoTime();
        db.close();
        Duration closing = Duration.ofNanos(System.nanoTime() - start);

        assertThat(running.get(5, TimeUnit.SECONDS)).containsExactly(1);
        assertThat(closing).isGreaterThan(Duration.ofMillis(500));
        assertThatThrownBy(() -> db.query("select 1", Params.empty(), row -> 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database pool 'graceful' is closed");
    }

    @Test
    void closeStopsWaitingAfterTheShutdownTimeout() throws Exception {
        Database db = Databases.create(config()
                .poolName("impatient")
                .pool(PoolConfig.builder().maxSize(2).shutdownTimeout(Duration.ofMillis(200)).build())
                .build());
        CompletableFuture<List<Integer>> running = CompletableFuture.supplyAsync(() ->
                db.query("select 1 as one from pg_sleep(5)", Params.empty(), row -> row.getInt("one")));
        Thread.sleep(200);

        long start = System.nanoTime();
        db.close();

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
        assertThatThrownBy(() -> running.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(DatabaseException.class);
    }

    private static DatabaseConfig.DatabaseConfigBuilder config() {
        return TestPostgres.config();
    }

    private static String show(PooledDatabase db, String setting) throws SQLException {
        try (Connection connection = db.dataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("show " + setting)) {
            result.next();
            return result.getString(1);
        }
    }
}
