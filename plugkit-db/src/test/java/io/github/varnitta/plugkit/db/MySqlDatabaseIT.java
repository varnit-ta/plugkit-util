package io.github.varnitta.plugkit.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.varnitta.plugkit.db.config.PoolConfig;
import io.github.varnitta.plugkit.db.config.SessionConfig;
import io.github.varnitta.plugkit.db.exception.LockTimeout;
import io.github.varnitta.plugkit.db.exception.NotNullViolation;
import io.github.varnitta.plugkit.db.exception.UniqueViolation;
import io.github.varnitta.plugkit.db.sql.Params;
import io.github.varnitta.plugkit.db.tx.TxOptions;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class MySqlDatabaseIT {

    private static Database db;

    @BeforeAll
    static void start() {
        db = Databases.create(TestMySql.config().build());
        db.update("""
                create table if not exists users (
                    id bigint auto_increment primary key,
                    email varchar(200) not null,
                    balance int not null default 0,
                    constraint users_email_key unique (email)
                )""", Params.empty());
    }

    @AfterAll
    static void stop() {
        db.close();
    }

    @BeforeEach
    void clean() {
        db.update("delete from users", Params.empty());
    }

    @Test
    void detectsMySqlAndAppliesSessionVariables() {
        record Settings(long maxExecutionTime, long lockWaitTimeout) {
        }

        Settings settings = db.queryOne("select @@max_execution_time as m, @@innodb_lock_wait_timeout as l",
                Params.empty(), row -> new Settings(row.getLong("m"), row.getLong("l"))).orElseThrow();

        assertThat(db.databaseType()).isEqualTo(DatabaseType.MYSQL);
        assertThat(settings).isEqualTo(new Settings(30_000, 10));
    }

    @Test
    void insertsAndQueriesRows() {
        long id = insertUser("alice@example.com");

        List<String> emails = db.query("select email from users where id in (:ids)", Params.of("ids", List.of(id)),
                row -> row.getString("email"));

        assertThat(emails).containsExactly("alice@example.com");
    }

    @Test
    void uniqueViolationNamesTheKeyWithoutTheValue() {
        insertUser("alice@example.com");

        assertThatThrownBy(() -> insertUser("alice@example.com"))
                .isInstanceOfSatisfying(UniqueViolation.class, e ->
                        assertThat(e.constraintName()).hasValueSatisfying(key -> assertThat(key).contains("users_email_key")))
                .hasMessageContaining("pool=mysql-it, SQLState=23000, errorCode=1062")
                .hasMessageNotContaining("alice@example.com");
    }

    @Test
    void notNullViolationIsTranslated() {
        assertThatThrownBy(() -> db.update("insert into users (email) values (:e)", Params.of("e", null)))
                .isInstanceOf(NotNullViolation.class);
    }

    @Test
    void mySqlQuotingIsNotMistakenForParameters() {
        String sql = """
                select 1 as `value:one`, 'it\\'s :not' as quoted # trailing :comment
                from dual where 1 = :one""";

        List<Integer> result = db.query(sql, Params.of("one", 1), row -> row.getInt("value:one"));

        assertThat(result).containsExactly(1);
    }

    @Test
    void lockWaitTimeoutIsTranslated() throws Exception {
        long id = insertUser("locked@example.com");
        try (Database impatient = Databases.create(TestMySql.config()
                .poolName("impatient")
                .session(SessionConfig.builder().lockTimeout(Duration.ofSeconds(1)).build())
                .build())) {
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> db.inTransaction(tx -> {
                tx.update("update users set balance = 1 where id = :id", Params.of("id", id));
                locked.countDown();
                await(release);
                return null;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> impatient.update("update users set balance = 2 where id = :id",
                    Params.of("id", id)))
                    .isInstanceOf(LockTimeout.class)
                    .hasMessageContaining("errorCode=1205");

            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void deadlockIsRetriedWhenEnabled() throws Exception {
        long first = insertUser("first@example.com");
        long second = insertUser("second@example.com");
        AtomicInteger attempts = new AtomicInteger();
        CyclicBarrier bothLocked = new CyclicBarrier(2);
        TxOptions retry = TxOptions.builder().maxAttempts(3).build();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = pool.submit(() -> transfer(retry, attempts, bothLocked, first, second));
            Future<?> b = pool.submit(() -> transfer(retry, attempts, bothLocked, second, first));
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(attempts).hasValue(3);
        assertThat(db.query("select balance from users order by id", Params.empty(), row -> row.getInt("balance")))
                .containsExactly(0, 0);
    }

    @Test
    void transactionStatementTimeoutIsRestoredAfterwards() {
        try (Database single = Databases.create(TestMySql.config()
                .poolName("single")
                .pool(PoolConfig.builder().maxSize(1).build())
                .build())) {
            long inside = single.inTransaction(TxOptions.builder().statementTimeout(Duration.ofMinutes(2)).build(),
                    tx -> tx.queryOne("select @@max_execution_time as m", Params.empty(), row -> row.getLong("m"))
                            .orElseThrow());
            long after = single.queryOne("select @@max_execution_time as m", Params.empty(), row -> row.getLong("m"))
                    .orElseThrow();

            assertThat(inside).isEqualTo(120_000);
            assertThat(after).isEqualTo(30_000);
        }
    }

    @Test
    void reportsHealthy() {
        assertThat(db.health().healthy()).isTrue();
    }

    private static long insertUser(String email) {
        return db.inTransaction(tx -> {
            tx.update("insert into users (email) values (:e)", Params.of("e", email));
            return tx.queryOne("select last_insert_id() as id", Params.empty(), row -> row.getLong("id")).orElseThrow();
        });
    }

    private static void transfer(TxOptions options, AtomicInteger attempts, CyclicBarrier barrier, long from, long to) {
        AtomicInteger mine = new AtomicInteger();
        db.inTransaction(options, tx -> {
            attempts.incrementAndGet();
            tx.update("update users set balance = balance - 5 where id = :id", Params.of("id", from));
            if (mine.incrementAndGet() == 1) {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            tx.update("update users set balance = balance + 5 where id = :id", Params.of("id", to));
            return null;
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
