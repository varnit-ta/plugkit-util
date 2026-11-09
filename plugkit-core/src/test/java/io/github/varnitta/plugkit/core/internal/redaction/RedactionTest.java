package io.github.varnitta.plugkit.core.internal.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RedactionTest {

    @ParameterizedTest
    @ValueSource(strings = {"password", "sslPassword", "PASSWORD", "clientSecret", "accessToken", "token"})
    void recognisesSecretKeys(String key) {
        assertThat(Redaction.isSecretKey(key)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sslmode", "user", "poolName", "options"})
    void leavesOrdinaryKeysAlone(String key) {
        assertThat(Redaction.isSecretKey(key)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "jdbc:postgresql://h/db?user=app&password=hunter2&sslmode=require | jdbc:postgresql://h/db?user=app&password=****&sslmode=require",
            "jdbc:sqlserver://h;user=app;password=hunter2;db=x | jdbc:sqlserver://h;user=app;password=****;db=x",
            "jdbc:mysql://app:hunter2@localhost:3306/app | jdbc:mysql://app:****@localhost:3306/app",
            "redis://localhost:6379?token=abc | redis://localhost:6379?token=****",
            "jdbc:postgresql://localhost:5432/app | jdbc:postgresql://localhost:5432/app"
    })
    void redactsSecretsInUrls(String url, String expected) {
        assertThat(Redaction.url(url)).isEqualTo(expected);
    }

    @Test
    void redactsSecretPropertyValuesAndKeepsOrder() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("sslmode", "require");
        properties.put("sslPassword", "hunter2");

        assertThat(Redaction.properties(properties)).isEqualTo("{sslmode=require, sslPassword=****}");
    }

    @Test
    void redactsASecretValueButKeepsNull() {
        assertThat(Redaction.secret("hunter2")).isEqualTo(Redaction.REDACTED);
        assertThat(Redaction.secret(null)).isNull();
    }
}
