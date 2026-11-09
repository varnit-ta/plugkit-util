package io.github.varnitta.plugkit.db.exception;

import io.github.varnitta.plugkit.core.exception.PlugkitException;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Base type for every error raised while talking to the database.
 *
 * <p>Subtypes describe what went wrong independent of the database vendor. {@link #isRetryable()} is
 * {@code true} only for {@link TransientFailure}s. The original {@link java.sql.SQLException} is kept as
 * the cause.
 */
public abstract class DatabaseException extends PlugkitException {

    private final @Nullable String sqlState;

    DatabaseException(String message, @Nullable String sqlState, @Nullable Throwable cause) {
        super(message, cause);
        this.sqlState = sqlState;
    }

    public Optional<String> sqlState() {
        return Optional.ofNullable(sqlState);
    }
}
