package io.github.varnitta.plugkit.db.internal.pool;

import com.zaxxer.hikari.HikariDataSource;
import io.github.varnitta.plugkit.core.Health;
import io.github.varnitta.plugkit.core.exception.InvalidConfigException;
import io.github.varnitta.plugkit.core.internal.lifecycle.InFlightTracker;
import io.github.varnitta.plugkit.db.Database;
import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.db.config.DatabaseConfig;
import io.github.varnitta.plugkit.db.exception.ConnectionLost;
import io.github.varnitta.plugkit.db.exception.DatabaseException;
import io.github.varnitta.plugkit.db.internal.connection.ConnectionProvider;
import io.github.varnitta.plugkit.db.internal.dialect.Dialect;
import io.github.varnitta.plugkit.db.internal.dialect.Dialects;
import io.github.varnitta.plugkit.db.internal.metrics.MetricsRecorder;
import io.github.varnitta.plugkit.db.internal.metrics.MetricsRecorderFactory;
import io.github.varnitta.plugkit.db.internal.statement.SqlFunction;
import io.github.varnitta.plugkit.db.internal.statement.StatementExecutor;
import io.github.varnitta.plugkit.db.internal.statement.UncheckedSqlException;
import io.github.varnitta.plugkit.db.internal.tx.TimeoutOverrides;
import io.github.varnitta.plugkit.db.internal.tx.TransactionRunner;
import io.github.varnitta.plugkit.db.sql.Params;
import io.github.varnitta.plugkit.db.sql.RowMapper;
import io.github.varnitta.plugkit.db.tx.Transaction;
import io.github.varnitta.plugkit.db.tx.TxOptions;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.sql.DataSource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The {@link io.github.varnitta.plugkit.db.Database} implementation.
 *
 * <p>Either owns a HikariCP pool built from a {@link DatabaseConfig} ({@link #start}) or wraps a
 * {@link DataSource} the application already has ({@link #wrap}); a wrapped pool is never closed.
 * Statements get their connection from a {@link ConnectionProvider}, so an integration can make them
 * join its transactions. Errors are translated through the dialect, pool exhaustion is reported as
 * {@link io.github.varnitta.plugkit.db.exception.AcquireTimeout} through
 * {@link TranslatingConnectionProvider}, and transactions are handed to {@link TransactionRunner}.
 *
 * <p>{@link #close()} stops new work, waits up to {@code pool.shutdownTimeout} for running statements
 * and transactions, then closes the pool if it owns it.
 */
@Slf4j
public final class PooledDatabase implements Database {

    private static final int HEALTH_CHECK_TIMEOUT_SECONDS = 2;
    private static final String WRAPPED_POOL_NAME = "datasource";

    private final DatabaseConfig config;
    private final Dialect dialect;
    @Getter
    private final DataSource dataSource;
    private final @Nullable HikariDataSource hikari;
    private final boolean ownsPool;
    private final ConnectionProvider connections;
    private final MetricsRecorder metrics;
    private final StatementExecutor executor;
    private final TransactionRunner transactions;
    private final InFlightTracker inFlight;
    private final AtomicBoolean closing = new AtomicBoolean();

    private PooledDatabase(DatabaseConfig config, Dialect dialect, DataSource dataSource, boolean ownsPool,
                           Function<DataSource, ConnectionProvider> providers, MetricsRecorder metrics) {
        this.config = config;
        this.dialect = dialect;
        this.dataSource = dataSource;
        this.hikari = unwrapHikari(dataSource);
        this.ownsPool = ownsPool;
        this.metrics = metrics;
        this.connections = new TranslatingConnectionProvider(providers.apply(dataSource), config.poolName(), hikari,
                config.pool().acquireTimeout());
        this.executor = new StatementExecutor(config.poolName(), config.slowQueryThreshold());
        this.transactions = new TransactionRunner(connections, dialect, config, executor, metrics);
        this.inFlight = new InFlightTracker("Database pool '" + config.poolName() + "'");
    }

    public static PooledDatabase start(DatabaseConfig config, MetricsRecorderFactory metrics,
                                       Function<DataSource, ConnectionProvider> providers) {
        Dialect dialect = Dialects.forType(config.databaseType());
        DriverCheck.requireDriver(dialect);
        MetricsRecorder recorder = metrics.forPool(config.poolName());
        HikariDataSource pool = HikariPoolFactory.start(config, dialect, recorder);
        PooledDatabase database = new PooledDatabase(config, dialect, pool, true, providers, recorder);
        database.warnIfPoolIsLarge();
        log.info("Started database pool '{}' ({}, maxSize={})", config.poolName(), config.databaseType().configName(),
                config.pool().maxSize());
        return database;
    }

    public static PooledDatabase wrap(DataSource dataSource, MetricsRecorderFactory metrics,
                                      Function<DataSource, ConnectionProvider> providers) {
        HikariDataSource hikari = unwrapHikari(dataSource);
        String poolName = hikari != null && hikari.getPoolName() != null ? hikari.getPoolName() : WRAPPED_POOL_NAME;
        DatabaseConfig config = configFor(dataSource, poolName);
        Dialect dialect = Dialects.forType(config.databaseType());
        log.info("Using existing DataSource as database pool '{}' ({})", poolName, config.databaseType().configName());
        return new PooledDatabase(config, dialect, dataSource, false, providers, metrics.forPool(poolName));
    }

    @Override
    public <T> List<T> query(String sql, Params params, RowMapper<T> mapper) {
        if (TimeoutOverrides.statement() != null) {
            return inTransaction(transaction -> transaction.query(sql, params, mapper));
        }
        return runTracked(() -> withConnection("query", connection -> executor.query(connection, sql, params, mapper,
                0, StatementExecutor.NO_QUERY_TIMEOUT)));
    }

    @Override
    public <T> Optional<T> queryOne(String sql, Params params, RowMapper<T> mapper) {
        if (TimeoutOverrides.statement() != null) {
            return inTransaction(transaction -> transaction.queryOne(sql, params, mapper));
        }
        return runTracked(() -> withConnection("queryOne", connection -> executor.queryOne(connection, sql, params,
                mapper, StatementExecutor.NO_QUERY_TIMEOUT)));
    }

    @Override
    public int update(String sql, Params params) {
        if (TimeoutOverrides.statement() != null) {
            return inTransaction(transaction -> transaction.update(sql, params));
        }
        return runTracked(() -> withConnection("update", connection -> executor.update(connection, sql, params,
                StatementExecutor.NO_QUERY_TIMEOUT)));
    }

    @Override
    public int[] batch(String sql, List<Params> rows) {
        if (rows.isEmpty()) {
            return new int[0];
        }
        return inTransaction(TxOptions.defaults(), transaction -> transaction.batch(sql, rows));
    }

    @Override
    public <T> T inTransaction(TxOptions options, Function<Transaction, T> work) {
        return runTracked(() -> transactions.run(options, work));
    }

    @Override
    public DatabaseType databaseType() {
        return config.databaseType();
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
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        Duration timeout = config.pool().shutdownTimeout();
        boolean interrupted = false;
        try {
            if (!inFlight.stopNewWorkAndWait(timeout)) {
                log.warn("Closing database pool '{}' with {} statements or transactions still running after "
                        + "waiting {} ms", config.poolName(), inFlight.runningCount(), timeout.toMillis());
            }
        } catch (InterruptedException e) {
            interrupted = true;
            log.warn("Interrupted while waiting for running work on database pool '{}'; closing now",
                    config.poolName());
        }
        if (ownsPool && hikari != null && !hikari.isClosed()) {
            hikari.close();
        }
        log.info("Stopped database pool '{}'", config.poolName());
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    static boolean isLargeShare(int maxSize, int maxConnections) {
        return maxSize * 2L > maxConnections;
    }

    private <T> T runTracked(Supplier<T> work) {
        inFlight.startWork();
        try {
            return work.get();
        } finally {
            inFlight.finishWork();
        }
    }

    private <T> T withConnection(String operation, SqlFunction<Connection, T> work) {
        long start = System.nanoTime();
        try {
            T result = runOnConnection(work);
            metrics.statement(operation, Duration.ofNanos(System.nanoTime() - start), null);
            return result;
        } catch (RuntimeException e) {
            metrics.statement(operation, Duration.ofNanos(System.nanoTime() - start), e);
            throw e;
        }
    }

    private <T> T runOnConnection(SqlFunction<Connection, T> work) {
        try {
            Connection connection = connections.acquire();
            try {
                return work.apply(connection);
            } finally {
                connections.release(connection);
            }
        } catch (SQLException e) {
            throw dialect.translate(e, config.poolName());
        } catch (UncheckedSqlException e) {
            throw dialect.translate(e.sqlException(), config.poolName());
        }
    }


    private void warnIfPoolIsLarge() {
        OptionalInt maxConnections = readMaxConnections();
        if (maxConnections.isPresent() && isLargeShare(config.pool().maxSize(), maxConnections.getAsInt())) {
            log.warn("Database pool '{}' has maxSize {}, more than half of the database's max_connections ({}). "
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
            log.debug("Could not read max_connections for pool '{}'", config.poolName(), e);
            return OptionalInt.empty();
        }
    }

    private static DatabaseConfig configFor(DataSource dataSource, String poolName) {
        String url;
        try (Connection connection = dataSource.getConnection()) {
            url = connection.getMetaData().getURL();
        } catch (SQLException e) {
            throw new ConnectionLost("Could not connect through the application's DataSource [pool=" + poolName
                    + "]: " + e.getMessage(), e.getSQLState(), e);
        }
        try {
            return DatabaseConfig.builder().url(url).poolName(poolName).build();
        } catch (InvalidConfigException e) {
            throw new InvalidConfigException(List.of("The application's DataSource connects to a database "
                    + "plugkit-db doesn't support: " + String.join("; ", e.problems())));
        }
    }

    private static @Nullable HikariDataSource unwrapHikari(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikari) {
            return hikari;
        }
        try {
            return dataSource.isWrapperFor(HikariDataSource.class) ? dataSource.unwrap(HikariDataSource.class) : null;
        } catch (SQLException e) {
            return null;
        }
    }
}
