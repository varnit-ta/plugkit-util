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

    private static final ThreadLocal<@Nullable Overrides> CURRENT = new ThreadLocal<>();

    private TimeoutOverrides() {
    }

    /**
     * The overrides in effect on the current thread.
     *
     * @param statement   the statement timeout, or {@code null} for the configured default
     * @param transaction the transaction timeout, or {@code null} for the configured default
     */
    public record Overrides(@Nullable Duration statement, @Nullable Duration transaction) {
    }

    /**
     * Restores the previous overrides when closed.
     */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    public static Scope apply(@Nullable Duration statement, @Nullable Duration transaction) {
        Overrides previous = CURRENT.get();
        CURRENT.set(new Overrides(statement, transaction));
        return () -> {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        };
    }

    public static @Nullable Duration statement() {
        Overrides overrides = CURRENT.get();
        return overrides == null ? null : overrides.statement();
    }

    public static @Nullable Duration transaction() {
        Overrides overrides = CURRENT.get();
        return overrides == null ? null : overrides.transaction();
    }
}
