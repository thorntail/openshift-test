package io.thorntail.openshift.test.injection;

import io.thorntail.openshift.test.AllAppsMetadata;
import io.thorntail.openshift.test.AppMetadata;
import io.thorntail.openshift.test.config.Config;
import io.thorntail.openshift.test.util.AwaitUtil;
import io.thorntail.openshift.test.util.OpenShiftUtil;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test instance field or test method parameter for injection.
 * The field/parameter must be of one of these types:
 * <ul>
 *     <li>{@link io.fabric8.openshift.client.OpenShiftClient}</li>
 *     <li>{@link AppMetadata} (if there's exactly one app)</li>
 *     <li>{@link AllAppsMetadata}</li>
 *     <li>{@link Config}</li>
 *     <li>{@link AwaitUtil}</li>
 *     <li>{@link OpenShiftUtil}</li>
 *     <li>{@link java.net.URL} (see also {@link WithName @WithName})</li>
 *     <li>{@link java.net.URI} (see also {@link WithName @WithName})</li>
 * </ul>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface TestResource {
}
