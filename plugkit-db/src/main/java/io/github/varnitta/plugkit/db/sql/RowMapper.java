package io.github.varnitta.plugkit.db.sql;

/**
 * Maps one {@link Row} of a query result to an object.
 *
 * <pre>{@code
 * RowMapper<User> mapper = row -> new User(row.require("id", Long.class), row.getString("email"));
 * }</pre>
 *
 * @param <T> the type each row is mapped to
 */
@FunctionalInterface
public interface RowMapper<T> {

    T map(Row row);
}
