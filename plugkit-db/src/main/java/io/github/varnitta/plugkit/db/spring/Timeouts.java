package io.github.varnitta.plugkit.db.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides plugkit-db's timeouts for the database work done while the annotated method runs.
 *
 * <p>Use it for the few methods that legitimately need longer, such as reports, instead of loosening
 * the global defaults. Values use Spring Boot's duration format, for example {@code 500ms},
 * {@code 30s} or {@code 5m}; an empty value keeps the configured default.
 *
 * <pre>{@code
 * @Timeouts(statement = "5m")
 * @Transactional(readOnly = true)
 * public Report monthlyReport(YearMonth month) { ... }
 * }</pre>
 *
 * <p>The statement timeout applies inside the current transaction and also raises the connection's
 * network timeout. Statements outside a transaction run in their own short transaction so the limit can
 * be applied. On a class, it applies to every public method.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Timeouts {

    /**
     * The longest a single statement may run.
     */
    String statement() default "";

    /**
     * The longest a transaction run with {@code Database.inTransaction} may take.
     */
    String transaction() default "";
}
