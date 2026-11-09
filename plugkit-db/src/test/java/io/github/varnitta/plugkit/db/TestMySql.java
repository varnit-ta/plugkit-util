package io.github.varnitta.plugkit.db;

import io.github.varnitta.plugkit.db.config.DatabaseConfig;
import io.github.varnitta.plugkit.db.config.PoolConfig;
import org.testcontainers.mysql.MySQLContainer;

/**
 * One MySQL container shared by the MySQL integration tests, started on first use.
 */
public final class TestMySql {

    private static final MySQLContainer CONTAINER = new MySQLContainer("mysql:8.4");

    private TestMySql() {
    }

    public static synchronized MySQLContainer container() {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
        return CONTAINER;
    }

    public static DatabaseConfig.DatabaseConfigBuilder config() {
        MySQLContainer mysql = container();
        return DatabaseConfig.builder()
                .url(mysql.getJdbcUrl())
                .user(mysql.getUsername())
                .password(mysql.getPassword())
                .poolName("mysql-it")
                .pool(PoolConfig.builder().maxSize(4).build());
    }
}
