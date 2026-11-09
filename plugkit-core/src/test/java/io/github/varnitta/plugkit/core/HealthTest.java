package io.github.varnitta.plugkit.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class HealthTest {

    @Test
    void upHasNoProblem() {
        Health health = Health.up(Duration.ofMillis(3));

        assertThat(health.healthy()).isTrue();
        assertThat(health.responseTime()).isEqualTo(Duration.ofMillis(3));
        assertThat(health.problem()).isNull();
    }

    @Test
    void downCarriesTheProblem() {
        Health health = Health.down(Duration.ofMillis(5), "connection refused");

        assertThat(health.healthy()).isFalse();
        assertThat(health.problem()).isEqualTo("connection refused");
    }
}
