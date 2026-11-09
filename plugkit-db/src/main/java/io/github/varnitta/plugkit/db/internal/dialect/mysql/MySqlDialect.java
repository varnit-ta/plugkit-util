package io.github.varnitta.plugkit.db.internal.dialect.mysql;

import io.github.varnitta.plugkit.core.internal.validation.ConfigValidator;
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
import io.github.varnitta.plugkit.db.exception.StatementTimeout;
import io.github.varnitta.plugkit.db.exception.UniqueViolation;
import io.github.varnitta.plugkit.db.internal.dialect.Dialect;
import io.github.varnitta.plugkit.db.internal.statement.SqlSyntax;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * MySQL dialect, for MySQL Connector/J.
 *
 * <p>Session timeouts are sent through the driver's {@code sessionVariables} property:
 * {@code max_execution_time} for the statement timeout and {@code innodb_lock_wait_timeout} for the lock
 * timeout. MySQL has no transaction-scoped settings, so a statement-timeout override is set on the session
 * and restored after the transaction. Errors are translated from MySQL error codes. The dialect uses only
 * standard JDBC types, so it needs no driver classes at compile time.
 *
 * <p>MySQL can't enforce everything plugkit-db configures; {@link #unsupportedSettings} reports the gaps.
 * Duplicate-key errors from MySQL contain the duplicate value, so their message is rebuilt with only the
 * key name.
 */
public final class MySqlDialect implements Dialect {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration SOCKET_TIMEOUT_MARGIN = Duration.ofSeconds(5);
    private static final List<String> MANAGED_PROPERTIES = List.of("connectTimeout", "socketTimeout");
    private static final SqlSyntax SYNTAX = new SqlSyntax(Set.of('\'', '"', '`'), true, true, true, false);
    private static final Pattern UNIQUE_KEY = Pattern.compile("for key '([^']+)'");
    private static final Pattern FOREIGN_KEY = Pattern.compile("CONSTRAINT `([^`]+)`");
    private static final Pattern CHECK_CONSTRAINT = Pattern.compile("Check constraint '([^']+)'");

    @Override
    public DatabaseType databaseType() {
        return DatabaseType.MYSQL;
    }

    @Override
    public String driverClassName() {
        return "com.mysql.cj.jdbc.Driver";
    }

    @Override
    public String driverArtifact() {
        return "com.mysql:mysql-connector-j";
    }

    @Override
    public Map<String, String> connectionProperties(SessionConfig session, Map<String, String> userProperties) {
        ConfigValidator validator = ConfigValidator.section("driverProperties");
        MANAGED_PROPERTIES.forEach(key -> validator.require(key, !userProperties.containsKey(key),
                "is managed by plugkit-db and cannot be set"));
        validator.throwIfInvalid();

        Map<String, String> properties = new LinkedHashMap<>(userProperties);
        properties.put("connectTimeout", String.valueOf(CONNECT_TIMEOUT.toMillis()));
        properties.put("socketTimeout", String.valueOf(session.statementTimeout().plus(SOCKET_TIMEOUT_MARGIN).toMillis()));
        properties.merge("sessionVariables", sessionVariables(session), (userVariables, plugkitVariables) ->
                plugkitVariables + "," + userVariables);
        return Collections.unmodifiableMap(properties);
    }

    @Override
    public String maxConnectionsQuery() {
        return "select @@max_connections";
    }

    @Override
    public List<String> statementTimeoutOverrideSql(Duration statementTimeout) {
        return List.of("set session max_execution_time = " + statementTimeout.toMillis());
    }

    @Override
    public List<String> statementTimeoutRestoreSql(SessionConfig session) {
        return List.of("set session max_execution_time = " + session.statementTimeout().toMillis());
    }

    @Override
    public SqlSyntax sqlSyntax() {
        return SYNTAX;
    }

    @Override
    public List<String> unsupportedSettings(SessionConfig session) {
        List<String> problems = new ArrayList<>();
        problems.add("session.statementTimeout is enforced by MySQL for SELECT statements only "
                + "(max_execution_time); other statements are stopped by the driver's socket timeout "
                + "(statement timeout + 5s) and fail with ConnectionLost");
        problems.add("session.idleInTransactionTimeout is not enforced, because MySQL has no equivalent setting");
        if (session.lockTimeout().toMillis() % 1000 != 0) {
            problems.add("session.lockTimeout " + ConfigValidator.formatDuration(session.lockTimeout())
                    + " is rounded up to " + lockTimeoutSeconds(session) + "s, because MySQL's "
                    + "innodb_lock_wait_timeout is in whole seconds");
        }
        return List.copyOf(problems);
    }

    @Override
    public DatabaseException translate(SQLException exception, String poolName) {
        SQLException source = sourceOf(exception);
        String sqlState = source.getSQLState() != null ? source.getSQLState() : exception.getSQLState();
        int errorCode = source.getErrorCode();
        String serverText = source.getMessage() == null ? "Database error" : source.getMessage();
        String context = " [pool=" + poolName + (sqlState == null ? "" : ", SQLState=" + sqlState)
                + (errorCode == 0 ? "" : ", errorCode=" + errorCode) + "]";
        if (source instanceof SQLTimeoutException || "70100".equals(sqlState)) {
            return new StatementTimeout(serverText + context, sqlState, exception);
        }
        return switch (errorCode) {
            case 1062, 1586 -> {
                String key = find(UNIQUE_KEY, serverText);
                yield new UniqueViolation("Duplicate entry for unique key " + (key == null ? "" : "'" + key + "'")
                        + context, key, sqlState, exception);
            }
            case 1216, 1217, 1451, 1452 ->
                    new ForeignKeyViolation(serverText + context, find(FOREIGN_KEY, serverText), sqlState, exception);
            case 1048, 1364 -> new NotNullViolation(serverText + context, null, sqlState, exception);
            case 3819 ->
                    new CheckViolation(serverText + context, find(CHECK_CONSTRAINT, serverText), sqlState, exception);
            case 1213 -> new Deadlock(serverText + context, sqlState, exception);
            case 1205 -> new LockTimeout(serverText + context, sqlState, exception);
            case 3024, 1317 -> new StatementTimeout(serverText + context, sqlState, exception);
            case 1040, 1053 -> new ConnectionLost(serverText + context, sqlState, exception);
            default -> sqlState != null && sqlState.startsWith("08")
                    ? new ConnectionLost(serverText + context, sqlState, exception)
                    : new QueryFailure(serverText + context, sqlState, exception);
        };
    }

    static long lockTimeoutSeconds(SessionConfig session) {
        return Math.max(1, Math.ceilDiv(session.lockTimeout().toMillis(), 1000));
    }

    private static String sessionVariables(SessionConfig session) {
        return "max_execution_time=" + session.statementTimeout().toMillis()
                + ",innodb_lock_wait_timeout=" + lockTimeoutSeconds(session);
    }

    private static @Nullable String find(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static SQLException sourceOf(SQLException exception) {
        if (!(exception instanceof BatchUpdateException)) {
            return exception;
        }
        SQLException next = exception.getNextException();
        if (next != null) {
            return next;
        }
        return exception.getCause() instanceof SQLException cause ? cause : exception;
    }
}
