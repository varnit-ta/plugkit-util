package io.github.varnitta.plugkit.core;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Result of a health check on a resource, such as {@code Database.health()} in plugkit-db.
 *
 * <p>A health check never throws; a failure is reported as {@code healthy = false} with the reason.
 *
 * @param healthy      whether the resource answered in time
 * @param responseTime how long the check took
 * @param problem      why the check failed, or {@code null} when healthy
 */
public record Health(boolean healthy, Duration responseTime, @Nullable String problem) {

    public static Health up(Duration responseTime) {
        return new Health(true, responseTime, null);
    }

    public static Health down(Duration responseTime, String problem) {
        return new Health(false, responseTime, problem);
    }
}
