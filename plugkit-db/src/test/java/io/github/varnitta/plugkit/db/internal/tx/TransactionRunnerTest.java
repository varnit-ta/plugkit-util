package io.github.varnitta.plugkit.db.internal.tx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.varnitta.plugkit.db.config.DatabaseConfig;
import io.github.varnitta.plugkit.db.exception.CommitOutcomeUnknown;
import io.github.varnitta.plugkit.db.exception.SerializationFailure;
import io.github.varnitta.plugkit.db.internal.dialect.postgres.PostgresDialect;
import io.github.varnitta.plugkit.db.internal.statement.StatementExecutor;
import io.github.varnitta.plugkit.db.tx.TxOptions;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TransactionRunnerTest {

    private static final DatabaseConfig CONFIG = DatabaseConfig.builder()
            .url("jdbc:postgresql://localhost/app")
            .poolName("unit")
            .build();

    private final List<String> calls = new ArrayList<>();
    private final Deque<@Nullable SQLException> commitResults = new ArrayDeque<>();
    private final AtomicInteger acquired = new AtomicInteger();

    @Test
    void commitsAndClosesTheConnection() {
        String result = runner().run(TxOptions.defaults(), tx -> "done");

        assertThat(result).isEqualTo("done");
        assertThat(calls).containsExactly("setAutoCommit", "commit", "close");
    }

    @Test
    void connectionLostDuringCommitIsOutcomeUnknownAndNotRetried() {
        commitResults.add(new SQLException("socket closed", "08006"));

        assertThatThrownBy(() -> runner().run(TxOptions.builder().maxAttempts(3).build(), tx -> "done"))
                .isInstanceOf(CommitOutcomeUnknown.class)
                .hasMessage("Connection lost during commit; the transaction may or may not have been applied "
                        + "[pool=unit]")
                .satisfies(e -> assertThat(((CommitOutcomeUnknown) e).isRetryable()).isFalse());
        assertThat(acquired).hasValue(1);
    }

    @Test
    void serializationFailureAtCommitIsRetried() {
        commitResults.add(new SQLException("could not serialize access", "40001"));

        String result = runner().run(TxOptions.builder().maxAttempts(2).build(), tx -> "done");

        assertThat(result).isEqualTo("done");
        assertThat(acquired).hasValue(2);
    }

    @Test
    void serializationFailureIsThrownWhenRetryIsOff() {
        commitResults.add(new SQLException("could not serialize access", "40001"));

        assertThatThrownBy(() -> runner().run(TxOptions.defaults(), tx -> "done"))
                .isInstanceOf(SerializationFailure.class);
        assertThat(acquired).hasValue(1);
    }

    @Test
    void callbackExceptionRollsBackAndIsRethrownUnchanged() {
        IllegalStateException failure = new IllegalStateException("boom");

        assertThatThrownBy(() -> runner().run(TxOptions.builder().maxAttempts(3).build(), tx -> {
            throw failure;
        })).isSameAs(failure);
        assertThat(calls).containsExactly("setAutoCommit", "rollback", "close");
        assertThat(acquired).hasValue(1);
    }

    @Test
    void longerStatementTimeoutRaisesTheNetworkTimeout() {
        runner().run(TxOptions.builder().statementTimeout(Duration.ofMinutes(5)).build(), tx -> "done");

        assertThat(calls).containsExactly("setAutoCommit", "createStatement", "setNetworkTimeout", "commit", "close");
    }

    @Test
    void shorterStatementTimeoutKeepsTheNetworkTimeout() {
        runner().run(TxOptions.builder().statementTimeout(Duration.ofSeconds(5)).build(), tx -> "done");

        assertThat(calls).containsExactly("setAutoCommit", "createStatement", "commit", "close");
    }

    private TransactionRunner runner() {
        return new TransactionRunner(this::fakeConnection, new PostgresDialect(), CONFIG,
                new StatementExecutor("unit", Duration.ofSeconds(1)));
    }

    private Connection fakeConnection() {
        acquired.incrementAndGet();
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (!name.equals("execute")) {
                        calls.add(name);
                    }
                    return switch (name) {
                        case "commit" -> {
                            SQLException failure = commitResults.isEmpty() ? null : commitResults.poll();
                            if (failure != null) {
                                throw failure;
                            }
                            yield null;
                        }
                        case "createStatement" -> fakeStatement();
                        default -> null;
                    };
                });
    }

    private Object fakeStatement() {
        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Statement.class},
                (proxy, method, args) -> method.getName().equals("execute") ? Boolean.FALSE : null);
    }
}
