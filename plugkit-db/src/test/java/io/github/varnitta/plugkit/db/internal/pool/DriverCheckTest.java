package io.github.varnitta.plugkit.db.internal.pool;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.db.config.SessionConfig;
import io.github.varnitta.plugkit.db.exception.DatabaseException;
import io.github.varnitta.plugkit.db.exception.InvalidConfigException;
import io.github.varnitta.plugkit.db.internal.dialect.Dialect;
import io.github.varnitta.plugkit.db.internal.dialect.postgres.PostgresDialect;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DriverCheckTest {

    @Test
    void passesWhenDriverIsOnClasspath() {
        assertThatCode(() -> DriverCheck.requireDriver(new PostgresDialect())).doesNotThrowAnyException();
    }

    @Test
    void failsWithTheDependencyToAdd() {
        assertThatThrownBy(() -> DriverCheck.requireDriver(new MissingDriverDialect()))
                .isInstanceOf(InvalidConfigException.class)
                .hasMessageContaining("type 'postgres' needs the JDBC driver com.example.MissingDriver: "
                        + "add the dependency com.example:missing-driver");
    }

    private static final class MissingDriverDialect implements Dialect {

        @Override
        public DatabaseType type() {
            return DatabaseType.POSTGRES;
        }

        @Override
        public String driverClassName() {
            return "com.example.MissingDriver";
        }

        @Override
        public String driverArtifact() {
            return "com.example:missing-driver";
        }

        @Override
        public Map<String, String> connectionProperties(SessionConfig session, Map<String, String> userProperties) {
            return Map.of();
        }

        @Override
        public String maxConnectionsQuery() {
            return "";
        }

        @Override
        public String localStatementTimeoutSql(Duration statementTimeout) {
            return "";
        }

        @Override
        public DatabaseException translate(SQLException exception, String poolName) {
            throw new UnsupportedOperationException();
        }
    }
}
