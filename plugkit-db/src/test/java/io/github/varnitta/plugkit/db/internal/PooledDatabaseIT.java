package io.github.varnitta.plugkit.db.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.varnitta.plugkit.db.Database;
import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.db.Databases;
import io.github.varnitta.plugkit.db.Health;
import io.github.varnitta.plugkit.db.config.DatabaseConfig;
import io.github.varnitta.plugkit.db.config.PoolConfig;
import io.github.varnitta.plugkit.db.config.SessionConfig;
import io.github.varnitta.plugkit.db.dialect.postgres.PostgresDialect;
import io.github.varnitta.plugkit.db.exception.ConnectionLost;
import io.github.varnitta.plugkit.db.exception.DatabaseException;
import io.github.varnitta.plugkit.db.exception.StatementTimeout;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class PooledDatabaseIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

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
        try (PooledDatabase db = PooledDatabase.start(config().build())) {
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

        try (PooledDatabase db = PooledDatabase.start(config)) {
            assertThat(show(db, "statement_timeout")).isEqualTo("2500ms");
            assertThat(show(db, "search_path")).isEqualTo("custom");
        }
    }

    @Test
    void statementTimeoutStopsSlowQueries() {
        DatabaseConfig config = config()
                .session(SessionConfig.builder().statementTimeout(Duration.ofSeconds(1)).build())
                .build();

        try (PooledDatabase db = PooledDatabase.start(config)) {
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

    private static DatabaseConfig.DatabaseConfigBuilder config() {
        return DatabaseConfig.builder()
                .url(POSTGRES.getJdbcUrl())
                .user(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .poolName("it")
                .pool(PoolConfig.builder().maxSize(2).build());
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
