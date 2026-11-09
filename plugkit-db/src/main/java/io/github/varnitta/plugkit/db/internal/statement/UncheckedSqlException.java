package io.github.varnitta.plugkit.db.internal.statement;

import java.sql.SQLException;
import lombok.Getter;

/**
 * Carries a {@link java.sql.SQLException} out of a {@link io.github.varnitta.plugkit.db.sql.RowMapper},
 * which can't throw checked exceptions, so it can be translated like any other database error.
 */
public final class UncheckedSqlException extends RuntimeException {

    @Getter
    private final SQLException sqlException;

    UncheckedSqlException(SQLException sqlException) {
        super(sqlException);
        this.sqlException = sqlException;
    }
}
