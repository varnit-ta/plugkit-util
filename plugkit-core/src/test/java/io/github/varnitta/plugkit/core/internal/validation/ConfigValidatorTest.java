package io.github.varnitta.plugkit.core.internal.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.varnitta.plugkit.core.exception.InvalidConfigException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConfigValidatorTest {

    @Test
    void topLevelKeysHaveNoPrefix() {
        assertThat(ConfigValidator.topLevel().reject("url", "is required").problems())
                .containsExactly("url is required");
    }

    @Test
    void sectionKeysArePrefixed() {
        assertThat(ConfigValidator.section("pool").reject("maxSize", "must be at least 1").problems())
                .containsExactly("pool.maxSize must be at least 1");
    }

    @Test
    void requireAddsAProblemOnlyWhenTheConditionIsFalse() {
        ConfigValidator validator = ConfigValidator.section("pool")
                .require("maxSize", true, "never reported")
                .require("minIdle", false, "must not be negative");

        assertThat(validator.problems()).containsExactly("pool.minIdle must not be negative");
    }

    @Test
    void requireAtLeastReportsTheLimitAndTheValue() {
        ConfigValidator validator = ConfigValidator.section("pool")
                .requireAtLeast("acquireTimeout", Duration.ofMillis(250), Duration.ofMillis(250))
                .requireAtLeast("maxLifetime", Duration.ofSeconds(10), Duration.ofSeconds(30));

        assertThat(validator.problems()).containsExactly("pool.maxLifetime must be at least 30s, was 10s");
    }

    @Test
    void requirePositiveRejectsZeroAndNegative() {
        ConfigValidator validator = ConfigValidator.section("tx")
                .requirePositive("a", Duration.ofMillis(1))
                .requirePositive("b", Duration.ZERO)
                .requirePositive("c", Duration.ofSeconds(-1));

        assertThat(validator.problems()).containsExactly(
                "tx.b must be at least 1ms, was 0ms",
                "tx.c must be at least 1ms, was -1s");
    }

    @Test
    void requireNotNegativeAllowsZero() {
        ConfigValidator validator = ConfigValidator.section("pool")
                .requireNotNegative("shutdownTimeout", Duration.ZERO)
                .requireNotNegative("other", Duration.ofSeconds(-2));

        assertThat(validator.problems()).containsExactly("pool.other must be at least 0ms, was -2s");
    }

    @Test
    void requireLessThanNamesTheOtherKey() {
        ConfigValidator validator = ConfigValidator.section("pool")
                .requireLessThan("idleTimeout", Duration.ofMinutes(5), "maxLifetime", Duration.ofMinutes(30))
                .requireLessThan("keepalive", Duration.ofMinutes(30), "maxLifetime", Duration.ofMinutes(30));

        assertThat(validator.problems())
                .containsExactly("pool.keepalive must be less than pool.maxLifetime (30m), was 30m");
    }

    @Test
    void throwIfInvalidThrowsWithEveryProblem() {
        ConfigValidator validator = ConfigValidator.topLevel().reject("a", "is bad").reject("b", "is worse");

        assertThatThrownBy(validator::throwIfInvalid)
                .isInstanceOfSatisfying(InvalidConfigException.class, e ->
                        assertThat(e.problems()).containsExactly("a is bad", "b is worse"));
    }

    @Test
    void throwIfInvalidDoesNothingWithoutProblems() {
        assertThatCode(() -> ConfigValidator.section("x").require("y", true, "unused").throwIfInvalid())
                .doesNotThrowAnyException();
    }

    @Test
    void problemsCannotBeModified() {
        ConfigValidator validator = ConfigValidator.topLevel().reject("a", "is bad");

        assertThatThrownBy(() -> validator.problems().add("b"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @CsvSource({"0, 0ms", "500, 500ms", "1500, 1500ms", "2000, 2s", "90000, 90s", "300000, 5m",
            "3600000, 60m", "-1000, -1s"})
    void formatsDurationsInTheLargestWholeUnit(long millis, String expected) {
        assertThat(ConfigValidator.formatDuration(Duration.ofMillis(millis))).isEqualTo(expected);
    }
}
