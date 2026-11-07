package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

public final class AcquireTimeout extends TimeoutFailure {

    public AcquireTimeout(String message, @Nullable String sqlState, @Nullable Throwable cause) {
        super(message, sqlState, cause);
    }
}
