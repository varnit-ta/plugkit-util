package io.github.varnitta.plugkit.db.internal.connection;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Where statements get their connection from.
 *
 * <p>The default borrows from the pool and closes the connection afterwards. An integration such as
 * Spring can return the connection of a transaction it manages instead; plugkit-db then joins that
 * transaction rather than starting its own.
 */
public interface ConnectionProvider {

    Connection acquire() throws SQLException;

    void release(Connection connection) throws SQLException;

    boolean isExternallyManaged(Connection connection);
}
