package io.thorntail.openshift.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If the test class is annotated {@code CustomAppMetadata}, then the annotation is used as a source
 * of {@link AppMetadata} for the test and known applications are not read from
 * {@code target/classes/META-INFjkube/openshift.yml}. Note that this annotation is repeatable.
 *
 * @see ManualApplicationDeployment
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(CustomAppMetadataContainer.class)
public @interface CustomAppMetadata {
    /**
     * Name of the application, which is also used as the name of Kubernetes resources.
     *
     * @see AppMetadata#name
     */
    String name();

    /**
     * Known endpoint that can be used to find out if the application is already running.
     * If the application has a readiness or liveness probe, it can be used here.
     *
     * @see AppMetadata#knownEndpoint
     */
    String knownEndpoint();
}
