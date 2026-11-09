package io.github.varnitta.plugkit.core.exception;

import org.jspecify.annotations.Nullable;

/**
 * Base type for every exception thrown by a plugkit-util library.
 *
 * <p>Applications can catch it to handle any plugkit failure in one place. {@link #isRetryable()} tells
 * retry logic whether running the operation again may succeed; it is {@code false} unless a subtype
 * says otherwise.
 */
public abstract class PlugkitException extends RuntimeException {

    protected PlugkitException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }

    public boolean isRetryable() {
        return false;
    }
}
