package io.github.varnitta.plugkit.db.internal.dialect.postgres;

import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.db.config.SessionConfig;
import io.github.varnitta.plugkit.db.exception.CheckViolation;
import io.github.varnitta.plugkit.db.exception.ConnectionLost;
import io.github.varnitta.plugkit.db.exception.DatabaseException;
import io.github.varnitta.plugkit.db.exception.Deadlock;
import io.github.varnitta.plugkit.db.exception.ForeignKeyViolation;
import io.github.varnitta.plugkit.db.exception.LockTimeout;
import io.github.varnitta.plugkit.db.exception.NotNullViolation;
import io.github.varnitta.plugkit.db.exception.QueryFailure;
import io.github.varnitta.plugkit.db.exception.SerializationFailure;
import io.github.varnitta.plugkit.db.exception.StatementTimeout;
import io.github.varnitta.plugkit.db.exception.TransactionTimeout;
import io.github.varnitta.plugkit.db.exception.UniqueViolation;
import io.github.varnitta.plugkit.db.internal.dialect.Dialect;
import io.github.varnitta.plugkit.db.internal.statement.SqlSyntax;
import io.github.varnitta.plugkit.core.internal.validation.ConfigValidator;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/**
 * PostgreSQL dialect.
 *
 * <p>Sends session timeouts through the driver's {@code options} property, overrides the statement
 * timeout per transaction with {@code set local} (which ends with the transaction), derives
 * {@code socketTimeout} from the statement timeout, disables server error detail in driver messages and
 * maps SQLState codes to {@link io.github.varnitta.plugkit.db.exception.DatabaseException} types.
 * Messages use the server's primary error text, never its {@code DETAIL}, so row values don't leak.
 */
public final class PostgresDialect implements Dialect {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final SqlSyntax SYNTAX = new SqlSyntax(Set.of('\'', '"'), false, false, false, true);
    static final Duration SOCKET_TIMEOUT_MARGIN = Duration.ofSeconds(5);
    private static final List<String> DERIVED_PROPERTIES = List.of("connectTimeout", "socketTimeout");

    @Override
    public DatabaseType databaseType() {
        return DatabaseType.POSTGRES;
    }

    @Override
    public String driverClassName() {
        return "org.postgresql.Driver";
    }

    @Override
    public String driverArtifact() {
        return "org.postgresql:postgresql";
    }

    @Override
    public Map<String, String> connectionProperties(SessionConfig session, Map<String, String> userProperties) {
        ConfigValidator validator = ConfigValidator.section("driverProperties");
        DERIVED_PROPERTIES.forEach(key -> validator.require(key, !userProperties.containsKey(key),
                "is managed by plugkit-db and cannot be set"));
        validator.throwIfInvalid();

        Map<String, String> properties = new LinkedHashMap<>(userProperties);
        properties.put("connectTimeout", String.valueOf(CONNECT_TIMEOUT.toSeconds()));
        properties.put("socketTimeout", String.valueOf(socketTimeoutSeconds(session)));
        properties.putIfAbsent("logServerErrorDetail", "false");
        properties.merge("options", sessionOptions(session), (userOptions, plugkitOptions) ->
                plugkitOptions + " " + userOptions);
        return Collections.unmodifiableMap(properties);
    }

    @Override
    public String maxConnectionsQuery() {
        return "show max_connections";
    }

    @Override
    public List<String> statementTimeoutOverrideSql(Duration statementTimeout) {
        return List.of("set local statement_timeout = " + statementTimeout.toMillis());
    }

    @Override
    public List<String> statementTimeoutRestoreSql(SessionConfig session) {
        return List.of();
    }

    @Override
    public SqlSyntax sqlSyntax() {
        return SYNTAX;
    }

    @Override
    public List<String> unsupportedSettings(SessionConfig session) {
        return List.of();
    }

    @Override
    public DatabaseException translate(SQLException exception, String poolName) {
        SQLException source = sourceOf(exception);
        String sqlState = source.getSQLState() != null ? source.getSQLState() : exception.getSQLState();
        String message = describe(exception, sqlState, poolName);
        if (sqlState == null) {
            return new QueryFailure(message, null, exception);
        }
        return switch (sqlState) {
            case "23505" -> new UniqueViolation(message, constraintName(exception), sqlState, exception);
            case "23503" -> new ForeignKeyViolation(message, constraintName(exception), sqlState, exception);
            case "23502" -> new NotNullViolation(message, constraintName(exception), sqlState, exception);
            case "23514" -> new CheckViolation(message, constraintName(exception), sqlState, exception);
            case "40001" -> new SerializationFailure(message, sqlState, exception);
            case "40P01" -> new Deadlock(message, sqlState, exception);
            case "57014" -> new StatementTimeout(message, sqlState, exception);
            case "55P03" -> new LockTimeout(message, sqlState, exception);
            case "25P03" -> new TransactionTimeout(message, sqlState, exception);
            case "57P01", "57P02", "57P03" -> new ConnectionLost(message, sqlState, exception);
            case String state when state.startsWith("08") -> new ConnectionLost(message, sqlState, exception);
            default -> new QueryFailure(message, sqlState, exception);
        };
    }

    static long socketTimeoutSeconds(SessionConfig session) {
        return Math.ceilDiv(session.statementTimeout().plus(SOCKET_TIMEOUT_MARGIN).toMillis(), 1000);
    }

    private static String sessionOptions(SessionConfig session) {
        return "-c statement_timeout=" + session.statementTimeout().toMillis()
                + " -c lock_timeout=" + session.lockTimeout().toMillis()
                + " -c idle_in_transaction_session_timeout=" + session.idleInTransactionTimeout().toMillis();
    }

    private static String describe(SQLException exception, @Nullable String sqlState, String poolName) {
        ServerErrorMessage server = serverMessage(exception);
        String serverText = server == null ? null : server.getMessage();
        String text = serverText != null ? serverText
                : exception instanceof BatchUpdateException ? "Batch statement failed"
                : exception.getMessage();
        return (text == null ? "Database error" : text)
                + " [pool=" + poolName + (sqlState == null ? "" : ", SQLState=" + sqlState) + "]";
    }

    private static @Nullable String constraintName(SQLException exception) {
        ServerErrorMessage server = serverMessage(exception);
        return server == null ? null : server.getConstraint();
    }

    private static @Nullable ServerErrorMessage serverMessage(SQLException exception) {
        return sourceOf(exception) instanceof PSQLException psql ? psql.getServerErrorMessage() : null;
    }

    private static SQLException sourceOf(SQLException exception) {
        SQLException next = exception instanceof BatchUpdateException ? exception.getNextException() : null;
        return next != null ? next : exception;
    }
}
