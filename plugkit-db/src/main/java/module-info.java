import org.jspecify.annotations.NullMarked;

/**
 * Relational database access with safe defaults.
 *
 * <p>Give plugkit-db a JDBC URL and credentials through
 * {@link io.github.varnitta.plugkit.db.config.DatabaseConfig}, then create a
 * {@link io.github.varnitta.plugkit.db.Database} with {@link io.github.varnitta.plugkit.db.Databases}.
 * The module builds and manages a connection pool, applies timeouts at every layer and translates
 * vendor errors into {@link io.github.varnitta.plugkit.db.exception.DatabaseException} types that are
 * the same for every database.
 *
 * <p>The JDBC driver for the database is not included; add it to the application. Micrometer and
 * Spring Boot support activate only when those libraries are present.
 */
@NullMarked
module io.github.varnitta.plugkit.db {
    requires transitive io.github.varnitta.plugkit.core;
    requires java.sql;
    requires com.zaxxer.hikari;
    requires org.slf4j;
    requires static lombok;
    requires static transitive org.jspecify;
    requires static org.postgresql.jdbc;
    requires static micrometer.core;
    requires static spring.aop;
    requires static spring.beans;
    requires static spring.boot;
    requires static spring.boot.autoconfigure;
    requires static spring.boot.health;
    requires static spring.context;
    requires static spring.core;
    requires static spring.jdbc;
    requires static spring.tx;

    exports io.github.varnitta.plugkit.db;
    exports io.github.varnitta.plugkit.db.config;
    exports io.github.varnitta.plugkit.db.exception;
    exports io.github.varnitta.plugkit.db.metrics;
    exports io.github.varnitta.plugkit.db.spring;
    exports io.github.varnitta.plugkit.db.sql;
    exports io.github.varnitta.plugkit.db.tx;

    opens io.github.varnitta.plugkit.db.spring;
    opens io.github.varnitta.plugkit.db.internal.spring;
}
