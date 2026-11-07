package io.github.varnitta.plugkit.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DatabaseTypeTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:5432/app",
            "jdbc:postgresql:app",
            "jdbc:PostgreSQL://localhost/app",
            "jdbc:aws-wrapper:postgresql://cluster.rds.amazonaws.com:5432/app",
            "jdbc:otel:postgresql://localhost:5432/app",
            "jdbc:postgresql:///app?cloudSqlInstance=project:region:instance&socketFactory=x"
    })
    void detectsPostgres(String url) {
        assertThat(DatabaseType.fromUrl(url)).contains(DatabaseType.POSTGRES);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "postgresql://localhost/app",
            "jdbc:mysql://localhost:3306/app",
            "jdbc:sqlserver://localhost;databaseName=app",
            "jdbc:mysql://localhost/postgresql"
    })
    void rejectsOtherUrls(String url) {
        assertThat(DatabaseType.fromUrl(url)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = "postgres")
    void configNameIsLowercaseName(String id) {
        assertThat(DatabaseType.POSTGRES.configName()).isEqualTo(id);
    }
}
