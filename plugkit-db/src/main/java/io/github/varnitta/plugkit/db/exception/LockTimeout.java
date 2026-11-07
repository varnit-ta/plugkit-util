package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

/**
 * A statement waited longer than {@code session.lockTimeout} for a lock held by another transaction.
 */
public final class LockTimeout extends TimeoutFailure {

    public LockTimeout(String message, @Nullable String sqlState, @Nullable Throwable cause) {
        super(message, sqlState, cause);
    }
}
