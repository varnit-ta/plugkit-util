package io.github.varnitta.plugkit.db.internal.statement;

import java.sql.SQLException;

/**
 * A function that may throw {@link SQLException}, which {@link java.util.function.Function} can't.
 *
 * <p>Used for work done with a JDBC object, for example "run this statement on this connection", so the
 * caller can catch and translate the exception in one place.
 *
 * @param <I> the input, such as a {@link java.sql.Connection}
 * @param <O> the result
 */
@FunctionalInterface
public interface SqlFunction<I, O> {

    O apply(I input) throws SQLException;
}
