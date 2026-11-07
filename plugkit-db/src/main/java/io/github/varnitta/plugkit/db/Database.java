package io.github.varnitta.plugkit.db;

import java.util.List;
import java.util.Optional;

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
