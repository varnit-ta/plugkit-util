package io.github.varnitta.plugkit.db;

@FunctionalInterface
public interface RowMapper<T> {

    T map(Row row);
}
