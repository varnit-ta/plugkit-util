package io.github.varnitta.plugkit.db.internal;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool.PoolInitializationException;
import io.github.varnitta.plugkit.db.config.DatabaseConfig;
import io.github.varnitta.plugkit.db.config.PoolConfig;
import io.github.varnitta.plugkit.db.dialect.Dialect;
import io.github.varnitta.plugkit.db.exception.ConnectionLost;
import java.sql.SQLException;

final class HikariPoolFactory {

    private HikariPoolFactory() {
    }

    static HikariDataSource start(DatabaseConfig config, Dialect dialect) {
        try {
            return new HikariDataSource(hikariConfig(config, dialect));
        } catch (PoolInitializationException e) {
            if (e.getCause() instanceof SQLException sqlException) {
                throw dialect.translate(sqlException, config.poolName());
            }
            throw new ConnectionLost("Could not start the connection pool [pool=" + config.poolName() + "]",
                    null, e);
        }
    }

    static HikariConfig hikariConfig(DatabaseConfig config, Dialect dialect) {
        PoolConfig pool = config.pool();
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName(config.poolName());
        hikari.setJdbcUrl(config.url());
        hikari.setUsername(config.user());
        hikari.setPassword(config.password());
        hikari.setAutoCommit(true);
        hikari.setMaximumPoolSize(pool.maxSize());
        hikari.setMinimumIdle(pool.minIdle());
        hikari.setConnectionTimeout(pool.acquireTimeout().toMillis());
        hikari.setMaxLifetime(pool.maxLifetime().toMillis());
        hikari.setIdleTimeout(pool.idleTimeout().toMillis());
        hikari.setKeepaliveTime(pool.keepalive().toMillis());
        hikari.setLeakDetectionThreshold(pool.leakDetection().toMillis());
        dialect.connectionProperties(config.session(), config.driverProperties())
                .forEach(hikari::addDataSourceProperty);
        return hikari;
    }
}
