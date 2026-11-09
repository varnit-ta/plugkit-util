package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

/**
 * A {@code NULL} was written to a column that doesn't allow it.
 */
public final class NotNullViolation extends ConstraintViolation {

    public NotNullViolation(String message, @Nullable String constraintName, @Nullable String sqlState,
            @Nullable Throwable cause) {
        super(message, constraintName, sqlState, cause);
    }
}
