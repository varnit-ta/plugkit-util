package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

/**
 * A row references a missing row, or a referenced row is still in use.
 */
public final class ForeignKeyViolation extends ConstraintViolation {

    public ForeignKeyViolation(String message, @Nullable String constraintName, @Nullable String sqlState,
            @Nullable Throwable cause) {
        super(message, constraintName, sqlState, cause);
    }
}
