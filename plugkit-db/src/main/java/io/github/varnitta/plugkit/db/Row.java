package io.github.varnitta.plugkit.db;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The current row of a query result, passed to a {@link RowMapper}.
 *
 * <p>Getters return {@code null} for SQL {@code NULL}; {@link #require(String, Class)} throws instead.
 * A {@code Row} is only valid during the {@link RowMapper#map(Row)} call it is passed to.
 */
public interface Row {

    <T> @Nullable T get(String column, Class<T> type);

    <T> @Nullable T get(int columnIndex, Class<T> type);

    <T> T require(String column, Class<T> type);

    @Nullable String getString(String column);

    @Nullable Long getLong(String column);

    @Nullable Integer getInt(String column);

    @Nullable Boolean getBoolean(String column);

    @Nullable BigDecimal getBigDecimal(String column);

    @Nullable Instant getInstant(String column);

    @Nullable LocalDate getLocalDate(String column);

    @Nullable UUID getUuid(String column);
}
