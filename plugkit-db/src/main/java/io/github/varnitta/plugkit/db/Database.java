package io.github.varnitta.plugkit.db;

public interface Database extends AutoCloseable {

    DatabaseType type();

    String poolName();

    Health health();

    @Override
    void close();
}
