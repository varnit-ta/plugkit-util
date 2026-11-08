package io.github.varnitta.plugkit.db.internal.tx;

import io.github.varnitta.plugkit.db.exception.DatabaseException;
import io.github.varnitta.plugkit.db.exception.StatementTimeout;
import io.github.varnitta.plugkit.db.exception.TransactionTimeout;
import io.github.varnitta.plugkit.db.internal.dialect.Dialect;
import io.github.varnitta.plugkit.db.internal.statement.StatementExecutor;
import io.github.varnitta.plugkit.db.internal.statement.UncheckedSqlException;
import io.github.varnitta.plugkit.db.internal.validation.ConfigValidator;
import io.github.varnitta.plugkit.db.sql.Params;
import io.github.varnitta.plugkit.db.sql.RowMapper;
import io.github.varnitta.plugkit.db.tx.Transaction;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A {@link Transaction} bound to one connection and one deadline.
 *
 * <p>Before each statement it checks the deadline and sets the statement's query timeout to the time
 * left, capped at the transaction's statement timeout. A statement cancelled because the deadline
 * passed is reported as {@link TransactionTimeout} rather than {@link StatementTimeout}.
 */
final class ConnectionTransaction implements Transaction {

    private final Connection connection;
    private final StatementExecutor executor;
    private final Dialect dialect;
    private final String poolName;
    private final Duration timeout;
    private final Duration statementTimeout;
    private final long deadlineNanos;
    private boolean active = true;

    ConnectionTransaction(Connection connection, StatementExecutor executor, Dialect dialect, String poolName,
                          Duration timeout, Duration statementTimeout, long deadlineNanos) {
        this.connection = connection;
        this.executor = executor;
        this.dialect = dialect;
        this.poolName = poolName;
        this.timeout = timeout;
        this.statementTimeout = statementTimeout;
        this.deadlineNanos = deadlineNanos;
    }

    @Override
    public <T> List<T> query(String sql, Params params, RowMapper<T> mapper) {
        return run(seconds -> executor.query(connection, sql, params, mapper, 0, seconds));
    }

    @Override
    public <T> Optional<T> queryOne(String sql, Params params, RowMapper<T> mapper) {
        return run(seconds -> executor.queryOne(connection, sql, params, mapper, seconds));
    }

    @Override
    public int update(String sql, Params params) {
        return run(seconds -> executor.update(connection, sql, params, seconds));
    }

    @Override
    public int[] batch(String sql, List<Params> rows) {
        return run(seconds -> executor.batch(connection, sql, rows, seconds));
    }

    void deactivate() {
        active = false;
    }

    boolean deadlinePassed() {
        return System.nanoTime() - deadlineNanos >= 0;
    }

    TransactionTimeout timeoutFailure(@Nullable Throwable cause) {
        return new TransactionTimeout("Transaction exceeded its timeout of "
                + ConfigValidator.formatDuration(timeout) + " [pool=" + poolName + "]", null, cause);
    }

    private <T> T run(StatementWork<T> work) {
        if (!active) {
            throw new IllegalStateException("Transaction is no longer active; use it only inside its callback");
        }
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            throw timeoutFailure(null);
        }
        long limitMillis = Math.min(Duration.ofNanos(remainingNanos).toMillis(), statementTimeout.toMillis());
        int queryTimeoutSeconds = (int) Math.max(1, Math.ceilDiv(limitMillis, 1000));
        try {
            return work.run(queryTimeoutSeconds);
        } catch (SQLException e) {
            throw translate(e);
        } catch (UncheckedSqlException e) {
            throw translate(e.sqlException());
        }
    }

    private DatabaseException translate(SQLException exception) {
        DatabaseException translated = dialect.translate(exception, poolName);
        return translated instanceof StatementTimeout && deadlinePassed() ? timeoutFailure(exception) : translated;
    }

    @FunctionalInterface
    private interface StatementWork<T> {
        T run(int queryTimeoutSeconds) throws SQLException;
    }
}
