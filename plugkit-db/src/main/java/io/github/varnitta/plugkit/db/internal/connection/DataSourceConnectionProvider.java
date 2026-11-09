package io.github.varnitta.plugkit.db.internal.connection;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * {@link ConnectionProvider} that borrows a connection from a {@link DataSource} and closes it after
 * use. No connection is ever externally managed.
 */
public final class DataSourceConnectionProvider implements ConnectionProvider {

    private final DataSource dataSource;

    public DataSourceConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection acquire() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void release(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public boolean isExternallyManaged(Connection connection) {
        return false;
    }
}
