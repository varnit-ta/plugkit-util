package io.github.varnitta.plugkit.db.dialect;

import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.db.dialect.postgres.PostgresDialect;

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
