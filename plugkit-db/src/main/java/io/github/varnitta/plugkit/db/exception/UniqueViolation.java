package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

/**
 * A row would duplicate a value protected by a unique constraint or primary key.
 */
public final class UniqueViolation extends ConstraintViolation {

    public UniqueViolation(String message, @Nullable String constraintName, @Nullable String sqlState,
            @Nullable Throwable cause) {
        super(message, constraintName, sqlState, cause);
    }
}
