package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

/**
 * The connection was lost while a commit was in flight, so the commit may or may not have been applied.
 *
 * <p>Never retried automatically; check the data before trying again.
 */
public final class CommitOutcomeUnknown extends DatabaseException {

    public CommitOutcomeUnknown(String message, @Nullable String sqlState, @Nullable Throwable cause) {
        super(message, sqlState, cause);
    }
}
