package io.github.varnitta.plugkit.db.internal.pool;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import io.github.varnitta.plugkit.db.config.DatabaseConfig;
import io.github.varnitta.plugkit.db.config.PoolConfig;
import io.github.varnitta.plugkit.db.internal.dialect.postgres.PostgresDialect;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class HikariPoolFactoryTest {

    @Test
    void mapsEverySettingToHikari() {
        DatabaseConfig config = DatabaseConfig.builder()
                .url("jdbc:postgresql://localhost:5432/app")
                .user("app")
                .password("secret")
                .poolName("orders")
                .pool(PoolConfig.builder()
                        .maxSize(20)
                        .minIdle(5)
                        .acquireTimeout(Duration.ofSeconds(2))
                        .maxLifetime(Duration.ofMinutes(20))
                        .idleTimeout(Duration.ofMinutes(5))
                        .keepalive(Duration.ofMinutes(1))
                        .leakDetection(Duration.ofSeconds(30))
                        .build())
                .driverProperty("sslmode", "require")
                .build();

        HikariConfig hikari = HikariPoolFactory.hikariConfig(config, new PostgresDialect());

        assertThat(hikari.getPoolName()).isEqualTo("orders");
        assertThat(hikari.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/app");
        assertThat(hikari.getUsername()).isEqualTo("app");
        assertThat(hikari.getPassword()).isEqualTo("secret");
        assertThat(hikari.isAutoCommit()).isTrue();
        assertThat(hikari.getMaximumPoolSize()).isEqualTo(20);
        assertThat(hikari.getMinimumIdle()).isEqualTo(5);
        assertThat(hikari.getConnectionTimeout()).isEqualTo(2_000);
        assertThat(hikari.getMaxLifetime()).isEqualTo(1_200_000);
        assertThat(hikari.getIdleTimeout()).isEqualTo(300_000);
        assertThat(hikari.getKeepaliveTime()).isEqualTo(60_000);
        assertThat(hikari.getLeakDetectionThreshold()).isEqualTo(30_000);
        assertThat(hikari.getDataSourceProperties())
                .containsEntry("sslmode", "require")
                .containsEntry("socketTimeout", "35")
                .containsEntry("connectTimeout", "5")
                .containsKey("options");
    }

    @Test
    void userAndPasswordAreOptional() {
        HikariConfig hikari = HikariPoolFactory.hikariConfig(
                DatabaseConfig.builder().url("jdbc:postgresql://localhost/app").build(), new PostgresDialect());

        assertThat(hikari.getUsername()).isNull();
        assertThat(hikari.getPassword()).isNull();
    }
}
