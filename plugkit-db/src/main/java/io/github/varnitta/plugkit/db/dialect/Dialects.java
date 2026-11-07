package io.github.varnitta.plugkit.db.dialect;

import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.db.dialect.postgres.PostgresDialect;

/**
 * Maps each {@link io.github.varnitta.plugkit.db.DatabaseType} to its {@link Dialect}.
 *
 * <p>The mapping is an exhaustive {@code switch}, so a new database type doesn't compile until its
 * dialect is registered.
 */
public final class Dialects {

    private static final Dialect POSTGRES = new PostgresDialect();

    private Dialects() {
    }

    public static Dialect forType(DatabaseType type) {
        return switch (type) {
            case POSTGRES -> POSTGRES;
        };
    }
}
