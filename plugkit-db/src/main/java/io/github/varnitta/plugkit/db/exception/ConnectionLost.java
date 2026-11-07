package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

public final class ConnectionLost extends TransientFailure {

    public ConnectionLost(String message, @Nullable String sqlState, @Nullable Throwable cause) {
        super(message, sqlState, cause);
    }
}
