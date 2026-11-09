package io.github.varnitta.plugkit.db.internal.dialect;

import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.db.config.SessionConfig;
import io.github.varnitta.plugkit.db.exception.DatabaseException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;

/**
 * Database-specific behaviour: driver details, connection properties and error translation.
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

    String localStatementTimeoutSql(Duration statementTimeout);

    DatabaseException translate(SQLException exception, String poolName);
}
