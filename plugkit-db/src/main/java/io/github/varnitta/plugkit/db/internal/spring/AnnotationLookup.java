package io.github.varnitta.plugkit.db.internal.spring;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * Finds an annotation on the invoked method or, failing that, on its class, and caches a value derived
 * from it per method.
 *
 * @param <A> the annotation type
 * @param <V> the value derived from the annotation
 */
final class AnnotationLookup<A extends Annotation, V> {

    private final Class<A> annotationType;
    private final Function<A, V> settings;
    private final Map<Method, V> cache = new ConcurrentHashMap<>();

    AnnotationLookup(Class<A> annotationType, Function<A, V> settings) {
        this.annotationType = annotationType;
        this.settings = settings;
    }

    @Nullable V find(MethodInvocation invocation) {
        Object target = invocation.getThis();
        Class<?> targetClass = target == null
                ? invocation.getMethod().getDeclaringClass()
                : AopUtils.getTargetClass(target);
        Method method = AopUtils.getMostSpecificMethod(invocation.getMethod(), targetClass);
        V cached = cache.get(method);
        if (cached != null) {
            return cached;
        }
        A annotation = AnnotatedElementUtils.findMergedAnnotation(method, annotationType);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(targetClass, annotationType);
        }
        if (annotation == null) {
            return null;
        }
        V value = settings.apply(annotation);
        cache.put(method, value);
        return value;
    }
}
