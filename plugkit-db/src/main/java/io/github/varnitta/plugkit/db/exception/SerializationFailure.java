package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

public final class SerializationFailure extends TransientFailure {

    public SerializationFailure(String message, @Nullable String sqlState, @Nullable Throwable cause) {
        super(message, sqlState, cause);
    }
}
