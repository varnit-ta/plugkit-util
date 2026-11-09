package io.github.varnitta.plugkit.db.internal.dialect.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.db.config.SessionConfig;
import io.github.varnitta.plugkit.db.exception.CheckViolation;
import io.github.varnitta.plugkit.db.exception.ConnectionLost;
import io.github.varnitta.plugkit.db.exception.ConstraintViolation;
import io.github.varnitta.plugkit.db.exception.DatabaseException;
import io.github.varnitta.plugkit.db.exception.Deadlock;
import io.github.varnitta.plugkit.db.exception.ForeignKeyViolation;
import io.github.varnitta.plugkit.core.exception.InvalidConfigException;
import io.github.varnitta.plugkit.db.exception.LockTimeout;
import io.github.varnitta.plugkit.db.exception.NotNullViolation;
import io.github.varnitta.plugkit.db.exception.QueryFailure;
import io.github.varnitta.plugkit.db.exception.SerializationFailure;
import io.github.varnitta.plugkit.db.exception.StatementTimeout;
import io.github.varnitta.plugkit.db.exception.TransactionTimeout;
import io.github.varnitta.plugkit.db.exception.UniqueViolation;
import io.github.varnitta.plugkit.db.internal.dialect.Dialects;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

class PostgresDialectTest {

    private final PostgresDialect dialect = new PostgresDialect();

    @Test
    void isRegisteredForPostgres() {
        assertThat(Dialects.forType(DatabaseType.POSTGRES)).isInstanceOf(PostgresDialect.class);
        assertThat(dialect.databaseType()).isEqualTo(DatabaseType.POSTGRES);
        assertThat(dialect.driverClassName()).isEqualTo("org.postgresql.Driver");
        assertThat(dialect.driverArtifact()).isEqualTo("org.postgresql:postgresql");
        assertThat(dialect.maxConnectionsQuery()).isEqualTo("show max_connections");
        assertThat(dialect.localStatementTimeoutSql(Duration.ofMinutes(5)))
                .isEqualTo("set local statement_timeout = 300000");
    }

    static Stream<Arguments> sqlStates() {
        return Stream.of(
                Arguments.of("23505", UniqueViolation.class, false),
                Arguments.of("23503", ForeignKeyViolation.class, false),
                Arguments.of("23502", NotNullViolation.class, false),
                Arguments.of("23514", CheckViolation.class, false),
                Arguments.of("40001", SerializationFailure.class, true),
                Arguments.of("40P01", Deadlock.class, true),
                Arguments.of("57014", StatementTimeout.class, false),
                Arguments.of("55P03", LockTimeout.class, false),
                Arguments.of("25P03", TransactionTimeout.class, false),
                Arguments.of("57P01", ConnectionLost.class, true),
                Arguments.of("57P02", ConnectionLost.class, true),
                Arguments.of("57P03", ConnectionLost.class, true),
                Arguments.of("08006", ConnectionLost.class, true),
                Arguments.of("08001", ConnectionLost.class, true),
                Arguments.of("42P01", QueryFailure.class, false),
                Arguments.of("42601", QueryFailure.class, false));
    }

    @ParameterizedTest
    @MethodSource("sqlStates")
    void translatesSqlState(String sqlState, Class<? extends DatabaseException> expected, boolean retryable) {
        SQLException original = new SQLException("boom", sqlState);

        DatabaseException translated = dialect.translate(original, "orders");

        assertThat(translated).isExactlyInstanceOf(expected).hasCause(original);
        assertThat(translated.isRetryable()).isEqualTo(retryable);
        assertThat(translated.sqlState()).contains(sqlState);
        assertThat(translated).hasMessage("boom [pool=orders, SQLState=" + sqlState + "]");
    }

    @Test
    void missingSqlStateBecomesQueryFailure() {
        DatabaseException translated = dialect.translate(new SQLException("no state"), "orders");

        assertThat(translated).isExactlyInstanceOf(QueryFailure.class).hasMessage("no state [pool=orders]");
        assertThat(translated.sqlState()).isEmpty();
    }

    @Test
    void uniqueViolationExposesConstraintNameWithoutLeakingValues() {
        PSQLException original = serverError("23505",
                "duplicate key value violates unique constraint \"users_email_key\"",
                "Key (email)=(alice@example.com) already exists.",
                "users_email_key");

        DatabaseException translated = dialect.translate(original, "orders");

        assertThat(translated).isInstanceOf(UniqueViolation.class);
        assertThat(((ConstraintViolation) translated).constraintName()).contains("users_email_key");
        assertThat(translated.getMessage())
                .isEqualTo("duplicate key value violates unique constraint \"users_email_key\" [pool=orders, SQLState=23505]")
                .doesNotContain("alice@example.com");
    }

    @Test
    void constraintNameIsEmptyForPlainSqlException() {
        ConstraintViolation translated = (ConstraintViolation) dialect.translate(new SQLException("dup", "23505"), "orders");

        assertThat(translated.constraintName()).isEmpty();
    }

    @Test
    void connectionPropertiesCarryTimeoutsAndSafeDefaults() {
        Map<String, String> properties = dialect.connectionProperties(SessionConfig.defaults(), Map.of());

        assertThat(properties)
                .containsEntry("connectTimeout", "5")
                .containsEntry("socketTimeout", "35")
                .containsEntry("logServerErrorDetail", "false")
                .containsEntry("options", "-c statement_timeout=30000 -c lock_timeout=10000"
                        + " -c idle_in_transaction_session_timeout=60000");
    }

    @Test
    void socketTimeoutFollowsStatementTimeoutRoundedUp() {
        SessionConfig session = SessionConfig.builder().statementTimeout(Duration.ofMillis(1500)).build();

        assertThat(dialect.connectionProperties(session, Map.of())).containsEntry("socketTimeout", "7");
    }

    @Test
    void userPropertiesArePassedThroughAndUserOptionsAreAppended() {
        Map<String, String> properties = dialect.connectionProperties(SessionConfig.defaults(), Map.of(
                "sslmode", "require",
                "logServerErrorDetail", "true",
                "options", "-c search_path=app"));

        assertThat(properties)
                .containsEntry("sslmode", "require")
                .containsEntry("logServerErrorDetail", "true");
        assertThat(properties.get("options"))
                .startsWith("-c statement_timeout=30000")
                .endsWith(" -c search_path=app");
    }

    @Test
    void derivedTimeoutsCannotBeSetByUser() {
        assertThatThrownBy(() -> dialect.connectionProperties(SessionConfig.defaults(),
                Map.of("socketTimeout", "0", "connectTimeout", "1")))
                .isInstanceOf(InvalidConfigException.class)
                .extracting(e -> ((InvalidConfigException) e).problems())
                .isEqualTo(List.of(
                        "driverProperties.connectTimeout is managed by plugkit-db and cannot be set",
                        "driverProperties.socketTimeout is managed by plugkit-db and cannot be set"));
    }

    @Test
    void connectionPropertiesAreImmutable() {
        Map<String, String> properties = dialect.connectionProperties(SessionConfig.defaults(), Map.of());

        assertThatThrownBy(() -> properties.put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    private static PSQLException serverError(String sqlState, String message, String detail, String constraint) {
        return new PSQLException(new ServerErrorMessage(
                "SERROR\0C" + sqlState + "\0M" + message + "\0D" + detail + "\0n" + constraint + "\0"));
    }
}
