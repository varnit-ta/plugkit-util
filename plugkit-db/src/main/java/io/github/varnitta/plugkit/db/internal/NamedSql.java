package io.github.varnitta.plugkit.db.internal;

import io.github.varnitta.plugkit.db.Params;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

final class NamedSql {

    private static final Pattern DOLLAR_TAG = Pattern.compile("\\$[A-Za-z_]*\\$");

    private final List<String> texts;
    private final List<String> names;

    private NamedSql(List<String> texts, List<String> names) {
        this.texts = texts;
        this.names = names;
    }

    record Bound(String sql, List<@Nullable Object> values) {
    }

    static NamedSql parse(String sql) {
        List<String> texts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            String dollarTag = c == '$' ? dollarTagAt(sql, i) : null;
            if (c == '\'' || c == '"') {
                i = copyThrough(sql, i, String.valueOf(c), String.valueOf(c), text);
            } else if (c == '-' && next == '-') {
                i = copyThrough(sql, i, "--", "\n", text);
            } else if (c == '/' && next == '*') {
                i = copyThrough(sql, i, "/*", "*/", text);
            } else if (dollarTag != null) {
                i = copyThrough(sql, i, dollarTag, dollarTag, text);
            } else if (c == ':' && next == ':') {
                text.append("::");
                i += 2;
            } else if (c == ':' && isNameStart(next)) {
                int end = i + 1;
                while (end < sql.length() && isNamePart(sql.charAt(end))) {
                    end++;
                }
                texts.add(text.toString());
                names.add(sql.substring(i + 1, end));
                text.setLength(0);
                i = end;
            } else {
                text.append(c);
                i++;
            }
        }
        texts.add(text.toString());
        return new NamedSql(List.copyOf(texts), List.copyOf(names));
    }

    Bound bind(Params params) {
        StringBuilder sql = new StringBuilder(texts.getFirst());
        List<@Nullable Object> values = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (!params.contains(name)) {
                throw new IllegalArgumentException("No value for parameter :" + name + "; given " + params);
            }
            Object value = params.get(name);
            if (value instanceof Collection<?> collection) {
                if (collection.isEmpty()) {
                    throw new IllegalArgumentException("Parameter :" + name + " is an empty collection");
                }
                sql.append("?, ".repeat(collection.size() - 1)).append('?');
                values.addAll(collection);
            } else {
                sql.append('?');
                values.add(value);
            }
            sql.append(texts.get(i + 1));
        }
        return new Bound(sql.toString(), values);
    }

    List<String> parameterNames() {
        return names;
    }

    private static int copyThrough(String sql, int start, String opening, String closing, StringBuilder text) {
        int closingAt = sql.indexOf(closing, start + opening.length());
        int stop = closingAt < 0 ? sql.length() : closingAt + closing.length();
        text.append(sql, start, stop);
        return stop;
    }

    private static @Nullable String dollarTagAt(String sql, int start) {
        Matcher matcher = DOLLAR_TAG.matcher(sql).region(start, sql.length());
        return matcher.lookingAt() ? matcher.group() : null;
    }

    private static boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
