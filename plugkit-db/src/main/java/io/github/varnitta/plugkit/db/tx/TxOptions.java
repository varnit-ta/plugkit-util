package io.github.varnitta.plugkit.db.tx;

import static java.util.Objects.requireNonNullElse;

import io.github.varnitta.plugkit.core.internal.validation.ConfigValidator;
import java.time.Duration;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * Settings for one {@link io.github.varnitta.plugkit.db.Database#inTransaction(TxOptions, java.util.function.Function)} call.
 *
 * <pre>{@code
 * TxOptions.builder()
 *         .isolation(Isolation.SERIALIZABLE)
 *         .maxAttempts(3)
 *         .build();
 * }</pre>
 *
 * @param isolation        the isolation level, or {@code null} for the database default
 * @param readOnly         whether the transaction may only read; default {@code false}
 * @param timeout          the deadline for the whole transaction, or {@code null} for
 *                         {@code tx.defaultTimeout}
 * @param statementTimeout the limit for each statement in this transaction, or {@code null} for
 *                         {@code session.statementTimeout}
 * @param maxAttempts      how many times the transaction runs in total when it fails with a
 *                         {@link io.github.varnitta.plugkit.db.exception.TransientFailure}; default 1,
 *                         which means no retry
 */
public record TxOptions(
        @Nullable Isolation isolation,
        boolean readOnly,
        @Nullable Duration timeout,
        @Nullable Duration statementTimeout,
        int maxAttempts) {

    public static final int MAX_ATTEMPTS_LIMIT = 10;

    public TxOptions {
        ConfigValidator validator = ConfigValidator.section("tx")
                .require("maxAttempts", maxAttempts >= 1 && maxAttempts <= MAX_ATTEMPTS_LIMIT,
                        "must be between 1 and " + MAX_ATTEMPTS_LIMIT + ", was " + maxAttempts);
        if (timeout != null) {
            validator.requirePositive("timeout", timeout);
        }
        if (statementTimeout != null) {
            validator.requirePositive("statementTimeout", statementTimeout);
        }
        validator.throwIfInvalid();
    }

    public static TxOptions defaults() {
        return builder().build();
    }

    @Builder
    private static TxOptions withDefaults(
            @Nullable Isolation isolation,
            @Nullable Boolean readOnly,
            @Nullable Duration timeout,
            @Nullable Duration statementTimeout,
            @Nullable Integer maxAttempts) {
        return new TxOptions(isolation, requireNonNullElse(readOnly, false), timeout, statementTimeout,
                requireNonNullElse(maxAttempts, 1));
    }
}
