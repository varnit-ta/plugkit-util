package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

/**
 * A statement ran longer than its statement timeout and was cancelled.
 */
public final class StatementTimeout extends TimeoutFailure {

    public StatementTimeout(String message, @Nullable String sqlState, @Nullable Throwable cause) {
        super(message, sqlState, cause);
    }
}
