package io.github.varnitta.plugkit.db.internal.dialect.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.db.config.SessionConfig;
import io.github.varnitta.plugkit.db.exception.CheckViolation;
import io.github.varnitta.plugkit.db.exception.ConnectionLost;
import io.github.varnitta.plugkit.db.exception.ConstraintViolation;
import io.github.varnitta.plugkit.db.exception.DatabaseException;
import io.github.varnitta.plugkit.db.exception.Deadlock;
import io.github.varnitta.plugkit.db.exception.ForeignKeyViolation;
import io.github.varnitta.plugkit.db.exception.LockTimeout;
import io.github.varnitta.plugkit.db.exception.NotNullViolation;
import io.github.varnitta.plugkit.db.exception.QueryFailure;
import io.github.varnitta.plugkit.db.exception.StatementTimeout;
import io.github.varnitta.plugkit.db.exception.UniqueViolation;
import io.github.varnitta.plugkit.db.internal.dialect.Dialect;
import io.github.varnitta.plugkit.db.internal.dialect.DialectContractTest;
import io.github.varnitta.plugkit.db.internal.dialect.Dialects;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MySqlDialectTest extends DialectContractTest {

    private final MySqlDialect dialect = new MySqlDialect();

    @Override
    protected Dialect dialect() {
        return dialect;
    }

    @Test
    void isRegisteredForMySql() {
        assertThat(Dialects.forType(DatabaseType.MYSQL)).isInstanceOf(MySqlDialect.class);
        assertThat(dialect.driverClassName()).isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(dialect.driverArtifact()).isEqualTo("com.mysql:mysql-connector-j");
        assertThat(dialect.maxConnectionsQuery()).isEqualTo("select @@max_connections");
    }

    static Stream<Arguments> errorCodes() {
        return Stream.of(
                Arguments.of(1451, "23000", ForeignKeyViolation.class, false),
                Arguments.of(1452, "23000", ForeignKeyViolation.class, false),
                Arguments.of(1048, "23000", NotNullViolation.class, false),
                Arguments.of(1364, "HY000", NotNullViolation.class, false),
                Arguments.of(3819, "HY000", CheckViolation.class, false),
                Arguments.of(1213, "40001", Deadlock.class, true),
                Arguments.of(1205, "HY000", LockTimeout.class, false),
                Arguments.of(3024, "HY000", StatementTimeout.class, false),
                Arguments.of(1317, "70100", StatementTimeout.class, false),
                Arguments.of(1040, "08004", ConnectionLost.class, true),
                Arguments.of(1053, "08S01", ConnectionLost.class, true),
                Arguments.of(1146, "42S02", QueryFailure.class, false),
                Arguments.of(1064, "42000", QueryFailure.class, false));
    }

    @ParameterizedTest
    @MethodSource("errorCodes")
    void translatesErrorCode(int errorCode, String sqlState, Class<? extends DatabaseException> expected,
                             boolean retryable) {
        SQLException original = new SQLException("server text", sqlState, errorCode);

        DatabaseException translated = dialect.translate(original, "orders");

        assertThat(translated).isExactlyInstanceOf(expected).hasCause(original);
        assertThat(translated.isRetryable()).isEqualTo(retryable);
        assertThat(translated).hasMessage("server text [pool=orders, SQLState=" + sqlState + ", errorCode="
                + errorCode + "]");
    }

    @Test
    void duplicateKeyMessageKeepsTheKeyButNotTheValue() {
        SQLException original = new SQLException("Duplicate entry 'alice@example.com' for key 'users.email'",
                "23000", 1062);

        DatabaseException translated = dialect.translate(original, "orders");

        assertThat(translated).isInstanceOf(UniqueViolation.class)
                .hasMessage("Duplicate entry for unique key 'users.email' [pool=orders, SQLState=23000, errorCode=1062]");
        assertThat(((ConstraintViolation) translated).constraintName()).contains("users.email");
    }

    @Test
    void foreignKeyAndCheckConstraintNamesAreRead() {
        ForeignKeyViolation foreignKey = (ForeignKeyViolation) dialect.translate(new SQLException(
                "Cannot add or update a child row: a foreign key constraint fails (`app`.`orders`, CONSTRAINT "
                        + "`orders_user_fk` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`))", "23000", 1452), "p");
        CheckViolation check = (CheckViolation) dialect.translate(
                new SQLException("Check constraint 'balance_positive' is violated.", "HY000", 3819), "p");

        assertThat(foreignKey.constraintName()).contains("orders_user_fk");
        assertThat(check.constraintName()).contains("balance_positive");
    }

    @Test
    void clientSideTimeoutIsStatementTimeout() {
        assertThat(dialect.translate(new SQLTimeoutException("Statement cancelled due to timeout"), "p"))
                .isInstanceOf(StatementTimeout.class);
    }

    @Test
    void batchErrorsAreReadFromTheUnderlyingError() {
        BatchUpdateException batch = new BatchUpdateException("batch failed with 'secret' values", new int[0],
                new SQLException("Duplicate entry 'x' for key 'users.email'", "23000", 1062));

        DatabaseException translated = dialect.translate(batch, "p");

        assertThat(translated).isInstanceOf(UniqueViolation.class).hasMessageNotContaining("secret");
    }

    @Test
    void connectionPropertiesCarryTimeoutsInMillisecondsAndSessionVariables() {
        Map<String, String> properties = dialect.connectionProperties(SessionConfig.defaults(),
                Map.of("sessionVariables", "sql_mode='STRICT_ALL_TABLES'"));

        assertThat(properties)
                .containsEntry("connectTimeout", "5000")
                .containsEntry("socketTimeout", "35000")
                .containsEntry("sessionVariables",
                        "max_execution_time=30000,innodb_lock_wait_timeout=10,sql_mode='STRICT_ALL_TABLES'");
    }

    @Test
    void lockTimeoutIsRoundedUpToWholeSeconds() {
        SessionConfig session = SessionConfig.builder().lockTimeout(Duration.ofMillis(1500)).build();

        assertThat(dialect.connectionProperties(session, Map.of()).get("sessionVariables"))
                .contains("innodb_lock_wait_timeout=2");
        assertThat(dialect.unsupportedSettings(session))
                .anySatisfy(problem -> assertThat(problem).contains("rounded up to 2s"));
    }

    @Test
    void reportsWhatMySqlCannotEnforce() {
        assertThat(dialect.unsupportedSettings(SessionConfig.defaults()))
                .hasSize(2)
                .anySatisfy(problem -> assertThat(problem).startsWith("session.statementTimeout is enforced by MySQL "
                        + "for SELECT statements only"))
                .anySatisfy(problem -> assertThat(problem).startsWith("session.idleInTransactionTimeout is not enforced"));
    }

    @Test
    void statementTimeoutOverrideIsUndoneAfterTheTransaction() {
        assertThat(dialect.statementTimeoutOverrideSql(Duration.ofMinutes(5)))
                .containsExactly("set session max_execution_time = 300000");
        assertThat(dialect.statementTimeoutRestoreSql(SessionConfig.defaults()))
                .containsExactly("set session max_execution_time = 30000");
    }

    @Test
    void sqlSyntaxFollowsMySqlQuoting() {
        assertThat(dialect.sqlSyntax().quoteCharacters()).containsExactlyInAnyOrder('\'', '"', '`');
        assertThat(dialect.sqlSyntax().backslashEscapesQuotes()).isTrue();
        assertThat(dialect.sqlSyntax().hashStartsComment()).isTrue();
        assertThat(dialect.sqlSyntax().dollarQuoting()).isFalse();
    }
}
