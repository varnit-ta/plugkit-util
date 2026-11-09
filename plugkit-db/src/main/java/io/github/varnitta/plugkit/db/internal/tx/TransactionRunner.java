package io.github.varnitta.plugkit.db.internal.tx;

import io.github.varnitta.plugkit.core.internal.retry.Backoff;
import io.github.varnitta.plugkit.db.config.DatabaseConfig;
import io.github.varnitta.plugkit.db.exception.CommitOutcomeUnknown;
import io.github.varnitta.plugkit.db.exception.ConnectionLost;
import io.github.varnitta.plugkit.db.exception.DatabaseException;
import io.github.varnitta.plugkit.db.internal.connection.ConnectionProvider;
import io.github.varnitta.plugkit.db.internal.dialect.Dialect;
import io.github.varnitta.plugkit.db.internal.metrics.MetricsRecorder;
import io.github.varnitta.plugkit.db.internal.statement.StatementExecutor;
import io.github.varnitta.plugkit.db.tx.Transaction;
import io.github.varnitta.plugkit.db.tx.TxOptions;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Runs a callback inside a database transaction: begin, run, commit or roll back, and retry.
 *
 * <p>A {@link io.github.varnitta.plugkit.db.exception.TransientFailure} reruns the whole callback in a
 * new transaction, up to {@code maxAttempts}, with {@link Backoff} between attempts; each attempt gets
 * the full timeout. Any other exception rolls back and is rethrown unchanged. A connection lost during
 * commit becomes {@link CommitOutcomeUnknown} and is never retried.
 *
 * <p>When the {@link ConnectionProvider} returns a connection whose transaction is managed elsewhere,
 * for example by Spring's {@code @Transactional}, the callback joins that transaction: no begin, commit,
 * rollback or retry, and isolation and read-only settings are left to the outer transaction.
 *
 * <p>A statement timeout that differs from the session's is applied with the dialect's override
 * statements and undone with its restore statements when the transaction ends, so a pooled connection
 * never keeps a changed limit.
 */
@Slf4j
public final class TransactionRunner {

    private static final Duration NETWORK_TIMEOUT_MARGIN = Duration.ofSeconds(5);

    private final ConnectionProvider connections;
    private final Dialect dialect;
    private final DatabaseConfig config;
    private final StatementExecutor executor;
    private final MetricsRecorder metrics;

    public TransactionRunner(ConnectionProvider connections, Dialect dialect, DatabaseConfig config,
                             StatementExecutor executor, MetricsRecorder metrics) {
        this.connections = connections;
        this.dialect = dialect;
        this.config = config;
        this.executor = executor;
        this.metrics = metrics;
    }

    public <T> T run(TxOptions options, Function<Transaction, T> work) {
        boolean[] joined = {false};
        for (int attempt = 1; ; attempt++) {
            long start = System.nanoTime();
            try {
                T result = attempt(options, work, joined);
                metrics.transaction(elapsedSince(start), null);
                return result;
            } catch (RuntimeException e) {
                metrics.transaction(elapsedSince(start), e);
                if (!(e instanceof DatabaseException failure) || !failure.isRetryable() || joined[0]
                        || attempt >= options.maxAttempts()) {
                    throw e;
                }
                metrics.retry(failure);
                log.info("Retrying transaction on pool '{}' after {} (attempt {} of {})", config.poolName(),
                        failure.getClass().getSimpleName(), attempt + 1, options.maxAttempts());
                pause(Backoff.delay(attempt), failure);
            }
        }
    }

    private <T> T attempt(TxOptions options, Function<Transaction, T> work, boolean[] joined) {
        Duration timeout = firstNonNull(options.timeout(), TimeoutOverrides.transaction(),
                config.tx().defaultTimeout());
        Duration statementTimeout = firstNonNull(options.statementTimeout(), TimeoutOverrides.statement(),
                config.session().statementTimeout());
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        try {
            Connection connection = connections.acquire();
            try {
                ConnectionTransaction transaction = new ConnectionTransaction(connection, executor, dialect,
                        config.poolName(), metrics, timeout, statementTimeout, deadlineNanos);
                if (connections.isExternallyManaged(connection)) {
                    joined[0] = true;
                    return runJoined(connection, transaction, statementTimeout, work);
                }
                return runOwn(connection, transaction, options, statementTimeout, work);
            } finally {
                connections.release(connection);
            }
        } catch (SQLException e) {
            throw dialect.translate(e, config.poolName());
        }
    }

    private <T> T runOwn(Connection connection, ConnectionTransaction transaction, TxOptions options,
                         Duration statementTimeout, Function<Transaction, T> work) throws SQLException {
        boolean overridden = false;
        try {
            connection.setAutoCommit(false);
            if (options.isolation() != null) {
                connection.setTransactionIsolation(options.isolation().jdbcLevel());
            }
            if (options.readOnly()) {
                connection.setReadOnly(true);
            }
            overridden = overrideStatementTimeout(connection, statementTimeout);
            T result = work.apply(transaction);
            transaction.deactivate();
            if (transaction.deadlinePassed()) {
                throw transaction.timeoutFailure(null);
            }
            commit(connection);
            return result;
        } catch (SQLException | RuntimeException e) {
            rollbackQuietly(connection, e);
            throw e;
        } finally {
            transaction.deactivate();
            if (overridden) {
                restoreStatementTimeout(connection);
            }
        }
    }

    private <T> T runJoined(Connection connection, ConnectionTransaction transaction, Duration statementTimeout,
                            Function<Transaction, T> work) throws SQLException {
        boolean overridden = false;
        try {
            overridden = overrideStatementTimeout(connection, statementTimeout);
            return work.apply(transaction);
        } finally {
            transaction.deactivate();
            if (overridden) {
                restoreStatementTimeout(connection);
            }
        }
    }

    private boolean overrideStatementTimeout(Connection connection, Duration statementTimeout) throws SQLException {
        Duration sessionTimeout = config.session().statementTimeout();
        boolean overridden = !statementTimeout.equals(sessionTimeout);
        if (overridden) {
            execute(connection, dialect.statementTimeoutOverrideSql(statementTimeout));
        }
        if (statementTimeout.compareTo(sessionTimeout) > 0) {
            connection.setNetworkTimeout(Runnable::run,
                    Math.toIntExact(statementTimeout.plus(NETWORK_TIMEOUT_MARGIN).toMillis()));
        }
        return overridden;
    }

    private void restoreStatementTimeout(Connection connection) {
        try {
            execute(connection, dialect.statementTimeoutRestoreSql(config.session()));
        } catch (SQLException e) {
            log.warn("Could not restore the statement timeout on a connection of pool '{}'", config.poolName(), e);
        }
    }

    private static void execute(Connection connection, List<String> sqlStatements) throws SQLException {
        for (String sql : sqlStatements) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    private void commit(Connection connection) {
        try {
            connection.commit();
        } catch (SQLException e) {
            DatabaseException translated = dialect.translate(e, config.poolName());
            if (translated instanceof ConnectionLost) {
                throw new CommitOutcomeUnknown("Connection lost during commit; the transaction may or may not "
                        + "have been applied [pool=" + config.poolName() + "]", translated.sqlState().orElse(null), e);
            }
            throw translated;
        }
    }

    private static Duration firstNonNull(@Nullable Duration explicit, @Nullable Duration override,
                                         Duration fallback) {
        if (explicit != null) {
            return explicit;
        }
        return override != null ? override : fallback;
    }

    private static Duration elapsedSince(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    private static void rollbackQuietly(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static void pause(Duration delay, DatabaseException lastFailure) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lastFailure.addSuppressed(e);
            throw lastFailure;
        }
    }
}
