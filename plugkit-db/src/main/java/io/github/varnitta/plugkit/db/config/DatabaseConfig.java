package io.github.varnitta.plugkit.db.config;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.db.exception.InvalidConfigException;
import io.github.varnitta.plugkit.db.internal.ConfigValidator;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Singular;
import org.jspecify.annotations.Nullable;

/**
 * Everything needed to connect to a database and run a connection pool.
 *
 * <p>Build it with {@code DatabaseConfig.builder()}; only {@code url} is required, every other setting has a safe
 * default. The {@link DatabaseType} is inferred from the URL unless set explicitly, in which case it must
 * match. Invalid settings throw
 * {@link io.github.varnitta.plugkit.db.exception.InvalidConfigException} listing every problem.
 *
 * <pre>{@code
 * DatabaseConfig config = DatabaseConfig.builder()
 *         .url("jdbc:postgresql://localhost:5432/app")
 *         .user("app")
 *         .password(secret)
 *         .pool(PoolConfig.builder().maxSize(20).build())
 *         .driverProperty("sslmode", "require")
 *         .build();
 * }</pre>
 *
 * <p>{@link #toString()} redacts the password, secrets in the URL and secret driver properties.
 *
 * @param type               the database type; inferred from {@code url} when not set
 * @param url                the JDBC URL; required
 * @param user               the database user, or {@code null} when the URL or driver supplies it
 * @param password           the password, or {@code null} for passwordless authentication such as IAM
 * @param poolName           the name used in logs, metrics and error messages; default {@code plugkit-db}
 * @param pool               connection pool settings
 * @param session            timeouts the database server enforces
 * @param tx                 transaction settings
 * @param slowQueryThreshold statements slower than this are logged as warnings; default 1s
 * @param driverProperties   extra properties passed to the JDBC driver as-is
 */
public record DatabaseConfig(
        DatabaseType type,
        String url,
        @Nullable String user,
        @Nullable String password,
        String poolName,
        PoolConfig pool,
        SessionConfig session,
        TxConfig tx,
        Duration slowQueryThreshold,
        Map<String, String> driverProperties) {

    private static final String REDACTED = "****";
    private static final List<String> SECRET_KEY_WORDS = List.of("password", "secret", "token");
    private static final Pattern URL_SECRET_PARAM =
            Pattern.compile("(?i)((?:password|secret|token)[^=&;?/]*=)[^&;]*");
    private static final Pattern URL_USER_INFO = Pattern.compile("(//[^/:@]+:)[^/@]+@");

    public DatabaseConfig {
        requireNonNull(type, "type");
        requireNonNull(url, "url");
        requireNonNull(poolName, "poolName");
        requireNonNull(pool, "pool");
        requireNonNull(session, "session");
        requireNonNull(tx, "tx");
        requireNonNull(slowQueryThreshold, "slowQueryThreshold");
        driverProperties.forEach((key, value) -> requireNonNull(value, "driverProperties." + key));
        validate(type, url, poolName, slowQueryThreshold, driverProperties).throwIfInvalid();
        driverProperties = Collections.unmodifiableMap(new LinkedHashMap<>(driverProperties));
    }

    @Builder
    private static DatabaseConfig withDefaults(
            @Nullable DatabaseType type,
            @Nullable String url,
            @Nullable String user,
            @Nullable String password,
            @Nullable String poolName,
            @Nullable PoolConfig pool,
            @Nullable SessionConfig session,
            @Nullable TxConfig tx,
            @Nullable Duration slowQueryThreshold,
            @Singular Map<String, String> driverProperties) {
        String jdbcUrl = requireNonNullElse(url, "");
        String name = requireNonNullElse(poolName, "plugkit-db");
        Duration slowQuery = requireNonNullElse(slowQueryThreshold, Duration.ofSeconds(1));
        DatabaseType resolvedType = type != null ? type : DatabaseType.fromUrl(jdbcUrl).orElse(null);
        if (resolvedType == null) {
            throw new InvalidConfigException(validate(null, jdbcUrl, name, slowQuery, driverProperties).problems());
        }
        return new DatabaseConfig(
                resolvedType,
                jdbcUrl,
                user,
                password,
                name,
                requireNonNullElse(pool, PoolConfig.defaults()),
                requireNonNullElse(session, SessionConfig.defaults()),
                requireNonNullElse(tx, TxConfig.defaults()),
                slowQuery,
                driverProperties);
    }

    private static ConfigValidator validate(@Nullable DatabaseType type, String url, String poolName,
                                            Duration slowQueryThreshold, Map<String, String> driverProperties) {
        boolean jdbcUrl = url.startsWith("jdbc:");
        ConfigValidator validator = ConfigValidator.topLevel()
                .require("url", !url.isBlank(), "is required")
                .require("url", url.isBlank() || jdbcUrl, "must start with 'jdbc:'");
        if (jdbcUrl && type == null) {
            validator.reject("url", "is not for a supported database; supported: " + describeSupportedTypes());
        }
        if (jdbcUrl && type != null) {
            validator.require("type", DatabaseType.fromUrl(url).filter(type::equals).isPresent(),
                    "is '" + type.configName() + "' but url does not start with " + type.jdbcPrefix());
        }
        validator.require("poolName", !poolName.isBlank(), "must not be blank")
                .requirePositive("slowQueryThreshold", slowQueryThreshold);
        driverProperties.keySet().forEach(key ->
                validator.require("driverProperties", !key.isBlank(), "keys must not be blank"));
        return validator;
    }

    private static String describeSupportedTypes() {
        return Arrays.stream(DatabaseType.values())
                .map(type -> type.configName() + " (" + type.jdbcPrefix() + ")")
                .collect(Collectors.joining(", "));
    }

    @Override
    public String toString() {
        return "DatabaseConfig[type=" + type
                + ", url=" + redactUrl(url)
                + ", user=" + user
                + ", password=" + (password == null ? null : REDACTED)
                + ", poolName=" + poolName
                + ", pool=" + pool
                + ", session=" + session
                + ", tx=" + tx
                + ", slowQueryThreshold=" + slowQueryThreshold
                + ", driverProperties=" + redactProperties(driverProperties)
                + "]";
    }

    private static String redactUrl(String url) {
        String redacted = URL_SECRET_PARAM.matcher(url).replaceAll("$1" + REDACTED);
        return URL_USER_INFO.matcher(redacted).replaceAll("$1" + REDACTED + "@");
    }

    private static String redactProperties(Map<String, String> properties) {
        return properties.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + (isSecretKey(entry.getKey()) ? REDACTED : entry.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private static boolean isSecretKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return SECRET_KEY_WORDS.stream().anyMatch(lower::contains);
    }
}
