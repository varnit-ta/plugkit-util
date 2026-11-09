package io.github.varnitta.plugkit.db.spring;

import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.db.config.DatabaseConfig;
import io.github.varnitta.plugkit.db.config.PoolConfig;
import io.github.varnitta.plugkit.db.config.SessionConfig;
import io.github.varnitta.plugkit.db.config.TxConfig;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Spring Boot properties under {@code plugkit.db}, mapped one-to-one onto {@link DatabaseConfig}.
 *
 * <pre>
 * plugkit:
 *   db:
 *     url: jdbc:postgresql://localhost:5432/app
 *     user: app
 *     password: ${DB_PASSWORD}
 *     pool:
 *       max-size: 20
 *     session:
 *       statement-timeout: 10s
 *     driver-properties:
 *       sslmode: require
 * </pre>
 *
 * <p>Unset values use the {@link DatabaseConfig} defaults. When {@code url} is set, plugkit-db builds
 * its own pool and exposes it as the application's {@code DataSource}; without it, plugkit-db uses the
 * application's existing {@code DataSource}.
 *
 * @param databaseType       which database the URL points to; inferred from {@code url} when not set
 * @param url                the JDBC URL
 * @param user               the database user
 * @param password           the database password
 * @param poolName           the name used in logs, metrics and error messages
 * @param pool               connection pool settings
 * @param session            timeouts the database server enforces
 * @param tx                 transaction settings
 * @param slowQueryThreshold statements slower than this are logged as warnings
 * @param driverProperties   extra properties passed to the JDBC driver as-is
 */
@ConfigurationProperties("plugkit.db")
public record PlugkitDbProperties(
        @Nullable DatabaseType databaseType,
        @Nullable String url,
        @Nullable String user,
        @Nullable String password,
        @Nullable String poolName,
        @DefaultValue Pool pool,
        @DefaultValue Session session,
        @DefaultValue Tx tx,
        @Nullable Duration slowQueryThreshold,
        @Nullable Map<String, String> driverProperties) {

    /**
     * Connection pool settings; see {@link PoolConfig}.
     *
     * @param maxSize        the most connections the pool opens
     * @param minIdle        idle connections kept open
     * @param acquireTimeout how long a caller waits for a free connection
     * @param maxLifetime    connections are replaced after this age
     * @param idleTimeout    idle connections above {@code minIdle} are closed after this
     * @param keepalive      how often idle connections are pinged
     * @param leakDetection   a connection held longer than this is logged as a possible leak
     * @param shutdownTimeout how long closing waits for running work before closing the pool
     */
    public record Pool(
            @Nullable Integer maxSize,
            @Nullable Integer minIdle,
            @Nullable Duration acquireTimeout,
            @Nullable Duration maxLifetime,
            @Nullable Duration idleTimeout,
            @Nullable Duration keepalive,
            @Nullable Duration leakDetection,
            @Nullable Duration shutdownTimeout) {
    }

    /**
     * Server-side timeouts; see {@link SessionConfig}.
     *
     * @param statementTimeout         the longest a single statement may run
     * @param lockTimeout              the longest a statement waits for a lock
     * @param idleInTransactionTimeout the longest a session may sit idle inside an open transaction
     */
    public record Session(
            @Nullable Duration statementTimeout,
            @Nullable Duration lockTimeout,
            @Nullable Duration idleInTransactionTimeout) {
    }

    /**
     * Transaction settings; see {@link TxConfig}.
     *
     * @param defaultTimeout the longest a transaction may run unless it sets its own timeout
     */
    public record Tx(@Nullable Duration defaultTimeout) {
    }

    public DatabaseConfig toDatabaseConfig() {
        return DatabaseConfig.builder()
                .databaseType(databaseType)
                .url(url)
                .user(user)
                .password(password)
                .poolName(poolName)
                .pool(PoolConfig.builder()
                        .maxSize(pool.maxSize())
                        .minIdle(pool.minIdle())
                        .acquireTimeout(pool.acquireTimeout())
                        .maxLifetime(pool.maxLifetime())
                        .idleTimeout(pool.idleTimeout())
                        .keepalive(pool.keepalive())
                        .leakDetection(pool.leakDetection())
                        .shutdownTimeout(pool.shutdownTimeout())
                        .build())
                .session(SessionConfig.builder()
                        .statementTimeout(session.statementTimeout())
                        .lockTimeout(session.lockTimeout())
                        .idleInTransactionTimeout(session.idleInTransactionTimeout())
                        .build())
                .tx(TxConfig.builder().defaultTimeout(tx.defaultTimeout()).build())
                .slowQueryThreshold(slowQueryThreshold)
                .driverProperties(driverProperties == null ? Map.of() : driverProperties)
                .build();
    }

    @Override
    public String toString() {
        return "PlugkitDbProperties[databaseType=" + databaseType + ", url=" + url + ", user=" + user
                + ", password=" + (password == null ? null : "****") + ", poolName=" + poolName
                + ", pool=" + pool + ", session=" + session + ", tx=" + tx
                + ", slowQueryThreshold=" + slowQueryThreshold
                + ", driverProperties=" + (driverProperties == null ? null : driverProperties.keySet()) + "]";
    }
}
