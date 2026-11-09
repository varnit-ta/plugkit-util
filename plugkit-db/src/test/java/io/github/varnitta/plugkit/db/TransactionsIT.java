package io.github.varnitta.plugkit.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.varnitta.plugkit.db.config.PoolConfig;
import io.github.varnitta.plugkit.db.config.SessionConfig;
import io.github.varnitta.plugkit.db.exception.Deadlock;
import io.github.varnitta.plugkit.db.exception.QueryFailure;
import io.github.varnitta.plugkit.db.exception.StatementTimeout;
import io.github.varnitta.plugkit.db.exception.TransactionTimeout;
import io.github.varnitta.plugkit.db.exception.UniqueViolation;
import io.github.varnitta.plugkit.db.sql.Params;
import io.github.varnitta.plugkit.db.sql.SqlOperations;
import io.github.varnitta.plugkit.db.tx.Isolation;
import io.github.varnitta.plugkit.db.tx.Transaction;
import io.github.varnitta.plugkit.db.tx.TxOptions;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
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
class TransactionsIT {

    private static Database db;

    @BeforeAll
    static void start() {
        db = Databases.create(TestPostgres.config().poolName("tx").pool(PoolConfig.builder().maxSize(4).build())
                .build());
        db.update("create table if not exists accounts (id int primary key, balance int not null)", Params.empty());
    }

    @AfterAll
    static void stop() {
        db.close();
    }

    @BeforeEach
    void seed() {
        db.update("truncate accounts", Params.empty());
        db.batch("insert into accounts (id, balance) values (:id, 0)", List.of(Params.of("id", 1), Params.of("id", 2)));
    }

    @Test
    void commitsEveryStatementTogether() {
        String result = db.inTransaction(tx -> {
            move(tx, 1, 2, 10);
            return "moved";
        });

        assertThat(result).isEqualTo("moved");
        assertThat(balances()).containsExactly(-10, 10);
    }

    @Test
    void callbackExceptionRollsBackAndPropagates() {
        IllegalStateException failure = new IllegalStateException("boom");

        assertThatThrownBy(() -> db.inTransaction(tx -> {
            move(tx, 1, 2, 10);
            throw failure;
        })).isSameAs(failure);

        assertThat(balances()).containsExactly(0, 0);
    }

    @Test
    void databaseErrorRollsBackEarlierStatements() {
        assertThatThrownBy(() -> db.inTransaction(tx -> {
            move(tx, 1, 2, 10);
            return tx.update("insert into accounts (id, balance) values (1, 0)", Params.empty());
        })).isInstanceOf(UniqueViolation.class);

        assertThat(balances()).containsExactly(0, 0);
    }

    @Test
    void readOnlyTransactionRejectsWrites() {
        assertThatThrownBy(() -> db.inTransaction(TxOptions.builder().readOnly(true).build(),
                tx -> tx.update("update accounts set balance = 1", Params.empty())))
                .isInstanceOf(QueryFailure.class)
                .hasMessageContaining("SQLState=25006");
    }

    @Test
    void isolationAndReadOnlyApplyInsideAndResetAfter() {
        TxOptions options = TxOptions.builder()
                .isolation(Isolation.SERIALIZABLE)
                .readOnly(true)
                .statementTimeout(Duration.ofSeconds(7))
                .build();

        List<String> inside = db.inTransaction(options, tx -> List.of(
                show(tx, "transaction_isolation"), show(tx, "transaction_read_only"), show(tx, "statement_timeout")));

        assertThat(inside).containsExactly("serializable", "on", "7s");
        for (int i = 0; i < 4; i++) {
            assertThat(show(db, "transaction_isolation")).isEqualTo("read committed");
            assertThat(show(db, "statement_timeout")).isEqualTo("30s");
        }
    }

    @Test
    void transactionStatementTimeoutCanBeTighter() {
        assertThatThrownBy(() -> db.inTransaction(TxOptions.builder().statementTimeout(Duration.ofSeconds(1)).build(),
                tx -> tx.query("select pg_sleep(3)", Params.empty(), row -> 1)))
                .isInstanceOf(StatementTimeout.class);
    }

    @Test
    void longerStatementTimeoutAlsoRaisesTheSocketTimeout() {
        try (Database shortSession = Databases.create(TestPostgres.config()
                .poolName("short")
                .session(SessionConfig.builder().statementTimeout(Duration.ofSeconds(1)).build())
                .build())) {
            TxOptions longStatements = TxOptions.builder()
                    .statementTimeout(Duration.ofSeconds(10))
                    .timeout(Duration.ofSeconds(20))
                    .build();

            List<Integer> result = shortSession.inTransaction(longStatements,
                    tx -> tx.query("select 1 as one from pg_sleep(7)", Params.empty(), row -> row.getInt("one")));

            assertThat(result).containsExactly(1);
        }
    }

    @Test
    void deadlineCancelsARunningStatement() {
        TxOptions options = TxOptions.builder().timeout(Duration.ofMillis(1500)).build();

        assertThatThrownBy(() -> db.inTransaction(options, tx -> {
            tx.query("select pg_sleep(1)", Params.empty(), row -> 1);
            return tx.query("select pg_sleep(1)", Params.empty(), row -> 1);
        }))
                .isInstanceOf(TransactionTimeout.class)
                .hasMessage("Transaction exceeded its timeout of 1500ms [pool=tx]");
    }

    @Test
    void deadlinePassedBeforeCommitRollsBack() {
        TxOptions options = TxOptions.builder().timeout(Duration.ofMillis(300)).build();

        assertThatThrownBy(() -> db.inTransaction(options, tx -> {
            move(tx, 1, 2, 10);
            sleep(Duration.ofMillis(400));
            return null;
        })).isInstanceOf(TransactionTimeout.class);

        assertThat(balances()).containsExactly(0, 0);
    }

    @Test
    void statementAfterDeadlineFailsWithoutRunning() {
        TxOptions options = TxOptions.builder().timeout(Duration.ofMillis(200)).build();

        assertThatThrownBy(() -> db.inTransaction(options, tx -> {
            sleep(Duration.ofMillis(300));
            return move(tx, 1, 2, 10);
        })).isInstanceOf(TransactionTimeout.class).hasNoCause();
    }

    @Test
    void transactionCannotBeUsedAfterItsCallback() {
        Transaction[] escaped = new Transaction[1];
        db.inTransaction(tx -> {
            escaped[0] = tx;
            return null;
        });

        assertThatThrownBy(() -> escaped[0].update("update accounts set balance = 1", Params.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Transaction is no longer active; use it only inside its callback");
    }

    @Test
    void deadlockIsRetriedWhenEnabled() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        List<Future<String>> results = runCrossingTransfers(TxOptions.builder().maxAttempts(3).build(), attempts);

        for (Future<String> result : results) {
            assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo("done");
        }
        assertThat(attempts).hasValue(3);
        assertThat(balances()).containsExactly(0, 0);
    }

    @Test
    void deadlockIsThrownWhenRetryIsOff() throws Exception {
        List<Future<String>> results = runCrossingTransfers(TxOptions.defaults(), new AtomicInteger());

        int failures = 0;
        for (Future<String> result : results) {
            try {
                result.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(Deadlock.class);
                failures++;
            }
        }
        assertThat(failures).isEqualTo(1);
    }

    @Test
    void unknownColumnInsideATransactionIsQueryFailure() {
        assertThatThrownBy(() -> db.inTransaction(tx -> tx.query("select 1 as one", Params.empty(),
                row -> row.getString("nope"))))
                .isInstanceOf(QueryFailure.class)
                .hasMessageContaining("pool=tx");
    }

    private static List<Future<String>> runCrossingTransfers(TxOptions options, AtomicInteger attempts)
            throws InterruptedException {
        CyclicBarrier bothLockedFirstRow = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> futures = List.of(
                    pool.submit(() -> transfer(options, attempts, bothLockedFirstRow, 1, 2)),
                    pool.submit(() -> transfer(options, attempts, bothLockedFirstRow, 2, 1)));
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
            return futures;
        } finally {
            pool.shutdownNow();
        }
    }

    private static String transfer(TxOptions options, AtomicInteger attempts, CyclicBarrier barrier, int from,
                                   int to) {
        AtomicInteger myAttempts = new AtomicInteger();
        return db.inTransaction(options, tx -> {
            attempts.incrementAndGet();
            tx.update("update accounts set balance = balance - 5 where id = :id", Params.of("id", from));
            if (myAttempts.incrementAndGet() == 1) {
                await(barrier);
            }
            tx.update("update accounts set balance = balance + 5 where id = :id", Params.of("id", to));
            return "done";
        });
    }

    private static int move(Transaction tx, int from, int to, int amount) {
        tx.update("update accounts set balance = balance - :amount where id = :id", Params.of("amount", amount, "id", from));
        return tx.update("update accounts set balance = balance + :amount where id = :id",
                Params.of("amount", amount, "id", to));
    }

    private static List<Integer> balances() {
        return db.query("select balance from accounts order by id", Params.empty(), row -> row.getInt("balance"));
    }

    private static String show(SqlOperations sql, String setting) {
        return sql.queryOne("show " + setting, Params.empty(), row -> row.require(setting, String.class))
                .orElseThrow();
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
