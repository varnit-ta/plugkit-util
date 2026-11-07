package io.github.varnitta.plugkit.db.exception;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A statement broke a database constraint.
 *
 * <p>{@link #constraintName()} names the constraint when the database reports it.
 */
public abstract class ConstraintViolation extends DatabaseException {

    private final @Nullable String constraintName;

    ConstraintViolation(String message, @Nullable String constraintName, @Nullable String sqlState,
                        @Nullable Throwable cause) {
        super(message, sqlState, cause);
        this.constraintName = constraintName;
    }

    public Optional<String> constraintName() {
        return Optional.ofNullable(constraintName);
    }
}
