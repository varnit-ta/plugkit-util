package io.github.varnitta.plugkit.db.tx;

import io.github.varnitta.plugkit.db.sql.SqlOperations;

/**
 * Statements that run on one connection inside one database transaction.
 *
 * <p>A {@code Transaction} is passed to the callback of
 * {@link io.github.varnitta.plugkit.db.Database#inTransaction(TxOptions, java.util.function.Function)} and is only valid during that
 * callback; using it afterwards throws {@link IllegalStateException}. It is not thread-safe.
 */
public interface Transaction extends SqlOperations {
}
