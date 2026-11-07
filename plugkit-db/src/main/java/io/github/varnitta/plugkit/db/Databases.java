package io.github.varnitta.plugkit.db;

import io.github.varnitta.plugkit.db.config.DatabaseConfig;
import io.github.varnitta.plugkit.db.internal.PooledDatabase;

public final class Databases {

    private Databases() {
    }

    public static Database create(DatabaseConfig config) {
        return PooledDatabase.start(config);
    }
}
