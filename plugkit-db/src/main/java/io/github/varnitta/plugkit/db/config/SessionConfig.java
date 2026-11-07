package io.github.varnitta.plugkit.db.config;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import io.github.varnitta.plugkit.db.internal.ConfigValidator;
import java.time.Duration;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

public record SessionConfig(Duration statementTimeout, Duration lockTimeout, Duration idleInTransactionTimeout) {

    public SessionConfig {
        requireNonNull(statementTimeout, "session.statementTimeout");
        requireNonNull(lockTimeout, "session.lockTimeout");
        requireNonNull(idleInTransactionTimeout, "session.idleInTransactionTimeout");
        ConfigValidator.section("session")
                .requirePositive("statementTimeout", statementTimeout)
                .requirePositive("lockTimeout", lockTimeout)
                .requirePositive("idleInTransactionTimeout", idleInTransactionTimeout)
                .throwIfInvalid();
    }

    public static SessionConfig defaults() {
        return builder().build();
    }

    @Builder
    private static SessionConfig withDefaults(
            @Nullable Duration statementTimeout,
            @Nullable Duration lockTimeout,
            @Nullable Duration idleInTransactionTimeout) {
        return new SessionConfig(
                requireNonNullElse(statementTimeout, Duration.ofSeconds(30)),
                requireNonNullElse(lockTimeout, Duration.ofSeconds(10)),
                requireNonNullElse(idleInTransactionTimeout, Duration.ofSeconds(60)));
    }
}
