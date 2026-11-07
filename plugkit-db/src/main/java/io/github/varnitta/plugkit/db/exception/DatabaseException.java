package io.github.varnitta.plugkit.db.exception;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

public abstract class DatabaseException extends RuntimeException {

    private final @Nullable String sqlState;

    DatabaseException(String message, @Nullable String sqlState, @Nullable Throwable cause) {
        super(message, cause);
        this.sqlState = sqlState;
    }

    public Optional<String> sqlState() {
        return Optional.ofNullable(sqlState);
    }

    public boolean isRetryable() {
        return false;
    }
}
