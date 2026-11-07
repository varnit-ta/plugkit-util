package io.github.varnitta.plugkit.db.internal;

import io.github.varnitta.plugkit.db.exception.InvalidConfigException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class ConfigValidator {

    private final String section;
    private final List<String> problems = new ArrayList<>();

    private ConfigValidator(String section) {
        this.section = section;
    }

    public static ConfigValidator topLevel() {
        return new ConfigValidator("");
    }

    public static ConfigValidator section(String name) {
        return new ConfigValidator(name);
    }

    public ConfigValidator require(String key, boolean condition, String message) {
        return condition ? this : reject(key, message);
    }

    public ConfigValidator reject(String key, String message) {
        problems.add(qualified(key) + " " + message);
        return this;
    }

    public ConfigValidator requireAtLeast(String key, Duration value, Duration minimum) {
        return require(key, value.compareTo(minimum) >= 0,
                "must be at least " + formatDuration(minimum) + ", was " + formatDuration(value));
    }

    public ConfigValidator requirePositive(String key, Duration value) {
        return requireAtLeast(key, value, Duration.ofMillis(1));
    }

    public ConfigValidator requireLessThan(String key, Duration value, String limitKey, Duration limit) {
        return require(key, value.compareTo(limit) < 0, "must be less than " + qualified(limitKey)
                + " (" + formatDuration(limit) + "), was " + formatDuration(value));
    }

    public List<String> problems() {
        return List.copyOf(problems);
    }

    public void throwIfInvalid() {
        if (!problems.isEmpty()) {
            throw new InvalidConfigException(problems);
        }
    }

    public static String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        if (millis != 0 && millis % 60_000 == 0) {
            return millis / 60_000 + "m";
        }
        if (millis != 0 && millis % 1_000 == 0) {
            return millis / 1_000 + "s";
        }
        return millis + "ms";
    }

    private String qualified(String key) {
        return section.isEmpty() ? key : section + "." + key;
    }
}
