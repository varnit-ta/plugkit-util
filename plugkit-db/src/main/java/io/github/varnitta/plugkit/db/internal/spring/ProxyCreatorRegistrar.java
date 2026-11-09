package io.github.varnitta.plugkit.db.internal.spring;

import org.springframework.aop.config.AopConfigUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Makes sure an auto-proxy creator exists, using class proxying, so the {@code @RetryOnTransient} and
 * {@code @Timeouts} advisors apply to classes without interfaces.
 */
final class ProxyCreatorRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry);
        AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
    }
}
