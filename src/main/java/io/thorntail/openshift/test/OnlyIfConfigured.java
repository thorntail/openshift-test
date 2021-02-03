package io.thorntail.openshift.test;

import io.thorntail.openshift.test.config.Config;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code @OnlyIfConfigured} annotation can be used to selectively enable or disable certain tests
 * based on a configuration property (using the built-in {@linkplain Config configuration system}).
 * If the annotation is present, the test is only executed if the specified configuration key evaluates
 * to {@code true}.
 *
 * @see #value()
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(OnlyIfConfiguredCondition.class)
public @interface OnlyIfConfigured {
    /**
     * Configuration key that must be enabled (per {@link Config#getAsBoolean(String, boolean) Config.getAsBoolean})
     * for the annotated test to be executed.
     */
    String value();
}
