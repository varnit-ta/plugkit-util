package io.github.varnitta.plugkit.db.config;

import static io.github.varnitta.plugkit.db.config.ConfigProblems.configProblemsThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PoolConfigTest {

    @Test
    void defaultsMatchTheDesign() {
        PoolConfig pool = PoolConfig.defaults();

        assertThat(pool.maxSize()).isEqualTo(10);
        assertThat(pool.minIdle()).isEqualTo(10);
        assertThat(pool.acquireTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(pool.maxLifetime()).isEqualTo(Duration.ofMinutes(30));
        assertThat(pool.idleTimeout()).isEqualTo(Duration.ofMinutes(10));
        assertThat(pool.keepalive()).isEqualTo(Duration.ofMinutes(2));
        assertThat(pool.leakDetection()).isEqualTo(Duration.ofSeconds(60));
        assertThat(pool.shutdownTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void minIdleFollowsMaxSizeUnlessSet() {
        assertThat(PoolConfig.builder().maxSize(25).build().minIdle()).isEqualTo(25);
        assertThat(PoolConfig.builder().maxSize(25).minIdle(5).build().minIdle()).isEqualTo(5);
    }

    static Stream<Arguments> invalidSettings() {
        return Stream.of(
                invalid(b -> b.maxSize(0), "pool.maxSize must be at least 1, was 0"),
                invalid(b -> b.maxSize(5).minIdle(6), "pool.minIdle must be between 0 and pool.maxSize (5), was 6"),
                invalid(b -> b.minIdle(-1), "pool.minIdle must be between 0 and pool.maxSize (10), was -1"),
                invalid(b -> b.acquireTimeout(Duration.ofMillis(100)), "pool.acquireTimeout must be at least 250ms, was 100ms"),
                invalid(b -> b.acquireTimeout(Duration.ZERO), "pool.acquireTimeout must be at least 250ms, was 0ms"),
                invalid(b -> b.maxLifetime(Duration.ofSeconds(10)), "pool.maxLifetime must be at least 30s, was 10s"),
                invalid(b -> b.idleTimeout(Duration.ofSeconds(5)), "pool.idleTimeout must be at least 10s, was 5s"),
                invalid(b -> b.idleTimeout(Duration.ofMinutes(30)),
                        "pool.idleTimeout must be less than pool.maxLifetime (30m), was 30m"),
                invalid(b -> b.keepalive(Duration.ofSeconds(20)), "pool.keepalive must be at least 30s, was 20s"),
                invalid(b -> b.maxLifetime(Duration.ofMinutes(1)).idleTimeout(Duration.ofSeconds(30))
                                .leakDetection(Duration.ofSeconds(30)),
                        "pool.keepalive must be less than pool.maxLifetime (1m), was 2m"),
                invalid(b -> b.leakDetection(Duration.ofSeconds(1)), "pool.leakDetection must be at least 2s, was 1s"),
                invalid(b -> b.shutdownTimeout(Duration.ofSeconds(-1)), "pool.shutdownTimeout must be at least 0ms, was -1s"),
                invalid(b -> b.leakDetection(Duration.ofHours(1)),
                        "pool.leakDetection must be less than pool.maxLifetime (30m), was 60m"));
    }

    @ParameterizedTest
    @MethodSource("invalidSettings")
    void rejectsInvalidSetting(Consumer<PoolConfig.PoolConfigBuilder> customizer, String expectedProblem) {
        PoolConfig.PoolConfigBuilder builder = PoolConfig.builder();
        customizer.accept(builder);

        assertThat(configProblemsThrownBy(builder::build))
                .containsExactly(expectedProblem);
    }

    @Test
    void zeroShutdownTimeoutIsAllowed() {
        assertThat(PoolConfig.builder().shutdownTimeout(Duration.ZERO).build().shutdownTimeout()).isZero();
    }

    @Test
    void reportsAllProblemsAtOnce() {
        assertThat(configProblemsThrownBy(() -> PoolConfig.builder().maxSize(0).acquireTimeout(Duration.ZERO).build()))
                .containsExactly(
                        "pool.maxSize must be at least 1, was 0",
                        "pool.acquireTimeout must be at least 250ms, was 0ms");
    }

    private static Arguments invalid(Consumer<PoolConfig.PoolConfigBuilder> customizer, String expectedProblem) {
        return Arguments.of(customizer, expectedProblem);
    }
}
