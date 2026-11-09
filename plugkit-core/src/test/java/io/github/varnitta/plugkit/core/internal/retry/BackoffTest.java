package io.github.varnitta.plugkit.core.internal.retry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BackoffTest {

    @ParameterizedTest
    @CsvSource({"1, 50", "2, 100", "3, 200", "6, 1600", "7, 2000", "30, 2000"})
    void ceilingDoublesUpToTheMaximum(int retry, long expectedMillis) {
        assertThat(Backoff.ceiling(retry)).isEqualTo(Duration.ofMillis(expectedMillis));
    }

    @RepeatedTest(50)
    void delayStaysWithinTheCeiling() {
        assertThat(Backoff.delay(3)).isBetween(Duration.ZERO, Backoff.ceiling(3));
    }
}
