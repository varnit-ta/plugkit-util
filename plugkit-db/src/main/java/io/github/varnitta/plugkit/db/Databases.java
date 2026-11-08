package io.github.varnitta.plugkit.db;

import io.github.varnitta.plugkit.db.config.DatabaseConfig;
import io.github.varnitta.plugkit.db.internal.pool.PooledDatabase;

/**
 * Entry point for creating {@link Database} instances.
 *
 * <pre>{@code
 * Database db = Databases.create(DatabaseConfig.builder()
 *         .url("jdbc:postgresql://localhost:5432/app")
 *         .user("app")
 *         .password(secret)
 *         .build());
 * }</pre>
 */
public final class Databases {

    private Databases() {
    }

    public static Database create(DatabaseConfig config) {
        return PooledDatabase.start(config);
    }
}
