package io.github.varnitta.plugkit.db.internal.spring;

import io.github.varnitta.plugkit.db.exception.DatabaseException;
import io.github.varnitta.plugkit.db.internal.tx.Backoff;
import io.github.varnitta.plugkit.db.spring.RetryOnTransient;
import io.github.varnitta.plugkit.db.tx.TxOptions;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Implements {@link RetryOnTransient}: reruns the method after a transient database failure, waiting
 * with {@link Backoff} between attempts.
 *
 * <p>Transient means a retryable plugkit-db exception, or Spring's {@link TransientDataAccessException},
 * which Spring's transaction manager throws for failures at commit. A call made while a transaction is
 * already active runs once, because only the outermost transaction can be rerun.
 */
final class RetryOnTransientInterceptor implements MethodInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(RetryOnTransientInterceptor.class);

    private final AnnotationLookup<RetryOnTransient, Integer> maxAttempts =
            new AnnotationLookup<>(RetryOnTransient.class, RetryOnTransientInterceptor::validMaxAttempts);

    @Override
    public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
        Integer attempts = maxAttempts.find(invocation);
        if (attempts == null || attempts == 1 || TransactionSynchronizationManager.isActualTransactionActive()
                || !(invocation instanceof ProxyMethodInvocation proxyInvocation)) {
            return invocation.proceed();
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return proxyInvocation.invocableClone().proceed();
            } catch (Throwable failure) {
                if (attempt >= attempts || !isTransient(failure)) {
                    throw failure;
                }
                LOG.info("Retrying {} after {} (attempt {} of {})", invocation.getMethod().getName(),
                        failure.getClass().getSimpleName(), attempt + 1, attempts);
                Thread.sleep(Backoff.delay(attempt));
            }
        }
    }

    static boolean isTransient(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof DatabaseException databaseFailure) {
                return databaseFailure.isRetryable();
            }
            if (current instanceof TransientDataAccessException) {
                return true;
            }
        }
        return false;
    }

    private static Integer validMaxAttempts(RetryOnTransient annotation) {
        int attempts = annotation.maxAttempts();
        if (attempts < 1 || attempts > TxOptions.MAX_ATTEMPTS_LIMIT) {
            throw new IllegalArgumentException("@RetryOnTransient maxAttempts must be between 1 and "
                    + TxOptions.MAX_ATTEMPTS_LIMIT + ", was " + attempts);
        }
        return attempts;
    }
}
