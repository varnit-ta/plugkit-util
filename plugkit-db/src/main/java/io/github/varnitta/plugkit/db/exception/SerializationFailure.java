package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

/**
 * The database aborted the transaction because it conflicted with a concurrent one. Retrying the whole
 * transaction usually succeeds.
 */
public final class SerializationFailure extends TransientFailure {

    public SerializationFailure(String message, @Nullable String sqlState, @Nullable Throwable cause) {
        super(message, sqlState, cause);
    }
}
