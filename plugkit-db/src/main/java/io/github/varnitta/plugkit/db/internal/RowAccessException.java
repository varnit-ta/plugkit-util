package io.github.varnitta.plugkit.db.internal;

import java.sql.SQLException;

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
