package io.github.varnitta.plugkit.db;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Immutable named parameters for a SQL statement.
 *
 * <p>Names match {@code :name} placeholders in the SQL. Values may be {@code null}. A
 * {@link java.util.Collection} value expands into a comma-separated list for {@code IN} clauses.
 *
 * <pre>{@code
 * Params.of("status", "ACTIVE").and("ids", List.of(1, 2, 3))
 * }</pre>
 *
 * <p>{@link #toString()} lists parameter names only, so values never appear in logs.
 */
public final class Params {

    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Params EMPTY = new Params(Map.of());

    private final Map<String, @Nullable Object> values;

    private Params(Map<String, @Nullable Object> values) {
        this.values = values;
    }

    public static Params empty() {
        return EMPTY;
    }

    public static Params of(String name, @Nullable Object value) {
        return EMPTY.and(name, value);
    }

    public static Params of(String name1, @Nullable Object value1, String name2, @Nullable Object value2) {
        return of(name1, value1).and(name2, value2);
    }

    public static Params of(String name1, @Nullable Object value1, String name2, @Nullable Object value2,
                            String name3, @Nullable Object value3) {
        return of(name1, value1, name2, value2).and(name3, value3);
    }

    public static Params of(String name1, @Nullable Object value1, String name2, @Nullable Object value2,
                            String name3, @Nullable Object value3, String name4, @Nullable Object value4) {
        return of(name1, value1, name2, value2, name3, value3).and(name4, value4);
    }

    public static Params of(Map<String, ? extends @Nullable Object> values) {
        Params params = EMPTY;
        for (Map.Entry<String, ? extends @Nullable Object> entry : values.entrySet()) {
            params = params.and(entry.getKey(), entry.getValue());
        }
        return params;
    }

    public Params and(String name, @Nullable Object value) {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid parameter name '" + name
                    + "': use letters, digits and underscores, not starting with a digit");
        }
        if (values.containsKey(name)) {
            throw new IllegalArgumentException("Parameter :" + name + " is already set");
        }
        Map<String, @Nullable Object> copy = new LinkedHashMap<>(values);
        copy.put(name, value);
        return new Params(Collections.unmodifiableMap(copy));
    }

    public boolean contains(String name) {
        return values.containsKey(name);
    }

    public @Nullable Object get(String name) {
        return values.get(name);
    }

    public Set<String> names() {
        return values.keySet();
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Params params && values.equals(params.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return "Params" + values.keySet();
    }
}
