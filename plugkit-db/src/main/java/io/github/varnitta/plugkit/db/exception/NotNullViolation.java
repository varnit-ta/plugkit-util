package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

public final class NotNullViolation extends ConstraintViolation {

    public NotNullViolation(String message, @Nullable String constraintName, @Nullable String sqlState,
            @Nullable Throwable cause) {
        super(message, constraintName, sqlState, cause);
    }
}
