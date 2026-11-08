package io.github.varnitta.plugkit.db.tx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.varnitta.plugkit.db.exception.InvalidConfigException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TxOptionsTest {

    @Test
    void defaultsUseConfiguredTimeoutsAndNoRetry() {
        TxOptions options = TxOptions.defaults();

        assertThat(options.isolation()).isNull();
        assertThat(options.readOnly()).isFalse();
        assertThat(options.timeout()).isNull();
        assertThat(options.statementTimeout()).isNull();
        assertThat(options.maxAttempts()).isEqualTo(1);
    }

    @Test
    void builderSetsEveryOption() {
        TxOptions options = TxOptions.builder()
                .isolation(Isolation.SERIALIZABLE)
                .readOnly(true)
                .timeout(Duration.ofMinutes(10))
                .statementTimeout(Duration.ofMinutes(5))
                .maxAttempts(3)
                .build();

        assertThat(options).isEqualTo(new TxOptions(Isolation.SERIALIZABLE, true, Duration.ofMinutes(10),
                Duration.ofMinutes(5), 3));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 11})
    void maxAttemptsMustBeInRange(int maxAttempts) {
        assertThatThrownBy(() -> TxOptions.builder().maxAttempts(maxAttempts).build())
                .isInstanceOf(InvalidConfigException.class)
                .hasMessageContaining("tx.maxAttempts must be between 1 and 10, was " + maxAttempts);
    }

    @Test
    void timeoutsMustBePositive() {
        assertThatThrownBy(() -> TxOptions.builder()
                .timeout(Duration.ZERO)
                .statementTimeout(Duration.ofSeconds(-1))
                .build())
                .isInstanceOfSatisfying(InvalidConfigException.class, e -> assertThat(e.problems()).containsExactly(
                        "tx.timeout must be at least 1ms, was 0ms",
                        "tx.statementTimeout must be at least 1ms, was -1s"));
    }
}
