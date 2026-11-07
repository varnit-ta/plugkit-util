package io.github.varnitta.plugkit.db;

import io.github.varnitta.plugkit.db.config.DatabaseConfig;
import io.github.varnitta.plugkit.db.config.PoolConfig;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class TestPostgres {

    private static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer("postgres:17-alpine");

    private TestPostgres() {
    }

    public static synchronized PostgreSQLContainer container() {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
        return CONTAINER;
    }

    public static DatabaseConfig.DatabaseConfigBuilder config() {
        PostgreSQLContainer postgres = container();
        return DatabaseConfig.builder()
                .url(postgres.getJdbcUrl())
                .user(postgres.getUsername())
                .password(postgres.getPassword())
                .poolName("it")
                .pool(PoolConfig.builder().maxSize(2).build());
    }
}
