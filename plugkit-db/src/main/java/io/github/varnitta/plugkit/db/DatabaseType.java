package io.github.varnitta.plugkit.db;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum DatabaseType {

    POSTGRES("postgresql");

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
