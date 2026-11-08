package io.github.varnitta.plugkit.db.internal.tx;

import static java.util.Objects.requireNonNullElse;

import io.github.varnitta.plugkit.db.config.DatabaseConfig;
import io.github.varnitta.plugkit.db.exception.CommitOutcomeUnknown;
import io.github.varnitta.plugkit.db.exception.ConnectionLost;
import io.github.varnitta.plugkit.db.exception.DatabaseException;
import io.github.varnitta.plugkit.db.internal.dialect.Dialect;
import io.github.varnitta.plugkit.db.internal.statement.StatementExecutor;
import io.github.varnitta.plugkit.db.tx.Transaction;
import io.github.varnitta.plugkit.db.tx.TxOptions;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a callback inside a database transaction: begin, run, commit or roll back, and retry.
 *
 * <p>A {@link io.github.varnitta.plugkit.db.exception.TransientFailure} reruns the whole callback in a
 * new transaction, up to {@code maxAttempts}, with {@link Backoff} between attempts; each attempt gets
 * the full timeout. Any other exception rolls back and is rethrown unchanged. A connection lost during
 * commit becomes {@link CommitOutcomeUnknown} and is never retried.
 */
public final class TransactionRunner {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionRunner.class);
    private static final Duration NETWORK_TIMEOUT_MARGIN = Duration.ofSeconds(5);

    private final ConnectionSource connections;
    private final Dialect dialect;
    private final DatabaseConfig config;
    private final StatementExecutor executor;

    public TransactionRunner(ConnectionSource connections, Dialect dialect, DatabaseConfig config,
                      StatementExecutor executor) {
        this.connections = connections;
        this.dialect = dialect;
        this.config = config;
        this.executor = executor;
    }

    @FunctionalInterface
    public interface ConnectionSource {
        Connection acquire() throws SQLException;
    }

    public <T> T run(TxOptions options, Function<Transaction, T> work) {
        for (int attempt = 1; ; attempt++) {
            try {
                return attempt(options, work);
            } catch (DatabaseException e) {
                if (!e.isRetryable() || attempt >= options.maxAttempts()) {
                    throw e;
                }
                LOG.info("Retrying transaction on pool '{}' after {} (attempt {} of {})", config.poolName(),
                        e.getClass().getSimpleName(), attempt + 1, options.maxAttempts());
                pause(Backoff.delay(attempt), e);
            }
        }
    }

    private <T> T attempt(TxOptions options, Function<Transaction, T> work) {
        Duration timeout = requireNonNullElse(options.timeout(), config.tx().defaultTimeout());
        Duration statementTimeout = requireNonNullElse(options.statementTimeout(),
                config.session().statementTimeout());
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        try (Connection connection = connections.acquire()) {
            ConnectionTransaction transaction = new ConnectionTransaction(connection, executor, dialect,
                    config.poolName(), timeout, statementTimeout, deadlineNanos);
            try {
                begin(connection, options, statementTimeout);
                T result = work.apply(transaction);
                transaction.deactivate();
                if (transaction.deadlinePassed()) {
                    throw transaction.timeoutFailure(null);
                }
                commit(connection);
                return result;
            } catch (SQLException e) {
                rollbackQuietly(connection, e);
                throw dialect.translate(e, config.poolName());
            } catch (RuntimeException e) {
                rollbackQuietly(connection, e);
                throw e;
            } finally {
                transaction.deactivate();
            }
        } catch (SQLException e) {
            throw dialect.translate(e, config.poolName());
        }
    }

    private void begin(Connection connection, TxOptions options, Duration statementTimeout) throws SQLException {
        connection.setAutoCommit(false);
        if (options.isolation() != null) {
            connection.setTransactionIsolation(options.isolation().jdbcLevel());
        }
        if (options.readOnly()) {
            connection.setReadOnly(true);
        }
        if (options.statementTimeout() != null) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(dialect.localStatementTimeoutSql(statementTimeout));
            }
        }
        if (statementTimeout.compareTo(config.session().statementTimeout()) > 0) {
            connection.setNetworkTimeout(Runnable::run,
                    Math.toIntExact(statementTimeout.plus(NETWORK_TIMEOUT_MARGIN).toMillis()));
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
