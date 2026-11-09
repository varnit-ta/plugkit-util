package io.github.varnitta.plugkit.db.internal.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.varnitta.plugkit.db.exception.Deadlock;
import io.github.varnitta.plugkit.db.exception.UniqueViolation;
import io.github.varnitta.plugkit.db.internal.tx.TimeoutOverride;
import io.github.varnitta.plugkit.db.internal.tx.TimeoutOverrideScope;
import io.github.varnitta.plugkit.db.internal.tx.TimeoutOverrides;
import io.github.varnitta.plugkit.db.spring.Timeouts;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

class InterceptorSettingsTest {

    @Test
    @Timeouts(statement = "5m", transaction = "1500ms")
    void timeoutsAreParsed() throws NoSuchMethodException {
        Timeouts annotation = getClass().getDeclaredMethod("timeoutsAreParsed").getAnnotation(Timeouts.class);

        assertThat(TimeoutsInterceptor.parse(annotation))
                .isEqualTo(new TimeoutOverride(Duration.ofMinutes(5), Duration.ofMillis(1500)));
    }

    @Test
    @Timeouts(statement = "30s")
    void emptyTimeoutKeepsTheDefault() throws NoSuchMethodException {
        Timeouts annotation = getClass().getDeclaredMethod("emptyTimeoutKeepsTheDefault").getAnnotation(Timeouts.class);

        assertThat(TimeoutsInterceptor.parse(annotation).transaction()).isNull();
    }

    @Test
    @Timeouts(statement = "soon")
    void invalidTimeoutIsRejected() throws NoSuchMethodException {
        Timeouts annotation = getClass().getDeclaredMethod("invalidTimeoutIsRejected").getAnnotation(Timeouts.class);

        assertThatThrownBy(() -> TimeoutsInterceptor.parse(annotation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("@Timeouts statement 'soon' is not a duration; use a value such as 500ms, 30s or 5m");
    }

    @Test
    @Timeouts(statement = "0s")
    void nonPositiveTimeoutIsRejected() throws NoSuchMethodException {
        Timeouts annotation = getClass().getDeclaredMethod("nonPositiveTimeoutIsRejected").getAnnotation(Timeouts.class);

        assertThatThrownBy(() -> TimeoutsInterceptor.parse(annotation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("@Timeouts statement must be positive, was '0s'");
    }

    @Test
    void transientFailuresAreRecognised() {
        assertThat(RetryOnTransientInterceptor.isTransient(new Deadlock("d", "40P01", null))).isTrue();
        assertThat(RetryOnTransientInterceptor.isTransient(new IllegalStateException(new Deadlock("d", "40P01", null))))
                .isTrue();
        assertThat(RetryOnTransientInterceptor.isTransient(new CannotAcquireLockException("lock"))).isTrue();
        assertThat(RetryOnTransientInterceptor.isTransient(new UniqueViolation("u", null, "23505", null))).isFalse();
        assertThat(RetryOnTransientInterceptor.isTransient(new DataIntegrityViolationException("x"))).isFalse();
        assertThat(RetryOnTransientInterceptor.isTransient(new IllegalStateException())).isFalse();
    }

    @Test
    void overridesNestAndRestore() {
        try (TimeoutOverrideScope outer = TimeoutOverrides.apply(Duration.ofMinutes(1), null)) {
            try (TimeoutOverrideScope inner = TimeoutOverrides.apply(Duration.ofMinutes(5), Duration.ofMinutes(10))) {
                assertThat(TimeoutOverrides.statement()).isEqualTo(Duration.ofMinutes(5));
                assertThat(TimeoutOverrides.transaction()).isEqualTo(Duration.ofMinutes(10));
            }
            assertThat(TimeoutOverrides.statement()).isEqualTo(Duration.ofMinutes(1));
            assertThat(TimeoutOverrides.transaction()).isNull();
        }
        assertThat(TimeoutOverrides.statement()).isNull();
    }
}
