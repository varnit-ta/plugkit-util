package io.github.varnitta.plugkit.db.internal.statement;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * SQL with its {@code :name} placeholders replaced by JDBC {@code ?} placeholders, and the values to
 * bind to them in order; produced by {@link NamedSql#bind}.
 *
 * @param sql    the SQL to prepare
 * @param values the values for each {@code ?}, in order
 */
record BoundSql(String sql, List<@Nullable Object> values) {
}
