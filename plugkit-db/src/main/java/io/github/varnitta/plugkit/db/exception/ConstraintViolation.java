package io.github.varnitta.plugkit.db.exception;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

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
