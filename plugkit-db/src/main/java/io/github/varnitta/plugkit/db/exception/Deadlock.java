package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

public final class Deadlock extends TransientFailure {

    public Deadlock(String message, @Nullable String sqlState, @Nullable Throwable cause) {
        super(message, sqlState, cause);
    }
}
