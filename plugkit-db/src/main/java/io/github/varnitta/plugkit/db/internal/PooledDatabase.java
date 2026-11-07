package io.github.varnitta.plugkit.db.internal;

import com.zaxxer.hikari.HikariDataSource;
import io.github.varnitta.plugkit.db.Database;
import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.db.Health;
import io.github.varnitta.plugkit.db.config.DatabaseConfig;
import io.github.varnitta.plugkit.db.dialect.Dialect;
import io.github.varnitta.plugkit.db.dialect.Dialects;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.OptionalInt;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PooledDatabase implements Database {

    private static final Logger LOG = LoggerFactory.getLogger(PooledDatabase.class);
    private static final int HEALTH_CHECK_TIMEOUT_SECONDS = 2;

    private final DatabaseConfig config;
    private final Dialect dialect;
    private final HikariDataSource dataSource;

    private PooledDatabase(DatabaseConfig config, Dialect dialect, HikariDataSource dataSource) {
        this.config = config;
        this.dialect = dialect;
        this.dataSource = dataSource;
    }

    public static PooledDatabase start(DatabaseConfig config) {
        Dialect dialect = Dialects.forType(config.type());
        DriverCheck.requireDriver(dialect);
        HikariDataSource dataSource = HikariPoolFactory.start(config, dialect);
        PooledDatabase database = new PooledDatabase(config, dialect, dataSource);
        database.warnIfPoolIsLarge();
        LOG.info("Started database pool '{}' ({}, maxSize={})", config.poolName(), config.type().configName(),
                config.pool().maxSize());
        return database;
    }

    @Override
    public DatabaseType type() {
        return config.type();
    }

    @Override
    public String poolName() {
        return config.poolName();
    }

    @Override
    public Health health() {
        long start = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(HEALTH_CHECK_TIMEOUT_SECONDS);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            return valid ? Health.up(elapsed) : Health.down(elapsed, "connection did not respond");
        } catch (SQLException e) {
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            return Health.down(elapsed, dialect.translate(e, config.poolName()).getMessage());
        }
    }

    @Override
    public void close() {
        if (!dataSource.isClosed()) {
            dataSource.close();
            LOG.info("Stopped database pool '{}'", config.poolName());
        }
    }

    DataSource dataSource() {
        return dataSource;
    }

    static boolean isLargeShare(int maxSize, int maxConnections) {
        return maxSize * 2L > maxConnections;
    }

    private void warnIfPoolIsLarge() {
        OptionalInt maxConnections = readMaxConnections();
        if (maxConnections.isPresent() && isLargeShare(config.pool().maxSize(), maxConnections.getAsInt())) {
            LOG.warn("Database pool '{}' has maxSize {}, more than half of the database's max_connections ({}). "
                            + "A few application instances can exhaust the database's connections.",
                    config.poolName(), config.pool().maxSize(), maxConnections.getAsInt());
        }
    }

    private OptionalInt readMaxConnections() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(dialect.maxConnectionsQuery())) {
            return result.next() ? OptionalInt.of(Integer.parseInt(result.getString(1).trim())) : OptionalInt.empty();
        } catch (SQLException | NumberFormatException e) {
            LOG.debug("Could not read max_connections for pool '{}'", config.poolName(), e);
            return OptionalInt.empty();
        }
    }
}
