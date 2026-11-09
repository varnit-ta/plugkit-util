package io.github.varnitta.plugkit.db;

import io.github.varnitta.plugkit.core.Health;
import io.github.varnitta.plugkit.db.sql.SqlOperations;
import io.github.varnitta.plugkit.db.tx.Transaction;
import io.github.varnitta.plugkit.db.tx.TxOptions;
import java.util.function.Function;

/**
 * A pooled connection to one database, with methods to run SQL.
 *
 * <p>Instances are created with {@link Databases#create} and are thread-safe. Each statement method
 * borrows a connection from the pool, runs the statement in auto-commit and returns the connection.
 * Errors are thrown as {@link io.github.varnitta.plugkit.db.exception.DatabaseException} subtypes.
 *
 * <pre>{@code
 * try (Database db = Databases.create(config)) {
 *     List<User> users = db.query("select * from users where status = :status",
 *             Params.of("status", "ACTIVE"), User::from);
 *
 *     db.inTransaction(tx -> {
 *         tx.update("update accounts set balance = balance - :amt where id = :id", debit);
 *         tx.update("update accounts set balance = balance + :amt where id = :id", credit);
 *         return null;
 *     });
 * }
 * }</pre>
 *
 * <p>Close the database when the application stops; closing releases every pooled connection.
 */
public interface Database extends SqlOperations, AutoCloseable {

    <T> T inTransaction(TxOptions options, Function<Transaction, T> work);

    default <T> T inTransaction(Function<Transaction, T> work) {
        return inTransaction(TxOptions.defaults(), work);
    }

    DatabaseType databaseType();

    String poolName();

    Health health();

    @Override
    void close();
}
