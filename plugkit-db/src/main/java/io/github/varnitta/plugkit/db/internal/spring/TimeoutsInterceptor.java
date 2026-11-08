package io.github.varnitta.plugkit.db.internal.spring;

import io.github.varnitta.plugkit.db.internal.tx.TimeoutOverrides;
import io.github.varnitta.plugkit.db.spring.Timeouts;
import java.time.Duration;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.convert.DurationStyle;

/**
 * Implements {@link Timeouts}: applies the annotation's timeouts as {@link TimeoutOverrides} for the
 * duration of the method call.
 */
final class TimeoutsInterceptor implements MethodInterceptor {

    private final AnnotationLookup<Timeouts, TimeoutOverrides.Overrides> overrides =
            new AnnotationLookup<>(Timeouts.class, TimeoutsInterceptor::parse);

    @Override
    public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
        TimeoutOverrides.Overrides settings = overrides.find(invocation);
        if (settings == null) {
            return invocation.proceed();
        }
        try (TimeoutOverrides.Scope scope = TimeoutOverrides.apply(settings.statement(), settings.transaction())) {
            return invocation.proceed();
        }
    }

    static TimeoutOverrides.Overrides parse(Timeouts annotation) {
        return new TimeoutOverrides.Overrides(duration("statement", annotation.statement()),
                duration("transaction", annotation.transaction()));
    }

    private static @Nullable Duration duration(String attribute, String value) {
        if (value.isBlank()) {
            return null;
        }
        Duration duration;
        try {
            duration = DurationStyle.detectAndParse(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("@Timeouts " + attribute + " '" + value
                    + "' is not a duration; use a value such as 500ms, 30s or 5m", e);
        }
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("@Timeouts " + attribute + " must be positive, was '" + value + "'");
        }
        return duration;
    }
}
