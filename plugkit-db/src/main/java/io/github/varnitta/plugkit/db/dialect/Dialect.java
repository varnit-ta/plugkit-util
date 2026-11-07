package io.github.varnitta.plugkit.db.dialect;

import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.db.config.SessionConfig;
import io.github.varnitta.plugkit.db.exception.DatabaseException;
import java.sql.SQLException;
import java.util.Map;

public interface Dialect {

    DatabaseType type();

    String driverClassName();

    String driverArtifact();

    Map<String, String> connectionProperties(SessionConfig session, Map<String, String> userProperties);

    String maxConnectionsQuery();

    DatabaseException translate(SQLException exception, String poolName);
}
