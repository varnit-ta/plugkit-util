package io.github.varnitta.plugkit.db.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.varnitta.plugkit.core.exception.InvalidConfigException;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

final class ConfigProblems {

    private ConfigProblems() {
    }

    static List<String> configProblemsThrownBy(ThrowingCallable call) {
        InvalidConfigException exception = catchThrowableOfType(InvalidConfigException.class, call);
        assertThat(exception).as("expected InvalidConfigException").isNotNull();
        return exception.problems();
    }
}
