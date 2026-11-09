package io.github.varnitta.plugkit.db.internal.tx;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * The timeouts overridden for the code running on one thread, as set through {@link TimeoutOverrides}.
 *
 * @param statement   the statement timeout, or {@code null} for the configured default
 * @param transaction the transaction timeout, or {@code null} for the configured default
 */
public record TimeoutOverride(@Nullable Duration statement, @Nullable Duration transaction) {
}
