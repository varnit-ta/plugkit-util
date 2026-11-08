package io.github.varnitta.plugkit.db.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ParamsTest {

    @Test
    void holdsValuesInInsertionOrder() {
        Params params = Params.of("b", 2, "a", 1).and("c", 3);

        assertThat(params.names()).containsExactly("b", "a", "c");
        assertThat(params.get("a")).isEqualTo(1);
        assertThat(params.contains("c")).isTrue();
        assertThat(params.contains("d")).isFalse();
    }

    @Test
    void allowsNullValues() {
        Params params = Params.of("a", null);

        assertThat(params.contains("a")).isTrue();
        assertThat(params.get("a")).isNull();
    }

    @Test
    void andReturnsNewInstance() {
        Params original = Params.of("a", 1);
        Params extended = original.and("b", 2);

        assertThat(original.names()).containsExactly("a");
        assertThat(extended.names()).containsExactly("a", "b");
    }

    @Test
    void fromMapKeepsNulls() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("x", null);
        values.put("y", "v");

        assertThat(Params.of(values)).isEqualTo(Params.of("x", null, "y", "v"));
    }

    @Test
    void duplicateNamesAreRejected() {
        assertThatThrownBy(() -> Params.of("a", 1, "a", 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Parameter :a is already set");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1a", "a-b", "a b", ":a"})
    void invalidNamesAreRejected(String name) {
        assertThatThrownBy(() -> Params.of(name, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid parameter name");
    }

    @Test
    void toStringShowsNamesButNotValues() {
        assertThat(Params.of("email", "alice@example.com", "password", "hunter2"))
                .hasToString("Params[email, password]");
    }

    @Test
    void emptyIsShared() {
        assertThat(Params.empty()).isSameAs(Params.empty());
        assertThat(Params.empty().names()).isEmpty();
    }
}
