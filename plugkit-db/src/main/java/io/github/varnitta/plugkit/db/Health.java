package io.github.varnitta.plugkit.db;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

public record Health(boolean healthy, Duration responseTime, @Nullable String problem) {

    public static Health up(Duration responseTime) {
        return new Health(true, responseTime, null);
    }

    public static Health down(Duration responseTime, String problem) {
        return new Health(false, responseTime, problem);
    }
}
