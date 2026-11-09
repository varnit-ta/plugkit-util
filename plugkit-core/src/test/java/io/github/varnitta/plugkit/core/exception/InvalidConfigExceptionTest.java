package io.github.varnitta.plugkit.core.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvalidConfigExceptionTest {

    @Test
    void messageListsEachProblemOnItsOwnLine() {
        InvalidConfigException exception = new InvalidConfigException(List.of("url is required", "pool.maxSize bad"));

        assertThat(exception).hasMessage(String.join(System.lineSeparator(),
                "Invalid plugkit configuration:",
                "  - url is required",
                "  - pool.maxSize bad"));
    }

    @Test
    void problemsAreAnImmutableCopy() {
        List<String> problems = new ArrayList<>(List.of("a"));
        InvalidConfigException exception = new InvalidConfigException(problems);
        problems.add("b");

        assertThat(exception.problems()).containsExactly("a");
        assertThatThrownBy(() -> exception.problems().add("c")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void isAPlugkitExceptionThatIsNotRetryable() {
        InvalidConfigException exception = new InvalidConfigException(List.of("a"));

        assertThat(exception).isInstanceOf(PlugkitException.class).hasNoCause();
        assertThat(exception.isRetryable()).isFalse();
    }
}
