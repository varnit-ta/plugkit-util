package io.github.varnitta.plugkit.db.internal.spring;

import io.github.varnitta.plugkit.db.internal.connection.ConnectionProvider;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link ConnectionProvider} that hands out the connection of the current Spring transaction when there
 * is one, so plugkit-db statements and transactions join Spring's {@code @Transactional}.
 *
 * <p>Outside a Spring transaction it behaves like a plain pool: borrow, use, return.
 */
public final class SpringConnectionProvider implements ConnectionProvider {

    private final DataSource dataSource;

    public SpringConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection acquire() throws SQLException {
        try {
            return DataSourceUtils.getConnection(dataSource);
        } catch (CannotGetJdbcConnectionException e) {
            if (e.getCause() instanceof SQLException cause) {
                throw cause;
            }
            throw e;
        }
    }

    @Override
    public void release(Connection connection) {
        DataSourceUtils.releaseConnection(connection, dataSource);
    }

    @Override
    public boolean isExternallyManaged(Connection connection) {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && DataSourceUtils.isConnectionTransactional(connection, dataSource);
    }
}
