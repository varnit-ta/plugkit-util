package io.github.varnitta.plugkit.db.sql;

import java.util.List;
import java.util.Optional;

/**
 * The statements available both on a {@link io.github.varnitta.plugkit.db.Database} and inside a
 * {@link io.github.varnitta.plugkit.db.tx.Transaction}.
 *
 * <p>SQL uses {@code :name} placeholders bound from {@link Params}. Errors are thrown as
 * {@link io.github.varnitta.plugkit.db.exception.DatabaseException} subtypes.
 */
public interface SqlOperations {

    <T> List<T> query(String sql, Params params, RowMapper<T> mapper);

    <T> Optional<T> queryOne(String sql, Params params, RowMapper<T> mapper);

    int update(String sql, Params params);

    int[] batch(String sql, List<Params> rows);
}
