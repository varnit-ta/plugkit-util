package io.github.varnitta.plugkit.db;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The databases plugkit-db supports.
 *
 * <p>The type is usually inferred from the JDBC URL with {@link #fromUrl(String)}; wrapper prefixes such
 * as {@code jdbc:aws-wrapper:postgresql:} are skipped. In configuration it is written as
 * {@link #configName()}, for example {@code database-type: postgres}.
 */
public enum DatabaseType {

    POSTGRES("postgresql"),
    MYSQL("mysql");

    private final String subProtocol;

    DatabaseType(String subProtocol) {
        this.subProtocol = subProtocol;
    }

    public String jdbcPrefix() {
        return "jdbc:" + subProtocol + ":";
    }

    public String configName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<DatabaseType> fromUrl(String url) {
        if (!url.startsWith("jdbc:")) {
            return Optional.empty();
        }
        String rest = url.substring("jdbc:".length());
        int hostStart = rest.indexOf("//");
        String prefix = hostStart < 0 ? rest : rest.substring(0, hostStart);
        return Arrays.stream(prefix.split(":"))
                .flatMap(token -> Arrays.stream(values()).filter(type -> type.subProtocol.equalsIgnoreCase(token)))
                .findFirst();
    }
}
