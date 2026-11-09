package io.github.varnitta.plugkit.db.internal.pool;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.github.varnitta.plugkit.db.exception.AcquireTimeout;
import io.github.varnitta.plugkit.db.internal.connection.ConnectionProvider;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Wraps a {@link ConnectionProvider} and explains why no connection could be obtained.
 *
 * <p>When the pool is exhausted, the pool's generic "connection not available" error becomes an
 * {@link AcquireTimeout} with the pool's active, idle and waiting counts and what to do about it. When
 * the pool couldn't create a connection at all, the driver's original error is passed on instead, so the
 * dialect can translate it, for example into {@code ConnectionLost}.
 */
final class TranslatingConnectionProvider implements ConnectionProvider {

    private final ConnectionProvider delegate;
    private final String poolName;
    private final @Nullable HikariDataSource hikari;
    private final Duration configuredAcquireTimeout;

    TranslatingConnectionProvider(ConnectionProvider delegate, String poolName, @Nullable HikariDataSource hikari,
                                  Duration configuredAcquireTimeout) {
        this.delegate = delegate;
        this.poolName = poolName;
        this.hikari = hikari;
        this.configuredAcquireTimeout = configuredAcquireTimeout;
    }

    @Override
    public Connection acquire() throws SQLException {
        try {
            return delegate.acquire();
        } catch (SQLTransientConnectionException e) {
            if (e.getCause() instanceof SQLException cause) {
                throw cause;
            }
            throw acquireTimeout(e);
        }
    }

    @Override
    public void release(Connection connection) throws SQLException {
        delegate.release(connection);
    }

    @Override
    public boolean isExternallyManaged(Connection connection) {
        return delegate.isExternallyManaged(connection);
    }

    private AcquireTimeout acquireTimeout(SQLTransientConnectionException cause) {
        HikariPoolMXBean pool = hikari == null ? null : hikari.getHikariPoolMXBean();
        String stats = pool == null ? "" : ", active=" + pool.getActiveConnections() + ", idle="
                + pool.getIdleConnections() + ", waiting=" + pool.getThreadsAwaitingConnection();
        long timeoutMillis = hikari != null ? hikari.getConnectionTimeout() : configuredAcquireTimeout.toMillis();
        return new AcquireTimeout("No connection available within " + timeoutMillis + " ms [pool=" + poolName
                + stats + "]. Increase pool.maxSize, or look for slow queries or connections that are not closed.",
                null, cause);
    }
}
