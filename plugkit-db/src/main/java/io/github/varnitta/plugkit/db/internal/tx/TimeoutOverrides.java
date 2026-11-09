package io.github.varnitta.plugkit.db.internal.tx;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Per-thread timeout overrides set by an integration, such as Spring's {@code @Timeouts}, for the
 * code running inside one method call.
 *
 * <p>While an override is active, transactions use it unless their {@code TxOptions} set their own
 * value, and auto-commit statements run in their own short transaction so the statement timeout can be
 * applied. {@link #apply} returns a scope that restores the previous overrides when closed, so nested
 * calls work.
 */
public final class TimeoutOverrides {

    private static final ThreadLocal<@Nullable TimeoutOverride> CURRENT = new ThreadLocal<>();

    private TimeoutOverrides() {
    }

    public static TimeoutOverrideScope apply(@Nullable Duration statement, @Nullable Duration transaction) {
        TimeoutOverride previous = CURRENT.get();
        CURRENT.set(new TimeoutOverride(statement, transaction));
        return () -> {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        };
    }

    public static @Nullable Duration statement() {
        TimeoutOverride override = CURRENT.get();
        return override == null ? null : override.statement();
    }

    public static @Nullable Duration transaction() {
        TimeoutOverride override = CURRENT.get();
        return override == null ? null : override.transaction();
    }
}
