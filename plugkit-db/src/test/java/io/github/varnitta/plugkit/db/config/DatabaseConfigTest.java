package io.github.varnitta.plugkit.db.config;

import static io.github.varnitta.plugkit.db.config.ConfigProblems.configProblemsThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.varnitta.plugkit.db.DatabaseType;
import io.github.varnitta.plugkit.core.exception.InvalidConfigException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DatabaseConfigTest {

    private static final String URL = "jdbc:postgresql://localhost:5432/app";

    @Test
    void minimalConfigUsesDefaults() {
        DatabaseConfig config = DatabaseConfig.builder().url(URL).build();

        assertThat(config.url()).isEqualTo(URL);
        assertThat(config.user()).isNull();
        assertThat(config.password()).isNull();
        assertThat(config.poolName()).isEqualTo("plugkit-db");
        assertThat(config.pool()).isEqualTo(PoolConfig.defaults());
        assertThat(config.session()).isEqualTo(SessionConfig.defaults());
        assertThat(config.tx()).isEqualTo(TxConfig.defaults());
        assertThat(config.slowQueryThreshold()).isEqualTo(Duration.ofSeconds(1));
        assertThat(config.driverProperties()).isEmpty();
    }

    @Test
    void sessionAndTxDefaultsMatchTheDesign() {
        assertThat(SessionConfig.defaults()).isEqualTo(
                new SessionConfig(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ofSeconds(60)));
        assertThat(TxConfig.defaults()).isEqualTo(new TxConfig(Duration.ofSeconds(60)));
    }

    @Test
    void canonicalConstructorRequiresEveryComponent() {
        assertThatThrownBy(() -> new DatabaseConfig(DatabaseType.POSTGRES, URL, null, null, "p", null,
                SessionConfig.defaults(), TxConfig.defaults(), Duration.ofSeconds(1), Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("pool");
    }

    @Test
    void canonicalConstructorValidatesTypeAgainstUrl() {
        assertThat(configProblemsThrownBy(() -> new DatabaseConfig(DatabaseType.POSTGRES, "jdbc:mysql://localhost/app", null,
                null, "p", PoolConfig.defaults(), SessionConfig.defaults(), TxConfig.defaults(),
                Duration.ofSeconds(1), Map.of())))
                .containsExactly("databaseType is 'postgres' but url does not start with jdbc:postgresql:");
    }

    @Test
    void typeIsInferredFromUrl() {
        assertThat(DatabaseConfig.builder().url(URL).build().databaseType()).isEqualTo(DatabaseType.POSTGRES);
    }

    @Test
    void typeIsInferredThroughWrapperDrivers() {
        DatabaseConfig config = DatabaseConfig.builder()
                .url("jdbc:aws-wrapper:postgresql://orders.cluster-abc.us-east-1.rds.amazonaws.com:5432/app")
                .build();

        assertThat(config.databaseType()).isEqualTo(DatabaseType.POSTGRES);
    }

    @Test
    void explicitTypeMatchingUrlIsAccepted() {
        assertThat(DatabaseConfig.builder().databaseType(DatabaseType.POSTGRES).url(URL).build().databaseType())
                .isEqualTo(DatabaseType.POSTGRES);
    }

    @Test
    void explicitTypeMustMatchUrl() {
        assertThat(configProblemsThrownBy(() -> DatabaseConfig.builder()
                .databaseType(DatabaseType.POSTGRES)
                .url("jdbc:mysql://localhost:3306/app")
                .build()))
                .containsExactly("databaseType is 'postgres' but url does not start with jdbc:postgresql:");
    }

    @Test
    void unsupportedDatabaseIsRejected() {
        assertThat(configProblemsThrownBy(() -> DatabaseConfig.builder().url("jdbc:sqlserver://localhost;databaseName=app").build()))
                .containsExactly("url is not for a supported database; supported: postgres (jdbc:postgresql:), mysql (jdbc:mysql:)");
    }

    @Test
    void nestedSettingsCanBeCustomized() {
        DatabaseConfig config = DatabaseConfig.builder()
                .url(URL)
                .user("app")
                .password("secret")
                .pool(PoolConfig.builder().maxSize(20).acquireTimeout(Duration.ofSeconds(2)).build())
                .session(SessionConfig.builder().statementTimeout(Duration.ofMinutes(5)).build())
                .tx(TxConfig.builder().defaultTimeout(Duration.ofMinutes(10)).build())
                .driverProperty("sslmode", "require")
                .build();

        assertThat(config.pool().maxSize()).isEqualTo(20);
        assertThat(config.pool().minIdle()).isEqualTo(20);
        assertThat(config.pool().acquireTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(config.session().statementTimeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(config.session().lockTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.tx().defaultTimeout()).isEqualTo(Duration.ofMinutes(10));
        assertThat(config.driverProperties()).containsEntry("sslmode", "require");
    }

    @Test
    void urlIsRequired() {
        assertThat(configProblemsThrownBy(() -> DatabaseConfig.builder().build())).containsExactly("url is required");
    }

    @Test
    void urlMustBeJdbc() {
        assertThatThrownBy(() -> DatabaseConfig.builder().url("postgresql://localhost/app").build())
                .isInstanceOf(InvalidConfigException.class)
                .hasMessageContaining("url must start with 'jdbc:'");
    }

    @Test
    void reportsAllTopLevelProblemsTogether() {
        assertThat(configProblemsThrownBy(() -> DatabaseConfig.builder()
                .poolName(" ")
                .slowQueryThreshold(Duration.ZERO)
                .build()))
                .containsExactly(
                        "url is required",
                        "poolName must not be blank",
                        "slowQueryThreshold must be at least 1ms, was 0ms");
    }

    @Test
    void sessionAndTxValidateTheirOwnSettings() {
        assertThatThrownBy(() -> SessionConfig.builder().lockTimeout(Duration.ZERO).build())
                .hasMessageContaining("session.lockTimeout must be at least 1ms, was 0ms");
        assertThatThrownBy(() -> TxConfig.builder().defaultTimeout(Duration.ofSeconds(-1)).build())
                .hasMessageContaining("tx.defaultTimeout must be at least 1ms, was -1s");
    }

    @Test
    void errorMessageListsEachProblemOnItsOwnLine() {
        assertThatThrownBy(() -> DatabaseConfig.builder().poolName("").build())
                .hasMessage(String.join(System.lineSeparator(),
                        "Invalid plugkit configuration:",
                        "  - url is required",
                        "  - poolName must not be blank"));
    }

    @Test
    void nullDriverPropertyValuesAreRejected() {
        Map<String, String> properties = new HashMap<>();
        properties.put("sslmode", null);

        assertThatThrownBy(() -> DatabaseConfig.builder().url(URL).driverProperties(properties).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("driverProperties.sslmode");
    }

    @Test
    void driverPropertiesAreImmutableAndKeepOrder() {
        DatabaseConfig config = DatabaseConfig.builder()
                .url(URL)
                .driverProperty("b", "2")
                .driverProperty("a", "1")
                .build();

        assertThat(config.driverProperties().keySet()).containsExactly("b", "a");
        assertThatThrownBy(() -> config.driverProperties().put("c", "3"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toStringMasksSecrets() {
        DatabaseConfig config = DatabaseConfig.builder()
                .url("jdbc:postgresql://localhost:5432/app?user=app&password=hunter2&sslmode=require")
                .user("app")
                .password("hunter2")
                .driverProperty("sslmode", "require")
                .driverProperty("sslPassword", "hunter2")
                .driverProperty("accessToken", "hunter2")
                .driverProperty("clientSecret", "hunter2")
                .build();

        String text = config.toString();

        assertThat(text).doesNotContain("hunter2");
        assertThat(text).contains("password=****&sslmode=require");
        assertThat(text).contains("password=****,");
        assertThat(text).contains("sslmode=require, sslPassword=****, accessToken=****, clientSecret=****");
        assertThat(text).contains("user=app");
    }

    @Test
    void toStringMasksCredentialsInUrlUserInfo() {
        DatabaseConfig config = DatabaseConfig.builder().url("jdbc:postgresql://app:hunter2@localhost:5432/app").build();

        assertThat(config.toString())
                .doesNotContain("hunter2")
                .contains("jdbc:postgresql://app:****@localhost:5432/app");
    }

    @Test
    void toStringShowsNullPasswordAsNull() {
        assertThat(DatabaseConfig.builder().url(URL).build().toString()).contains("password=null");
    }
}
