package io.github.varnitta.plugkit.db.internal.spring;

import io.github.varnitta.plugkit.db.Database;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Reports {@link Database#health()} to Spring Boot's health endpoint, with the database type, pool
 * name, response time and, when down, the problem.
 */
final class DatabaseHealthIndicator implements HealthIndicator {

    private final Database database;

    DatabaseHealthIndicator(Database database) {
        this.database = database;
    }

    @Override
    public Health health() {
        io.github.varnitta.plugkit.core.Health result = database.health();
        Health.Builder builder = result.healthy() ? Health.up() : Health.down();
        builder.withDetail("database", database.databaseType().configName())
                .withDetail("pool", database.poolName())
                .withDetail("responseTimeMs", result.responseTime().toMillis());
        if (result.problem() != null) {
            builder.withDetail("problem", result.problem());
        }
        return builder.build();
    }
}
