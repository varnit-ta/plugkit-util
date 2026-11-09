package io.github.varnitta.plugkit.db.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs the annotated method again when it fails with a transient database error, such as a deadlock or
 * a serialization failure.
 *
 * <p>The retry wraps Spring's {@code @Transactional}, so every attempt runs in a fresh transaction.
 * Between attempts it waits a random time that grows with each retry. A method called while a
 * transaction is already active is not retried, because only the outermost transaction can be rerun.
 *
 * <pre>{@code
 * @RetryOnTransient(maxAttempts = 3)
 * @Transactional
 * public void transfer(long from, long to, BigDecimal amount) { ... }
 * }</pre>
 *
 * <p>On a class, it applies to every public method.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RetryOnTransient {

    /**
     * How many times the method runs in total, between 1 and 10.
     */
    int maxAttempts() default 3;
}
