package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

/**
 * A row failed a check constraint.
 */
public final class CheckViolation extends ConstraintViolation {

    public CheckViolation(String message, @Nullable String constraintName, @Nullable String sqlState,
            @Nullable Throwable cause) {
        super(message, constraintName, sqlState, cause);
    }
}
