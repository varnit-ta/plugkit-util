package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

public abstract class TimeoutFailure extends DatabaseException {

    TimeoutFailure(String message, @Nullable String sqlState, @Nullable Throwable cause) {
        super(message, sqlState, cause);
    }
}
