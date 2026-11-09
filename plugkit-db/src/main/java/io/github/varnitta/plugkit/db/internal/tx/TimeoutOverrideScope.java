package io.github.varnitta.plugkit.db.internal.tx;

/**
 * Returned by {@link TimeoutOverrides#apply}; closing it restores the overrides that were in effect
 * before, so nested overrides work with try-with-resources.
 */
@FunctionalInterface
public interface TimeoutOverrideScope extends AutoCloseable {

    @Override
    void close();
}
