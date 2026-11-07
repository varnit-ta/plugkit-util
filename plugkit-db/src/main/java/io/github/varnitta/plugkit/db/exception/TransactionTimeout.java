package io.github.varnitta.plugkit.db.exception;

import org.jspecify.annotations.Nullable;

/**
 * A transaction ran past its deadline, or stayed idle inside a transaction for too long.
 */
public final class TransactionTimeout extends TimeoutFailure {

    public TransactionTimeout(String message, @Nullable String sqlState, @Nullable Throwable cause) {
        super(message, sqlState, cause);
    }
}
