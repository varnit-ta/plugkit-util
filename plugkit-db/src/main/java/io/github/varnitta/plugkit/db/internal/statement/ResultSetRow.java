package io.github.varnitta.plugkit.db.internal.statement;

import io.github.varnitta.plugkit.db.exception.QueryFailure;
import io.github.varnitta.plugkit.db.sql.Row;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@link io.github.varnitta.plugkit.db.sql.Row} view over the current row of a {@link java.sql.ResultSet}.
 */
final class ResultSetRow implements Row {

    private final ResultSet resultSet;
    private final String poolName;

    ResultSetRow(ResultSet resultSet, String poolName) {
        this.resultSet = resultSet;
        this.poolName = poolName;
    }

    @Override
    public <T> @Nullable T get(String column, Class<T> type) {
        return read(() -> type == Instant.class
                ? type.cast(toInstant(resultSet.getObject(column, OffsetDateTime.class)))
                : resultSet.getObject(column, type));
    }

    @Override
    public <T> @Nullable T get(int columnIndex, Class<T> type) {
        return read(() -> type == Instant.class
                ? type.cast(toInstant(resultSet.getObject(columnIndex, OffsetDateTime.class)))
                : resultSet.getObject(columnIndex, type));
    }

    @Override
    public <T> T require(String column, Class<T> type) {
        T value = get(column, type);
        if (value == null) {
            throw new QueryFailure("Column '" + column + "' is NULL but a value is required [pool=" + poolName + "]",
                    null, null);
        }
        return value;
    }

    @Override
    public @Nullable String getString(String column) {
        return read(() -> resultSet.getString(column));
    }

    @Override
    public @Nullable Long getLong(String column) {
        return read(() -> {
            long value = resultSet.getLong(column);
            return resultSet.wasNull() ? null : value;
        });
    }

    @Override
    public @Nullable Integer getInt(String column) {
        return read(() -> {
            int value = resultSet.getInt(column);
            return resultSet.wasNull() ? null : value;
        });
    }

    @Override
    public @Nullable Boolean getBoolean(String column) {
        return read(() -> {
            boolean value = resultSet.getBoolean(column);
            return resultSet.wasNull() ? null : value;
        });
    }

    @Override
    public @Nullable BigDecimal getBigDecimal(String column) {
        return read(() -> resultSet.getBigDecimal(column));
    }

    @Override
    public @Nullable Instant getInstant(String column) {
        return get(column, Instant.class);
    }

    @Override
    public @Nullable LocalDate getLocalDate(String column) {
        return get(column, LocalDate.class);
    }

    @Override
    public @Nullable UUID getUuid(String column) {
        return get(column, UUID.class);
    }

    private static @Nullable Instant toInstant(@Nullable OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static <T> @Nullable T read(ColumnRead<T> read) {
        try {
            return read.read();
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    @FunctionalInterface
    private interface ColumnRead<T> {
        @Nullable T read() throws SQLException;
    }
}
