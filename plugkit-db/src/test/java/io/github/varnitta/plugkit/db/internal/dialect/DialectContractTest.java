package io.github.varnitta.plugkit.db.internal.dialect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.varnitta.plugkit.core.exception.InvalidConfigException;
import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.db.config.SessionConfig;
import io.github.varnitta.plugkit.db.exception.ConnectionLost;
import io.github.varnitta.plugkit.db.exception.DatabaseException;
import io.github.varnitta.plugkit.db.exception.QueryFailure;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Behaviour every {@link Dialect} must have. Each dialect's test extends this class.
 */
public abstract class DialectContractTest {

    protected abstract Dialect dialect();

    @Test
    protected void isTheDialectRegisteredForItsType() {
        assertThat(Dialects.forType(dialect().databaseType())).isSameAs(Dialects.forType(dialect().databaseType()));
        assertThat(Dialects.forType(dialect().databaseType()).getClass()).isEqualTo(dialect().getClass());
    }

    @Test
    protected void namesItsDriver() {
        assertThat(dialect().driverClassName()).isNotBlank();
        assertThat(dialect().driverArtifact()).contains(":");
    }

    @Test
    protected void connectionPropertiesSetTheConnectAndSocketTimeouts() {
        assertThat(dialect().connectionProperties(SessionConfig.defaults(), Map.of()))
                .containsKeys("connectTimeout", "socketTimeout");
    }

    @Test
    protected void managedDriverPropertiesCannotBeSetByTheUser() {
        assertThatThrownBy(() -> dialect().connectionProperties(SessionConfig.defaults(),
                Map.of("socketTimeout", "1", "connectTimeout", "1")))
                .isInstanceOfSatisfying(InvalidConfigException.class, e -> assertThat(e.problems()).hasSize(2));
    }

    @Test
    protected void userDriverPropertiesArePassedThrough() {
        assertThat(dialect().connectionProperties(SessionConfig.defaults(), Map.of("ssl", "true")))
                .containsEntry("ssl", "true");
    }

    @Test
    protected void readsTheServerConnectionLimit() {
        assertThat(dialect().maxConnectionsQuery()).isNotBlank();
    }

    @Test
    protected void overridesTheStatementTimeoutForATransaction() {
        assertThat(dialect().statementTimeoutOverrideSql(Duration.ofMinutes(5))).isNotEmpty()
                .allSatisfy(sql -> assertThat(sql).contains("300000"));
        assertThat(dialect().statementTimeoutRestoreSql(SessionConfig.defaults())).isNotNull();
    }

    @Test
    protected void singleQuotesAlwaysQuoteText() {
        assertThat(dialect().sqlSyntax().quoteCharacters()).contains('\'');
    }

    @Test
    protected void reportsUnsupportedSettingsAsAList() {
        assertThat(dialect().unsupportedSettings(SessionConfig.defaults())).isNotNull();
    }

    @Test
    protected void unknownErrorsBecomeQueryFailure() {
        DatabaseException translated = dialect().translate(new SQLException("boom", "42000", 99999), "contract");

        assertThat(translated).isExactlyInstanceOf(QueryFailure.class).hasMessageContaining("pool=contract");
        assertThat(translated.isRetryable()).isFalse();
    }

    @Test
    protected void connectionErrorsAreRetryable() {
        DatabaseException translated = dialect().translate(new SQLException("link failure", "08S01"), "contract");

        assertThat(translated).isInstanceOf(ConnectionLost.class);
        assertThat(translated.isRetryable()).isTrue();
    }

    @Test
    protected void keepsTheOriginalErrorAsTheCause() {
        SQLException original = new SQLException("boom", "42000");

        assertThat(dialect().translate(original, "contract")).hasCause(original);
    }

    @Test
    protected void hasADatabaseType() {
        assertThat(dialect().databaseType()).isIn((Object[]) DatabaseType.values());
    }
}
