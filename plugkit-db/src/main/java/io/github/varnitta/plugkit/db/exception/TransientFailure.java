package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

/**
 * A failure that may succeed if the whole transaction is run again, such as a deadlock or a dropped
 * connection.
 */
public abstract class TransientFailure extends DatabaseException {

    TransientFailure(String message, @Nullable String sqlState, @Nullable Throwable cause) {
        super(message, sqlState, cause);
    }

    @Override
    public boolean isRetryable() {
        return true;
    }
}
