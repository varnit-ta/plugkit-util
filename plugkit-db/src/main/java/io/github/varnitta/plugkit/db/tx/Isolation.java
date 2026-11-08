package io.github.varnitta.plugkit.db.tx;

import java.sql.Connection;

/**
 * Transaction isolation levels.
 *
 * <p>When a transaction doesn't set one, the database's default applies (PostgreSQL:
 * {@link #READ_COMMITTED}). {@link #SERIALIZABLE} transactions can fail with
 * {@link io.github.varnitta.plugkit.db.exception.SerializationFailure}; combine them with
 * {@code maxAttempts > 1} in {@link TxOptions}.
 */
public enum Isolation {

    READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
    REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),
    SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);

    private final int jdbcLevel;

    Isolation(int jdbcLevel) {
        this.jdbcLevel = jdbcLevel;
    }

    public int jdbcLevel() {
        return jdbcLevel;
    }
}
