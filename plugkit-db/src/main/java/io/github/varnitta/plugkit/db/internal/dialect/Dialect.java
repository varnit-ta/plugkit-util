package io.github.varnitta.plugkit.db.internal.dialect;

import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.db.config.SessionConfig;
import io.github.varnitta.plugkit.db.exception.DatabaseException;
import io.github.varnitta.plugkit.db.internal.statement.SqlSyntax;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Database-specific behaviour: driver details, connection properties, statement-timeout overrides, SQL
 * quoting rules, settings the database can't enforce, and error translation.
 *
 * <p>A statement-timeout override is applied with {@link #statementTimeoutOverrideSql} when a
 * transaction needs a different limit, and undone with {@link #statementTimeoutRestoreSql} afterwards.
 * A database whose override already ends with the transaction returns no restore statements.
 *
 * <p>Dialects are internal. Each {@link io.github.varnitta.plugkit.db.DatabaseType} has exactly one,
 * looked up through {@link Dialects}.
 */
public interface Dialect {

    DatabaseType databaseType();

    String driverClassName();

    String driverArtifact();

    Map<String, String> connectionProperties(SessionConfig session, Map<String, String> userProperties);

    String maxConnectionsQuery();

    List<String> statementTimeoutOverrideSql(Duration statementTimeout);

    List<String> statementTimeoutRestoreSql(SessionConfig session);

    SqlSyntax sqlSyntax();

    List<String> unsupportedSettings(SessionConfig session);

    DatabaseException translate(SQLException exception, String poolName);
}
