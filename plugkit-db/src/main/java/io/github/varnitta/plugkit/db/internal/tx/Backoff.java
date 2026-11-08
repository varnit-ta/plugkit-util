package io.github.varnitta.plugkit.db.internal.tx;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff with full jitter for transaction retries.
 *
 * <p>Before retry {@code n} (1-based) it waits a random time between 0 and
 * {@code min(MAX, BASE * 2^(n-1))}, which spreads competing transactions apart.
 */
public final class Backoff {

    static final Duration BASE = Duration.ofMillis(50);
    static final Duration MAX = Duration.ofSeconds(2);

    private Backoff() {
    }

    static Duration ceiling(int retry) {
        long millis = BASE.toMillis() << Math.min(retry - 1, 20);
        return Duration.ofMillis(Math.min(millis, MAX.toMillis()));
    }

    public static Duration delay(int retry) {
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(ceiling(retry).toMillis() + 1));
    }
}
