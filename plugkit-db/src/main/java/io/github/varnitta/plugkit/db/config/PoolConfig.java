package io.github.varnitta.plugkit.db.config;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import io.github.varnitta.plugkit.db.internal.ConfigValidator;
import java.time.Duration;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

public record PoolConfig(
        int maxSize,
        int minIdle,
        Duration acquireTimeout,
        Duration maxLifetime,
        Duration idleTimeout,
        Duration keepalive,
        Duration leakDetection) {

    private static final Duration MIN_MAX_LIFETIME = Duration.ofSeconds(30);

    public PoolConfig {
        requireNonNull(acquireTimeout, "pool.acquireTimeout");
        requireNonNull(maxLifetime, "pool.maxLifetime");
        requireNonNull(idleTimeout, "pool.idleTimeout");
        requireNonNull(keepalive, "pool.keepalive");
        requireNonNull(leakDetection, "pool.leakDetection");
        validate(maxSize, minIdle, acquireTimeout, maxLifetime, idleTimeout, keepalive, leakDetection).throwIfInvalid();
    }

    public static PoolConfig defaults() {
        return builder().build();
    }

    @Builder
    private static PoolConfig withDefaults(
            @Nullable Integer maxSize,
            @Nullable Integer minIdle,
            @Nullable Duration acquireTimeout,
            @Nullable Duration maxLifetime,
            @Nullable Duration idleTimeout,
            @Nullable Duration keepalive,
            @Nullable Duration leakDetection) {
        int size = requireNonNullElse(maxSize, 10);
        return new PoolConfig(
                size,
                requireNonNullElse(minIdle, size),
                requireNonNullElse(acquireTimeout, Duration.ofSeconds(5)),
                requireNonNullElse(maxLifetime, Duration.ofMinutes(30)),
                requireNonNullElse(idleTimeout, Duration.ofMinutes(10)),
                requireNonNullElse(keepalive, Duration.ofMinutes(2)),
                requireNonNullElse(leakDetection, Duration.ofSeconds(60)));
    }

    private static ConfigValidator validate(int maxSize, int minIdle, Duration acquireTimeout, Duration maxLifetime,
                                            Duration idleTimeout, Duration keepalive, Duration leakDetection) {
        ConfigValidator validator = ConfigValidator.section("pool")
                .require("maxSize", maxSize >= 1, "must be at least 1, was " + maxSize)
                .require("minIdle", minIdle >= 0 && minIdle <= maxSize,
                        "must be between 0 and pool.maxSize (" + maxSize + "), was " + minIdle)
                .requireAtLeast("acquireTimeout", acquireTimeout, Duration.ofMillis(250))
                .requireAtLeast("maxLifetime", maxLifetime, MIN_MAX_LIFETIME)
                .requireAtLeast("idleTimeout", idleTimeout, Duration.ofSeconds(10))
                .requireAtLeast("keepalive", keepalive, Duration.ofSeconds(30))
                .requireAtLeast("leakDetection", leakDetection, Duration.ofSeconds(2));
        if (maxLifetime.compareTo(MIN_MAX_LIFETIME) >= 0) {
            validator.requireLessThan("idleTimeout", idleTimeout, "maxLifetime", maxLifetime)
                    .requireLessThan("keepalive", keepalive, "maxLifetime", maxLifetime)
                    .requireLessThan("leakDetection", leakDetection, "maxLifetime", maxLifetime);
        }
        return validator;
    }
}
