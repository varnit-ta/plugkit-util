package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

/**
 * No pooled connection became free within {@code pool.acquireTimeout}.
 *
 * <p>The message includes the pool's active, idle and waiting counts.
 */
public final class AcquireTimeout extends TimeoutFailure {

    public AcquireTimeout(String message, @Nullable String sqlState, @Nullable Throwable cause) {
        super(message, sqlState, cause);
    }
}
