package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

/**
 * The database aborted the transaction to break a deadlock. Retrying the whole transaction usually
 * succeeds.
 */
public final class Deadlock extends TransientFailure {

    public Deadlock(String message, @Nullable String sqlState, @Nullable Throwable cause) {
        super(message, sqlState, cause);
    }
}
