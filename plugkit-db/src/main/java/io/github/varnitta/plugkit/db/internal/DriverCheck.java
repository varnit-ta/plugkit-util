package io.github.varnitta.plugkit.db.internal;

import io.github.varnitta.plugkit.db.dialect.Dialect;
import io.github.varnitta.plugkit.db.exception.InvalidConfigException;
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
            throw new InvalidConfigException(List.of("type '" + dialect.type().configName()
                    + "' needs the JDBC driver " + dialect.driverClassName()
                    + ": add the dependency " + dialect.driverArtifact()));
        }
    }

    private static ClassLoader classLoader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : DriverCheck.class.getClassLoader();
    }
}
