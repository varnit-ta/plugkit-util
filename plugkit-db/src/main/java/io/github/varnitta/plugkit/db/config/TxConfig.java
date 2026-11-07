package io.github.varnitta.plugkit.db.config;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import io.github.varnitta.plugkit.db.internal.ConfigValidator;
import java.time.Duration;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * Transaction settings.
 *
 * <p>{@code defaultTimeout} (60s) limits how long a whole transaction may run, across all of its
 * statements.
 *
 * @param defaultTimeout the longest a transaction may run unless it sets its own timeout
 */
public record TxConfig(Duration defaultTimeout) {

    public TxConfig {
        requireNonNull(defaultTimeout, "tx.defaultTimeout");
        ConfigValidator.section("tx").requirePositive("defaultTimeout", defaultTimeout).throwIfInvalid();
    }

    public static TxConfig defaults() {
        return builder().build();
    }

    @Builder
    private static TxConfig withDefaults(@Nullable Duration defaultTimeout) {
        return new TxConfig(requireNonNullElse(defaultTimeout, Duration.ofSeconds(60)));
    }
}
