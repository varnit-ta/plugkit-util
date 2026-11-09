package io.github.varnitta.plugkit.db.internal.pool;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.varnitta.plugkit.core.exception.InvalidConfigException;
import io.github.varnitta.plugkit.db.Databases;
import io.github.varnitta.plugkit.db.exception.ConnectionLost;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class WrappedDataSourceTest {

    @Test
    void dataSourceThatCannotConnectFailsWithConnectionLost() {
        DataSource unreachable = dataSource(true, () -> {
            throw new SQLException("Connection refused", "08001");
        });

        assertThatThrownBy(() -> Databases.create(unreachable))
                .isInstanceOf(ConnectionLost.class)
                .hasMessageStartingWith("Could not connect through the application's DataSource [pool=datasource]")
                .hasMessageContaining("Connection refused");
    }

    @Test
    void dataSourceForAnUnsupportedDatabaseIsRejected() {
        DataSource mysql = dataSource(false, () -> connectionTo("jdbc:mysql://localhost:3306/app"));

        assertThatThrownBy(() -> Databases.create(mysql))
                .isInstanceOf(InvalidConfigException.class)
                .hasMessageContaining("The application's DataSource connects to a database plugkit-db doesn't support")
                .hasMessageContaining("supported: postgres (jdbc:postgresql:)");
    }

    @FunctionalInterface
    interface ConnectionOpener {
        Connection open() throws SQLException;
    }

    private static DataSource dataSource(boolean wrapperCheckFails, ConnectionOpener opener) {
        return (DataSource) Proxy.newProxyInstance(WrappedDataSourceTest.class.getClassLoader(),
                new Class<?>[] {DataSource.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getConnection" -> opener.open();
                    case "isWrapperFor" -> {
                        if (wrapperCheckFails) {
                            throw new SQLException("not supported");
                        }
                        yield false;
                    }
                    default -> null;
                });
    }

    private static Connection connectionTo(String url) {
        DatabaseMetaData metaData = (DatabaseMetaData) Proxy.newProxyInstance(
                WrappedDataSourceTest.class.getClassLoader(), new Class<?>[] {DatabaseMetaData.class},
                (proxy, method, args) -> method.getName().equals("getURL") ? url : null);
        return (Connection) Proxy.newProxyInstance(WrappedDataSourceTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> method.getName().equals("getMetaData") ? metaData : null);
    }
}
