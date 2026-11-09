package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

/**
 * Any other database error, such as invalid SQL, a missing table or a type mismatch. Not retryable.
 */
public final class QueryFailure extends DatabaseException {

    public QueryFailure(String message, @Nullable String sqlState, @Nullable Throwable cause) {
        super(message, sqlState, cause);
    }
}
