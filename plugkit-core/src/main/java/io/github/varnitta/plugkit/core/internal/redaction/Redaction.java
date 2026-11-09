package io.github.varnitta.plugkit.core.internal.redaction;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Hides secrets before configuration is printed, so passwords and tokens never reach logs.
 *
 * <p>A key is secret when it contains {@code password}, {@code secret} or {@code token}, ignoring case.
 * In URLs, secret query parameters and the password in {@code user:password@host} are replaced.
 */
public final class Redaction {

    public static final String REDACTED = "****";

    private static final List<String> SECRET_KEY_WORDS = List.of("password", "secret", "token");
    private static final Pattern URL_SECRET_PARAM =
            Pattern.compile("(?i)((?:password|secret|token)[^=&;?/]*=)[^&;]*");
    private static final Pattern URL_USER_INFO = Pattern.compile("(//[^/:@]+:)[^/@]+@");

    private Redaction() {
    }

    public static boolean isSecretKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return SECRET_KEY_WORDS.stream().anyMatch(lower::contains);
    }

    public static String url(String url) {
        String redacted = URL_SECRET_PARAM.matcher(url).replaceAll("$1" + REDACTED);
        return URL_USER_INFO.matcher(redacted).replaceAll("$1" + REDACTED + "@");
    }

    public static String properties(Map<String, String> properties) {
        return properties.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + (isSecretKey(entry.getKey()) ? REDACTED : entry.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    public static @Nullable String secret(@Nullable String value) {
        return value == null ? null : REDACTED;
    }
}
