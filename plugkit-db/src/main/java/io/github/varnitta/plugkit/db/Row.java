package io.github.varnitta.plugkit.db;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

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
