package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

/**
 * The connection to the database failed or was closed, or the server isn't accepting connections.
 */
public final class ConnectionLost extends TransientFailure {

    public ConnectionLost(String message, @Nullable String sqlState, @Nullable Throwable cause) {
        super(message, sqlState, cause);
    }
}
