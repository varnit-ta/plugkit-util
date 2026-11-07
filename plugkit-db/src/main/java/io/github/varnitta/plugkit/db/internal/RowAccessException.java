package io.github.varnitta.plugkit.db.internal;

import java.sql.SQLException;

/**
 * Carries a {@link java.sql.SQLException} out of a {@link io.github.varnitta.plugkit.db.RowMapper},
 * which can't throw checked exceptions, so it can be translated like any other database error.
 */
final class RowAccessException extends RuntimeException {

    private final SQLException sqlException;

    RowAccessException(SQLException sqlException) {
        super(sqlException);
        this.sqlException = sqlException;
    }

    SQLException sqlException() {
        return sqlException;
    }
}
