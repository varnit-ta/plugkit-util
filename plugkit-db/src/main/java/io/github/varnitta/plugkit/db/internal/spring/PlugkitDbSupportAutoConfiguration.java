package io.github.varnitta.plugkit.db.internal.spring;

import io.github.varnitta.plugkit.db.Database;
import io.github.varnitta.plugkit.db.spring.RetryOnTransient;
import io.github.varnitta.plugkit.db.spring.Timeouts;
import java.lang.annotation.Annotation;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;

/**
 * Extras that need a {@link Database} bean: the {@link RetryOnTransient} and {@link Timeouts}
 * annotations, and a health indicator when Spring Boot's health support is present.
 *
 * <p>The retry advice is ordered outside Spring's transaction advice, so each attempt runs in a fresh
 * transaction. An auto-proxy creator with class proxying is registered if the application has none.
 */
@AutoConfiguration(after = {PlugkitDbAutoConfiguration.class, PlugkitDbDataSourceAutoConfiguration.class})
@ConditionalOnBean(Database.class)
@Import(ProxyCreatorRegistrar.class)
public class PlugkitDbSupportAutoConfiguration {

    static final int RETRY_ORDER = Ordered.LOWEST_PRECEDENCE - 100;
    static final int TIMEOUTS_ORDER = RETRY_ORDER + 1;

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor plugkitRetryOnTransientAdvisor() {
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(annotated(RetryOnTransient.class),
                new RetryOnTransientInterceptor());
        advisor.setOrder(RETRY_ORDER);
        return advisor;
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor plugkitTimeoutsAdvisor() {
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(annotated(Timeouts.class),
                new TimeoutsInterceptor());
        advisor.setOrder(TIMEOUTS_ORDER);
        return advisor;
    }

    private static Pointcut annotated(Class<? extends Annotation> annotation) {
        return new ComposablePointcut(AnnotationMatchingPointcut.forMethodAnnotation(annotation))
                .union(new AnnotationMatchingPointcut(annotation, true));
    }

    /**
     * Exposes {@link Database#health()} to Spring Boot's health endpoint as {@code plugkitDb}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    static class HealthConfiguration {

        @Bean
        @ConditionalOnMissingBean(name = "plugkitDbHealthIndicator")
        HealthIndicator plugkitDbHealthIndicator(Database database) {
            return new DatabaseHealthIndicator(database);
        }
    }
}
