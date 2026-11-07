package io.github.varnitta.plugkit.db.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.varnitta.plugkit.db.Params;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NamedSqlTest {

    @Test
    void replacesNamedParametersInOrder() {
        NamedSql.Bound bound = NamedSql.parse("select * from t where a = :a and b = :b_2")
                .bind(Params.of("b_2", 2, "a", 1));

        assertThat(bound.sql()).isEqualTo("select * from t where a = ? and b = ?");
        assertThat(bound.values()).containsExactly(1, 2);
    }

    @Test
    void repeatedNameBindsEachOccurrence() {
        NamedSql.Bound bound = NamedSql.parse("select :x, :x").bind(Params.of("x", "v"));

        assertThat(bound.sql()).isEqualTo("select ?, ?");
        assertThat(bound.values()).containsExactly("v", "v");
    }

    @Test
    void nullValuesAreBound() {
        NamedSql.Bound bound = NamedSql.parse("update t set a = :a").bind(Params.of("a", null));

        assertThat(bound.values()).containsExactly((Object) null);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "select ':a'",
            "select \"col:a\" from t",
            "select 1 -- :a\n",
            "select 1 /* :a */",
            "select $$ :a $$",
            "select $body$ :a $body$",
            "select now()::date",
            "select arr[1:2] from t",
            "select ':'",
            "select 'it''s :a'"
    })
    void leavesNonParametersUntouched(String sql) {
        NamedSql parsed = NamedSql.parse(sql);

        assertThat(parsed.parameterNames()).isEmpty();
        assertThat(parsed.bind(Params.empty()).sql()).isEqualTo(sql);
    }

    @Test
    void castAfterParameterIsKept() {
        NamedSql.Bound bound = NamedSql.parse("select :id::uuid").bind(Params.of("id", "x"));

        assertThat(bound.sql()).isEqualTo("select ?::uuid");
    }

    @Test
    void unterminatedQuoteDoesNotHideLaterText() {
        assertThat(NamedSql.parse("select 'abc").bind(Params.empty()).sql()).isEqualTo("select 'abc");
    }

    @Test
    void collectionsExpandForInLists() {
        NamedSql.Bound bound = NamedSql.parse("select * from t where id in (:ids) and s = :s")
                .bind(Params.of("ids", List.of(1, 2, 3), "s", "x"));

        assertThat(bound.sql()).isEqualTo("select * from t where id in (?, ?, ?) and s = ?");
        assertThat(bound.values()).containsExactly(1, 2, 3, "x");
    }

    @Test
    void singleElementCollection() {
        NamedSql.Bound bound = NamedSql.parse("where id in (:ids)").bind(Params.of("ids", Set.of(7)));

        assertThat(bound.sql()).isEqualTo("where id in (?)");
    }

    @Test
    void arraysAreBoundAsSingleValues() {
        Integer[] ids = {1, 2};
        NamedSql.Bound bound = NamedSql.parse("where id = any(:ids)").bind(Params.of("ids", ids));

        assertThat(bound.sql()).isEqualTo("where id = any(?)");
        assertThat(bound.values()).hasSize(1);
        assertThat(Arrays.equals((Integer[]) bound.values().getFirst(), ids)).isTrue();
    }

    @Test
    void emptyCollectionIsRejected() {
        assertThatThrownBy(() -> NamedSql.parse("where id in (:ids)").bind(Params.of("ids", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Parameter :ids is an empty collection");
    }

    @Test
    void missingParameterIsRejectedWithGivenNames() {
        assertThatThrownBy(() -> NamedSql.parse("where a = :a and b = :b").bind(Params.of("a", 1, "c", 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No value for parameter :b; given Params[a, c]");
    }

    @Test
    void extraParametersAreIgnored() {
        assertThat(NamedSql.parse("select :a").bind(Params.of("a", 1, "unused", 2)).values()).containsExactly(1);
    }
}
