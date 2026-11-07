package io.github.varnitta.plugkit.db.internal;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.github.varnitta.plugkit.db.Database;
import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.db.Health;
import io.github.varnitta.plugkit.db.Params;
import io.github.varnitta.plugkit.db.RowMapper;
import io.github.varnitta.plugkit.db.config.DatabaseConfig;
import io.github.varnitta.plugkit.db.dialect.Dialect;
import io.github.varnitta.plugkit.db.dialect.Dialects;
import io.github.varnitta.plugkit.db.exception.AcquireTimeout;
import io.github.varnitta.plugkit.db.exception.QueryFailure;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
    private final StatementExecutor executor;

    private PooledDatabase(DatabaseConfig config, Dialect dialect, HikariDataSource dataSource) {
        this.config = config;
        this.dialect = dialect;
        this.dataSource = dataSource;
        this.executor = new StatementExecutor(config.poolName(), config.slowQueryThreshold());
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
    public <T> List<T> query(String sql, Params params, RowMapper<T> mapper) {
        return withConnection(connection -> executor.query(connection, sql, params, mapper, 0));
    }

    @Override
    public <T> Optional<T> queryOne(String sql, Params params, RowMapper<T> mapper) {
        List<T> rows = withConnection(connection -> executor.query(connection, sql, params, mapper, 2));
        if (rows.size() > 1) {
            throw new QueryFailure("Expected at most one row but the query returned more [pool="
                    + config.poolName() + "]", null, null);
        }
        return rows.stream().findFirst();
    }

    @Override
    public int update(String sql, Params params) {
        return withConnection(connection -> executor.update(connection, sql, params));
    }

    @Override
    public int[] batch(String sql, List<Params> rows) {
        if (rows.isEmpty()) {
            return new int[0];
        }
        return withConnection(connection -> {
            connection.setAutoCommit(false);
            try {
                int[] counts = executor.batch(connection, sql, rows);
                connection.commit();
                return counts;
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(connection, e);
                throw e;
            }
        });
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

    private <T> T withConnection(ConnectionWork<T> work) {
        try (Connection connection = acquire()) {
            return work.run(connection);
        } catch (SQLException e) {
            throw dialect.translate(e, config.poolName());
        } catch (RowAccessException e) {
            throw dialect.translate(e.sqlException(), config.poolName());
        }
    }

    private Connection acquire() throws SQLException {
        try {
            return dataSource.getConnection();
        } catch (SQLTransientConnectionException e) {
            if (e.getCause() instanceof SQLException cause) {
                throw cause;
            }
            throw acquireTimeout(e);
        }
    }

    private AcquireTimeout acquireTimeout(SQLTransientConnectionException cause) {
        HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
        String stats = pool == null ? "" : ", active=" + pool.getActiveConnections() + ", idle="
                + pool.getIdleConnections() + ", waiting=" + pool.getThreadsAwaitingConnection();
        return new AcquireTimeout("No connection available within "
                + config.pool().acquireTimeout().toMillis() + " ms [pool=" + config.poolName() + stats
                + "]. Increase pool.maxSize, or look for slow queries or connections that are not closed.",
                null, cause);
    }

    private void rollbackQuietly(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
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

    @FunctionalInterface
    private interface ConnectionWork<T> {
        T run(Connection connection) throws SQLException;
    }
}
