package io.github.varnitta.plugkit.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.varnitta.plugkit.db.config.PoolConfig;
import io.github.varnitta.plugkit.db.exception.AcquireTimeout;
import io.github.varnitta.plugkit.db.exception.NotNullViolation;
import io.github.varnitta.plugkit.db.exception.QueryFailure;
import io.github.varnitta.plugkit.db.exception.UniqueViolation;
import io.github.varnitta.plugkit.db.sql.Params;
import io.github.varnitta.plugkit.db.sql.Row;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class DatabaseQueriesIT {

    private static Database db;

    record User(long id, String email, String status, BigDecimal balance) {
        static User from(Row row) {
            return new User(row.require("id", Long.class), row.require("email", String.class),
                    row.require("status", String.class), row.require("balance", BigDecimal.class));
        }
    }

    enum Status { ACTIVE, BLOCKED }

    @BeforeAll
    static void start() {
        db = Databases.create(TestPostgres.config().poolName("queries").build());
        db.update("""
                create table if not exists users (
                    id bigserial primary key,
                    email text not null unique,
                    status text not null,
                    balance numeric(12, 2) not null default 0
                )""", Params.empty());
    }

    @AfterAll
    static void stop() {
        db.close();
    }

    @BeforeEach
    void clean() {
        db.update("truncate users restart identity", Params.empty());
    }

    @Test
    void insertsAndQueriesRows() {
        int inserted = db.update("insert into users (email, status, balance) values (:email, :status, :balance)",
                Params.of("email", "alice@example.com", "status", Status.ACTIVE, "balance", new BigDecimal("10.50")));

        List<User> users = db.query("select * from users where status = :status",
                Params.of("status", "ACTIVE"), User::from);

        assertThat(inserted).isEqualTo(1);
        assertThat(users).containsExactly(new User(1, "alice@example.com", "ACTIVE", new BigDecimal("10.50")));
    }

    @Test
    void queryOneReturnsEmptyOrSingleRow() {
        insertUser("alice@example.com");

        assertThat(db.queryOne("select * from users where email = :e", Params.of("e", "nobody"), User::from))
                .isEmpty();
        assertThat(db.queryOne("select * from users where email = :e", Params.of("e", "alice@example.com"),
                User::from)).map(User::email).contains("alice@example.com");
    }

    @Test
    void queryOneRejectsMultipleRows() {
        insertUser("a@example.com");
        insertUser("b@example.com");

        assertThatThrownBy(() -> db.queryOne("select * from users", Params.empty(), User::from))
                .isInstanceOf(QueryFailure.class)
                .hasMessageStartingWith("Expected at most one row");
    }

    @Test
    void returningClauseWorksWithQueryOne() {
        long id = db.queryOne("insert into users (email, status) values (:e, 'ACTIVE') returning id",
                Params.of("e", "new@example.com"), row -> row.require("id", Long.class)).orElseThrow();

        assertThat(id).isEqualTo(1L);
    }

    @Test
    void uuidAndInstantRoundTrip() {
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        record Values(UUID uuid, Instant instant) {
        }

        Values values = db.queryOne("select :uuid as u, :ts as ts", Params.of("uuid", uuid, "ts", now),
                row -> new Values(row.require("u", UUID.class), row.require("ts", Instant.class))).orElseThrow();

        assertThat(values).isEqualTo(new Values(uuid, now));
    }

    @Test
    void typedGettersHandleValuesAndNulls() {
        record Typed(Integer i, Long l, Boolean b, LocalDate d, BigDecimal n, String s, Long nullLong) {
        }

        Typed typed = db.queryOne("""
                select 42 as i, 9000000000 as l, true as b, date '2026-01-31' as d,
                       1.25::numeric as n, 'x' as s, null::bigint as nl""", Params.empty(),
                row -> new Typed(row.getInt("i"), row.getLong("l"), row.getBoolean("b"), row.getLocalDate("d"),
                        row.getBigDecimal("n"), row.getString("s"), row.getLong("nl"))).orElseThrow();

        assertThat(typed).isEqualTo(new Typed(42, 9_000_000_000L, true, LocalDate.of(2026, 1, 31),
                new BigDecimal("1.25"), "x", null));
    }

    @Test
    void requireFailsOnNull() {
        assertThatThrownBy(() -> db.queryOne("select null::text as v", Params.empty(),
                row -> row.require("v", String.class)))
                .isInstanceOf(QueryFailure.class)
                .hasMessage("Column 'v' is NULL but a value is required [pool=queries]");
    }

    @Test
    void inListExpandsCollections() {
        insertUser("a@example.com");
        insertUser("b@example.com");
        insertUser("c@example.com");

        List<String> emails = db.query("select email from users where email in (:emails) order by email",
                Params.of("emails", List.of("a@example.com", "c@example.com")), row -> row.getString("email"));

        assertThat(emails).containsExactly("a@example.com", "c@example.com");
    }

    @Test
    void uniqueViolationIsTranslatedWithConstraintName() {
        insertUser("alice@example.com");

        assertThatThrownBy(() -> insertUser("alice@example.com"))
                .isInstanceOfSatisfying(UniqueViolation.class, e ->
                        assertThat(e.constraintName()).contains("users_email_key"))
                .hasMessageContaining("[pool=queries, SQLState=23505]")
                .hasMessageNotContaining("alice@example.com");
    }

    @Test
    void notNullViolationIsTranslated() {
        assertThatThrownBy(() -> db.update("insert into users (email, status) values (:e, null)",
                Params.of("e", "x@example.com")))
                .isInstanceOf(NotNullViolation.class);
    }

    @Test
    void unknownColumnIsQueryFailure() {
        insertUser("alice@example.com");

        assertThatThrownBy(() -> db.query("select * from users", Params.empty(), row -> row.getString("nope")))
                .isInstanceOf(QueryFailure.class)
                .hasMessageContaining("pool=queries");
    }

    @Test
    void batchInsertsAllRows() {
        int[] counts = db.batch("insert into users (email, status) values (:e, 'ACTIVE')", List.of(
                Params.of("e", "a@example.com"), Params.of("e", "b@example.com"), Params.of("e", "c@example.com")));

        assertThat(counts).containsExactly(1, 1, 1);
        assertThat(countUsers()).isEqualTo(3);
    }

    @Test
    void batchIsAtomicAndDoesNotLeakValues() {
        insertUser("taken@example.com");

        assertThatThrownBy(() -> db.batch("insert into users (email, status) values (:e, 'ACTIVE')", List.of(
                Params.of("e", "fresh@example.com"), Params.of("e", "taken@example.com"))))
                .isInstanceOfSatisfying(UniqueViolation.class, e ->
                        assertThat(e.constraintName()).contains("users_email_key"))
                .hasMessageNotContaining("taken@example.com")
                .hasMessageNotContaining("fresh@example.com");

        assertThat(countUsers()).isEqualTo(1);
    }

    @Test
    void emptyBatchDoesNothing() {
        assertThat(db.batch("insert into users (email, status) values (:e, 'ACTIVE')", List.of())).isEmpty();
    }

    @Test
    void busyPoolFailsWithAcquireTimeout() throws Exception {
        try (Database small = Databases.create(TestPostgres.config()
                .poolName("small")
                .pool(PoolConfig.builder().maxSize(1).acquireTimeout(Duration.ofMillis(300)).build())
                .build())) {
            CompletableFuture<List<Integer>> holder = CompletableFuture.supplyAsync(() ->
                    small.query("select 1 as one from pg_sleep(2)", Params.empty(), row -> row.getInt("one")));
            Thread.sleep(500);

            assertThatThrownBy(() -> small.query("select 1", Params.empty(), row -> 1))
                    .isInstanceOf(AcquireTimeout.class)
                    .hasMessageContaining("pool=small, active=1, idle=0");

            assertThat(holder.get(10, TimeUnit.SECONDS)).containsExactly(1);
        }
    }

    @Test
    void readsByColumnIndexAndTypedTimeColumns() {
        record Values(Long byIndex, boolean hasInstant, boolean hasUuid) {
        }

        Values values = db.queryOne("select 7::bigint as n, now() as ts, gen_random_uuid() as u", Params.empty(),
                row -> new Values(row.get(1, Long.class), row.getInstant("ts") != null, row.getUuid("u") != null))
                .orElseThrow();

        assertThat(values).isEqualTo(new Values(7L, true, true));
    }

    @Test
    void batchRowsMustExpandToTheSameSql() {
        assertThatThrownBy(() -> db.batch("select * from users where id in (:ids)", List.of(
                Params.of("ids", List.of(1)), Params.of("ids", List.of(1, 2)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Every batch row must expand to the same SQL");
    }

    @Test
    void emptyBatchInsideATransactionDoesNothing() {
        int[] counts = db.inTransaction(tx -> tx.batch("insert into users (email, status) values (:e, 'ACTIVE')",
                List.of()));

        assertThat(counts).isEmpty();
    }

    @Test
    void longSlowStatementsStillRun() {
        try (Database verbose = Databases.create(TestPostgres.config()
                .poolName("verbose")
                .slowQueryThreshold(Duration.ofMillis(1))
                .build())) {
            String longSql = "select 1 as one /* " + "x".repeat(600) + " */ from pg_sleep(0.01)";

            assertThat(verbose.query(longSql, Params.empty(), row -> row.getInt("one"))).containsExactly(1);
        }
    }

    private static void insertUser(String email) {
        db.update("insert into users (email, status) values (:e, 'ACTIVE')", Params.of("e", email));
    }

    private static long countUsers() {
        return db.queryOne("select count(*) as c from users", Params.empty(), row -> row.require("c", Long.class))
                .orElseThrow();
    }
}
