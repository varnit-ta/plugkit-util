package io.github.varnitta.plugkit.db.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import io.github.varnitta.plugkit.db.Database;
import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.db.Databases;
import io.github.varnitta.plugkit.db.TestPostgres;
import io.github.varnitta.plugkit.db.exception.QueryFailure;
import io.github.varnitta.plugkit.db.sql.Params;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class MeteredDatabasesIT {

    @Test
    void recordsStatementTransactionAndPoolMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        try (Database db = MeteredDatabases.create(TestPostgres.config().poolName("metered").build(), registry)) {
            db.query("select 1 as one", Params.empty(), row -> row.getInt("one"));
            assertThatThrownBy(() -> db.update("select * from missing_table", Params.empty()))
                    .isInstanceOf(QueryFailure.class);
            db.inTransaction(tx -> tx.query("select 1 as one", Params.empty(), row -> row.getInt("one")));

            assertThat(registry.get("plugkit.db.statement")
                    .tags("pool", "metered", "operation", "query", "outcome", "success").timer().count()).isEqualTo(2);
            assertThat(registry.get("plugkit.db.statement")
                    .tags("operation", "update", "outcome", "QueryFailure").timer().count()).isEqualTo(1);
            assertThat(registry.get("plugkit.db.transaction").tags("pool", "metered", "outcome", "success")
                    .timer().count()).isEqualTo(1);
            assertThat(registry.get("hikaricp.connections.max").tags("pool", "metered").gauge().value())
                    .isEqualTo(2.0);
        }
    }

    @Test
    void wrapsAnExistingPoolWithoutClosingIt() {
        HikariDataSource pool = new HikariDataSource();
        pool.setJdbcUrl(TestPostgres.container().getJdbcUrl());
        pool.setUsername(TestPostgres.container().getUsername());
        pool.setPassword(TestPostgres.container().getPassword());
        pool.setPoolName("app-pool");
        pool.setMaximumPoolSize(2);

        try (pool) {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            Database db = MeteredDatabases.create(pool, registry);

            assertThat(db.databaseType()).isEqualTo(DatabaseType.POSTGRES);
            assertThat(db.poolName()).isEqualTo("app-pool");
            assertThat(db.query("select 1 as one", Params.empty(), row -> row.getInt("one"))).containsExactly(1);
            db.close();

            assertThat(pool.isClosed()).isFalse();
            assertThat(Databases.create(pool).health().healthy()).isTrue();
            assertThat(registry.get("plugkit.db.statement").tags("pool", "app-pool").timer().count()).isEqualTo(1);
        }
    }
}
