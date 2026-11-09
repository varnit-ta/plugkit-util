package io.github.varnitta.plugkit.db.internal.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.varnitta.plugkit.db.internal.dialect.mysql.MySqlDialect;
import io.github.varnitta.plugkit.db.internal.dialect.postgres.PostgresDialect;
import io.github.varnitta.plugkit.db.sql.Params;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NamedSqlTest {

    private static final SqlSyntax POSTGRES = new PostgresDialect().sqlSyntax();
    private static final SqlSyntax MYSQL = new MySqlDialect().sqlSyntax();

    @Test
    void replacesNamedParametersInOrder() {
        BoundSql bound = NamedSql.parse("select * from t where a = :a and b = :b_2", POSTGRES)
                .bind(Params.of("b_2", 2, "a", 1));

        assertThat(bound.sql()).isEqualTo("select * from t where a = ? and b = ?");
        assertThat(bound.values()).containsExactly(1, 2);
    }

    @Test
    void repeatedNameBindsEachOccurrence() {
        BoundSql bound = NamedSql.parse("select :x, :x", POSTGRES).bind(Params.of("x", "v"));

        assertThat(bound.sql()).isEqualTo("select ?, ?");
        assertThat(bound.values()).containsExactly("v", "v");
    }

    @Test
    void nullValuesAreBound() {
        BoundSql bound = NamedSql.parse("update t set a = :a", POSTGRES).bind(Params.of("a", null));

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
        NamedSql parsed = NamedSql.parse(sql, POSTGRES);

        assertThat(parsed.parameterNames()).isEmpty();
        assertThat(parsed.bind(Params.empty()).sql()).isEqualTo(sql);
    }

    @Test
    void castAfterParameterIsKept() {
        BoundSql bound = NamedSql.parse("select :id::uuid", POSTGRES).bind(Params.of("id", "x"));

        assertThat(bound.sql()).isEqualTo("select ?::uuid");
    }

    @Test
    void unterminatedQuoteDoesNotHideLaterText() {
        assertThat(NamedSql.parse("select 'abc", POSTGRES).bind(Params.empty()).sql()).isEqualTo("select 'abc");
    }

    @Test
    void collectionsExpandForInLists() {
        BoundSql bound = NamedSql.parse("select * from t where id in (:ids) and s = :s", POSTGRES)
                .bind(Params.of("ids", List.of(1, 2, 3), "s", "x"));

        assertThat(bound.sql()).isEqualTo("select * from t where id in (?, ?, ?) and s = ?");
        assertThat(bound.values()).containsExactly(1, 2, 3, "x");
    }

    @Test
    void singleElementCollection() {
        BoundSql bound = NamedSql.parse("where id in (:ids)", POSTGRES).bind(Params.of("ids", Set.of(7)));

        assertThat(bound.sql()).isEqualTo("where id in (?)");
    }

    @Test
    void arraysAreBoundAsSingleValues() {
        Integer[] ids = {1, 2};
        BoundSql bound = NamedSql.parse("where id = any(:ids)", POSTGRES).bind(Params.of("ids", ids));

        assertThat(bound.sql()).isEqualTo("where id = any(?)");
        assertThat(bound.values()).hasSize(1);
        assertThat(Arrays.equals((Integer[]) bound.values().getFirst(), ids)).isTrue();
    }

    @Test
    void emptyCollectionIsRejected() {
        assertThatThrownBy(() -> NamedSql.parse("where id in (:ids)", POSTGRES).bind(Params.of("ids", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Parameter :ids is an empty collection");
    }

    @Test
    void missingParameterIsRejectedWithGivenNames() {
        assertThatThrownBy(() -> NamedSql.parse("where a = :a and b = :b", POSTGRES).bind(Params.of("a", 1, "c", 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No value for parameter :b; given Params[a, c]");
    }

    @Test
    void extraParametersAreIgnored() {
        assertThat(NamedSql.parse("select :a", POSTGRES).bind(Params.of("a", 1, "unused", 2)).values()).containsExactly(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "select 1 as `col:a`",
            "select 'it\\'s :a'",
            "select 1 # :a\n",
            "select \"x:a\""
    })
    void mySqlQuotingRulesAreFollowed(String sql) {
        assertThat(NamedSql.parse(sql, MYSQL).parameterNames()).isEmpty();
    }

    @Test
    void mySqlParameterAfterBacktickIdentifierIsFound() {
        BoundSql bound = NamedSql.parse("select `user:id` from t where id = :id", MYSQL).bind(Params.of("id", 1));

        assertThat(bound.sql()).isEqualTo("select `user:id` from t where id = ?");
    }

    @Test
    void mySqlDashNeedsSpaceToStartAComment() {
        assertThat(NamedSql.parse("select 5--:a", MYSQL).parameterNames()).containsExactly("a");
        assertThat(NamedSql.parse("select 5 -- :a", MYSQL).parameterNames()).isEmpty();
    }

    @Test
    void postgresDoesNotTreatHashOrBacktickAsQuoting() {
        assertThat(NamedSql.parse("select 1 # :a", POSTGRES).parameterNames()).containsExactly("a");
        assertThat(NamedSql.parse("select $$ :a $$", MYSQL).parameterNames()).containsExactly("a");
    }
}
