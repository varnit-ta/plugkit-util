package io.github.varnitta.plugkit.db.internal.statement;

import io.github.varnitta.plugkit.db.exception.QueryFailure;
import io.github.varnitta.plugkit.db.sql.Params;
import io.github.varnitta.plugkit.db.sql.Row;
import io.github.varnitta.plugkit.db.sql.RowMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Prepares, binds and runs statements on a given connection, and logs statements slower than
 * {@code slowQueryThreshold} without their parameter values.
 *
 * <p>A query timeout of {@code 0} seconds means no client-side limit; the server's statement timeout
 * still applies.
 */
@Slf4j
public final class StatementExecutor {

    public static final int NO_QUERY_TIMEOUT = 0;

    private static final int MAX_LOGGED_SQL_LENGTH = 500;

    private final String poolName;
    private final Duration slowQueryThreshold;
    private final SqlSyntax syntax;

    public StatementExecutor(String poolName, Duration slowQueryThreshold, SqlSyntax syntax) {
        this.poolName = poolName;
        this.slowQueryThreshold = slowQueryThreshold;
        this.syntax = syntax;
    }

    public <T> List<T> query(Connection connection, String sql, Params params, RowMapper<T> mapper, int maxRows,
                      int queryTimeoutSeconds) throws SQLException {
        BoundSql bound = NamedSql.parse(sql, syntax).bind(params);
        long start = System.nanoTime();
        try (PreparedStatement statement = connection.prepareStatement(bound.sql())) {
            statement.setMaxRows(maxRows);
            statement.setQueryTimeout(queryTimeoutSeconds);
            bindValues(statement, bound.values());
            try (ResultSet resultSet = statement.executeQuery()) {
                Row row = new ResultSetRow(resultSet, poolName);
                List<T> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapper.map(row));
                }
                return results;
            }
        } finally {
            logIfSlow(sql, start);
        }
    }

    public <T> Optional<T> queryOne(Connection connection, String sql, Params params, RowMapper<T> mapper,
                             int queryTimeoutSeconds) throws SQLException {
        List<T> rows = query(connection, sql, params, mapper, 2, queryTimeoutSeconds);
        if (rows.size() > 1) {
            throw new QueryFailure("Expected at most one row but the query returned more [pool=" + poolName + "]",
                    null, null);
        }
        return rows.stream().findFirst();
    }

    public int update(Connection connection, String sql, Params params, int queryTimeoutSeconds) throws SQLException {
        BoundSql bound = NamedSql.parse(sql, syntax).bind(params);
        long start = System.nanoTime();
        try (PreparedStatement statement = connection.prepareStatement(bound.sql())) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            bindValues(statement, bound.values());
            return statement.executeUpdate();
        } finally {
            logIfSlow(sql, start);
        }
    }

    public int[] batch(Connection connection, String sql, List<Params> rows, int queryTimeoutSeconds)
            throws SQLException {
        if (rows.isEmpty()) {
            return new int[0];
        }
        NamedSql parsed = NamedSql.parse(sql, syntax);
        List<BoundSql> bound = rows.stream().map(parsed::bind).toList();
        String expandedSql = bound.getFirst().sql();
        if (bound.stream().anyMatch(row -> !row.sql().equals(expandedSql))) {
            throw new IllegalArgumentException(
                    "Every batch row must expand to the same SQL; collection parameters need the same size in each row");
        }
        long start = System.nanoTime();
        try (PreparedStatement statement = connection.prepareStatement(expandedSql)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            for (BoundSql row : bound) {
                bindValues(statement, row.values());
                statement.addBatch();
            }
            return statement.executeBatch();
        } finally {
            logIfSlow(sql, start);
        }
    }

    private static void bindValues(PreparedStatement statement, List<@Nullable Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            statement.setObject(i + 1, toJdbcValue(values.get(i)));
        }
    }

    private static @Nullable Object toJdbcValue(@Nullable Object value) {
        return switch (value) {
            case Instant instant -> OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
            case Enum<?> constant -> constant.name();
            case null, default -> value;
        };
    }

    private void logIfSlow(String sql, long startNanos) {
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        if (elapsed.compareTo(slowQueryThreshold) > 0) {
            log.warn("Slow query on pool '{}' took {} ms: {}", poolName, elapsed.toMillis(), abbreviate(sql));
        }
    }

    private static String abbreviate(String sql) {
        String compact = sql.replaceAll("\\s+", " ").trim();
        return compact.length() <= MAX_LOGGED_SQL_LENGTH
                ? compact
                : compact.substring(0, MAX_LOGGED_SQL_LENGTH) + "…";
    }
}
