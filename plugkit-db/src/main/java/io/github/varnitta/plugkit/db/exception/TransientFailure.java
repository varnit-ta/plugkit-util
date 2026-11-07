package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

public abstract class TransientFailure extends DatabaseException {

    TransientFailure(String message, @Nullable String sqlState, @Nullable Throwable cause) {
        super(message, sqlState, cause);
    }

    @Override
    public boolean isRetryable() {
        return true;
    }
}
