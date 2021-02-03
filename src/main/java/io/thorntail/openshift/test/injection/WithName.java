package io.thorntail.openshift.test.injection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows customization of injected test instance field or test method parameter, e.g. route name.
 * Works in conjunction with {@link TestResource}.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface WithName {
    String value() default "";
}
