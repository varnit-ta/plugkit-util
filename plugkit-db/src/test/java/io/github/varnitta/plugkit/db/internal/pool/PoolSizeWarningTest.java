package io.github.varnitta.plugkit.db.internal.pool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PoolSizeWarningTest {

    @ParameterizedTest
    @CsvSource({
            "10, 100, false",
            "50, 100, false",
            "51, 100, true",
            "10, 15, true",
            "1, 2, false"
    })
    void warnsWhenPoolTakesMoreThanHalfOfMaxConnections(int maxSize, int maxConnections, boolean expected) {
        assertThat(PooledDatabase.isLargeShare(maxSize, maxConnections)).isEqualTo(expected);
    }
}
