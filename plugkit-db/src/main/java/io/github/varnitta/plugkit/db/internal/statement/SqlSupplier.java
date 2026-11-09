package io.github.varnitta.plugkit.db.internal.statement;

import java.sql.SQLException;
import org.jspecify.annotations.Nullable;

/**
 * A supplier that may throw {@link SQLException}, which {@link java.util.function.Supplier} can't.
 *
 * <p>Used for reading a value from JDBC, for example one column of the current row.
 *
 * @param <T> the value; may be {@code null}, as for an SQL {@code NULL}
 */
@FunctionalInterface
public interface SqlSupplier<T extends @Nullable Object> {

    T get() throws SQLException;
}
