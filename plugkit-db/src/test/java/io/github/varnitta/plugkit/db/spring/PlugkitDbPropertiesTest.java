package io.github.varnitta.plugkit.db.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.db.config.DatabaseConfig;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlugkitDbPropertiesTest {

    private static final PlugkitDbProperties.Pool NO_POOL =
            new PlugkitDbProperties.Pool(null, null, null, null, null, null, null);
    private static final PlugkitDbProperties.Session NO_SESSION = new PlugkitDbProperties.Session(null, null, null);
    private static final PlugkitDbProperties.Tx NO_TX = new PlugkitDbProperties.Tx(null);

    @Test
    void unsetValuesFallBackToConfigDefaults() {
        PlugkitDbProperties properties = new PlugkitDbProperties(null, "jdbc:postgresql://localhost/app", null, null,
                null, NO_POOL, NO_SESSION, NO_TX, null, null);

        assertThat(properties.toDatabaseConfig())
                .isEqualTo(DatabaseConfig.builder().url("jdbc:postgresql://localhost/app").build());
    }

    @Test
    void everyValueIsMapped() {
        PlugkitDbProperties properties = new PlugkitDbProperties(DatabaseType.POSTGRES,
                "jdbc:postgresql://localhost/app", "app", "secret", "orders",
                new PlugkitDbProperties.Pool(20, 5, Duration.ofSeconds(2), Duration.ofMinutes(20),
                        Duration.ofMinutes(5), Duration.ofMinutes(1), Duration.ofSeconds(30)),
                new PlugkitDbProperties.Session(Duration.ofSeconds(10), Duration.ofSeconds(3), Duration.ofSeconds(20)),
                new PlugkitDbProperties.Tx(Duration.ofMinutes(2)),
                Duration.ofMillis(500), Map.of("sslmode", "require"));

        DatabaseConfig config = properties.toDatabaseConfig();

        assertThat(config.type()).isEqualTo(DatabaseType.POSTGRES);
        assertThat(config.user()).isEqualTo("app");
        assertThat(config.password()).isEqualTo("secret");
        assertThat(config.poolName()).isEqualTo("orders");
        assertThat(config.pool().maxSize()).isEqualTo(20);
        assertThat(config.pool().minIdle()).isEqualTo(5);
        assertThat(config.pool().leakDetection()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.session().statementTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.session().lockTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(config.tx().defaultTimeout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(config.slowQueryThreshold()).isEqualTo(Duration.ofMillis(500));
        assertThat(config.driverProperties()).containsEntry("sslmode", "require");
    }

    @Test
    void toStringHidesThePasswordAndDriverPropertyValues() {
        PlugkitDbProperties properties = new PlugkitDbProperties(null, "jdbc:postgresql://localhost/app", "app",
                "hunter2", null, NO_POOL, NO_SESSION, NO_TX, null, Map.of("sslPassword", "hunter2"));

        assertThat(properties.toString())
                .doesNotContain("hunter2")
                .contains("password=****")
                .contains("driverProperties=[sslPassword]");
    }
}
