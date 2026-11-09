package io.github.varnitta.plugkit.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.Toxic;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import io.github.varnitta.plugkit.core.Health;
import io.github.varnitta.plugkit.db.config.DatabaseConfig;
import io.github.varnitta.plugkit.db.config.PoolConfig;
import io.github.varnitta.plugkit.db.config.SessionConfig;
import io.github.varnitta.plugkit.db.exception.CommitOutcomeUnknown;
import io.github.varnitta.plugkit.db.exception.ConnectionLost;
import io.github.varnitta.plugkit.db.exception.DatabaseException;
import io.github.varnitta.plugkit.db.sql.Params;
import io.github.varnitta.plugkit.db.tx.Transaction;
import io.github.varnitta.plugkit.db.tx.TxOptions;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.toxiproxy.ToxiproxyContainer;

@Testcontainers(disabledWithoutDocker = true)
class NetworkFailureIT {

    private static final int PROXY_PORT = 8666;

    private static Network network;
    private static PostgreSQLContainer postgres;
    private static ToxiproxyContainer toxiproxy;
    private static Proxy proxy;

    @BeforeAll
    static void start() throws IOException {
        network = Network.newNetwork();
        postgres = new PostgreSQLContainer("postgres:17-alpine").withNetwork(network).withNetworkAliases("postgres");
        toxiproxy = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.12.0").withNetwork(network);
        postgres.start();
        toxiproxy.start();
        proxy = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort())
                .createProxy("postgres", "0.0.0.0:" + PROXY_PORT, "postgres:5432");
        try (Database setup = Databases.create(config("setup").build())) {
            setup.update("create table events (id serial primary key, name text not null)", Params.empty());
        }
    }

    @AfterAll
    static void stop() {
        toxiproxy.stop();
        postgres.stop();
        network.close();
    }

    @BeforeEach
    void healthyNetwork() throws IOException {
        proxy.enable();
        for (Toxic toxic : proxy.toxics().getAll()) {
            toxic.remove();
        }
        try (Database db = Databases.create(config("clean").build())) {
            db.update("truncate events", Params.empty());
        }
    }

    @Test
    void silentNetworkIsCutOffBySocketTimeoutInsteadOfHanging() throws IOException {
        DatabaseConfig config = config("silent")
                .session(SessionConfig.builder().statementTimeout(Duration.ofSeconds(1)).build())
                .pool(PoolConfig.builder().maxSize(1).build())
                .build();

        try (Database db = Databases.create(config)) {
            db.query("select 1 as one", Params.empty(), row -> row.getInt("one"));
            proxy.toxics().timeout("blackhole", ToxicDirection.DOWNSTREAM, 0);

            long start = System.nanoTime();
            assertThatThrownBy(() -> db.query("select 1 as one", Params.empty(), row -> row.getInt("one")))
                    .isInstanceOf(ConnectionLost.class)
                    .hasMessageContaining("pool=silent");
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            assertThat(elapsed).isBetween(Duration.ofSeconds(4), Duration.ofSeconds(12));
        }
    }

    @Test
    void connectionCutMidTransactionIsRetriedOnAFreshConnection() {
        AtomicInteger attempts = new AtomicInteger();

        try (Database db = Databases.create(config("retry").build())) {
            db.inTransaction(TxOptions.builder().maxAttempts(3).build(), tx -> {
                insert(tx, "first");
                if (attempts.incrementAndGet() == 1) {
                    cutConnections();
                }
                return insert(tx, "second");
            });

            assertThat(attempts).hasValue(2);
            assertThat(countEvents(db)).isEqualTo(2);
        }
    }

    @Test
    void connectionLostDuringCommitIsOutcomeUnknownAndNeverRetried() throws IOException {
        AtomicInteger attempts = new AtomicInteger();

        try (Database db = Databases.create(config("commit").build())) {
            assertThatThrownBy(() -> db.inTransaction(TxOptions.builder().maxAttempts(3).build(), tx -> {
                attempts.incrementAndGet();
                insert(tx, "maybe");
                disableProxy();
                return null;
            }))
                    .isInstanceOf(CommitOutcomeUnknown.class)
                    .hasMessageContaining("may or may not have been applied");

            assertThat(attempts).hasValue(1);
            proxy.enable();
            assertThat(countEvents(db)).isZero();
        }
    }

    @Test
    void poolRecoversAfterAnOutage() throws IOException {
        DatabaseConfig config = config("recover")
                .pool(PoolConfig.builder().maxSize(2).acquireTimeout(Duration.ofSeconds(1)).build())
                .build();

        try (Database db = Databases.create(config)) {
            assertThat(db.health().healthy()).isTrue();

            proxy.disable();
            Health down = db.health();
            assertThat(down.healthy()).isFalse();
            assertThat(down.problem()).contains("pool=recover");
            assertThatThrownBy(() -> db.query("select 1", Params.empty(), row -> 1))
                    .isInstanceOf(DatabaseException.class);

            proxy.enable();
            assertThat(db.health().healthy()).isTrue();
            assertThat(db.query("select 1 as one", Params.empty(), row -> row.getInt("one"))).containsExactly(1);
        }
    }

    private static DatabaseConfig.DatabaseConfigBuilder config(String poolName) {
        return DatabaseConfig.builder()
                .url("jdbc:postgresql://" + toxiproxy.getHost() + ":" + toxiproxy.getMappedPort(PROXY_PORT) + "/"
                        + postgres.getDatabaseName())
                .user(postgres.getUsername())
                .password(postgres.getPassword())
                .poolName(poolName)
                .pool(PoolConfig.builder().maxSize(2).build());
    }

    private static int insert(Transaction tx, String name) {
        return tx.update("insert into events (name) values (:name)", Params.of("name", name));
    }

    private static long countEvents(Database db) {
        return db.queryOne("select count(*) as c from events", Params.empty(), row -> row.require("c", Long.class))
                .orElseThrow();
    }

    private static void cutConnections() {
        disableProxy();
        try {
            proxy.enable();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void disableProxy() {
        try {
            proxy.disable();
            Thread.sleep(200);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
