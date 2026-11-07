package io.github.varnitta.plugkit.db;

import java.util.List;
import java.util.Optional;

/**
 * A pooled connection to one database, with methods to run SQL.
 *
 * <p>Instances are created with {@link Databases#create} and are thread-safe. Each call borrows a
 * connection from the pool, runs the statement and returns the connection. Errors are thrown as
 * {@link io.github.varnitta.plugkit.db.exception.DatabaseException} subtypes.
 *
 * <pre>{@code
 * try (Database db = Databases.create(config)) {
 *     List<User> users = db.query("select * from users where status = :status",
 *             Params.of("status", "ACTIVE"), User::from);
 * }
 * }</pre>
 *
 * <p>Close the database when the application stops; closing releases every pooled connection.
 */
public interface Database extends AutoCloseable {

    <T> List<T> query(String sql, Params params, RowMapper<T> mapper);

    <T> Optional<T> queryOne(String sql, Params params, RowMapper<T> mapper);

    int update(String sql, Params params);

    int[] batch(String sql, List<Params> rows);

    DatabaseType type();

    String poolName();

    Health health();

    @Override
    void close();
}
