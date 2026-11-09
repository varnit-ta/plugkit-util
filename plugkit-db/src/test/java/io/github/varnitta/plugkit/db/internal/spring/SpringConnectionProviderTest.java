package io.github.varnitta.plugkit.db.internal.spring;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class SpringConnectionProviderTest {

    @Test
    void driverErrorIsPassedOnInsteadOfSpringsWrapper() {
        SQLException refused = new SQLException("Connection refused", "08001");
        DataSource unreachable = (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getConnection")) {
                        throw refused;
                    }
                    return null;
                });

        assertThatThrownBy(() -> new SpringConnectionProvider(unreachable).acquire()).isSameAs(refused);
    }
}
