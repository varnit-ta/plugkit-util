package io.github.varnitta.plugkit.db.internal.pool;

import io.github.varnitta.plugkit.core.exception.InvalidConfigException;
import io.github.varnitta.plugkit.db.internal.dialect.Dialect;
import java.util.List;

/**
 * Fails fast with the dependency to add when the JDBC driver for the configured database is missing.
 */
final class DriverCheck {

    private DriverCheck() {
    }

    static void requireDriver(Dialect dialect) {
        try {
            Class.forName(dialect.driverClassName(), false, classLoader());
        } catch (ClassNotFoundException e) {
            throw new InvalidConfigException(List.of("database type '" + dialect.databaseType().configName()
                    + "' needs the JDBC driver " + dialect.driverClassName()
                    + ": add the dependency " + dialect.driverArtifact()));
        }
    }

    private static ClassLoader classLoader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : DriverCheck.class.getClassLoader();
    }
}
