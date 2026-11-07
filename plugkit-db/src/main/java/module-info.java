import org.jspecify.annotations.NullMarked;

@NullMarked
module io.github.varnitta.plugkit.db {
    requires java.sql;
    requires com.zaxxer.hikari;
    requires org.slf4j;
    requires static lombok;
    requires static transitive org.jspecify;
    requires static org.postgresql.jdbc;

    exports io.github.varnitta.plugkit.db;
    exports io.github.varnitta.plugkit.db.config;
    exports io.github.varnitta.plugkit.db.exception;
}
