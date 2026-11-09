package io.github.varnitta.plugkit.db.config;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import io.github.varnitta.plugkit.core.internal.validation.ConfigValidator;
import java.time.Duration;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * Timeouts the database server enforces on every connection.
 *
 * <p>Defaults: {@code statementTimeout} 30s, {@code lockTimeout} 10s, {@code idleInTransactionTimeout}
 * 60s. The driver's socket timeout is derived from {@code statementTimeout} and can't be set separately.
 *
 * @param statementTimeout         the longest a single statement may run
 * @param lockTimeout              the longest a statement waits for a lock
 * @param idleInTransactionTimeout the longest a session may sit idle inside an open transaction
 */
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
