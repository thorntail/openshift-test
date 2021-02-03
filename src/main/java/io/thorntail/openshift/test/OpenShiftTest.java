package io.thorntail.openshift.test;

import io.thorntail.openshift.test.injection.TestResource;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the test class as OpenShift test.
 * That is, the JUnit extension for testing JKube-based applications on OpenShift will be applied.
 * <p>
 * For running the tests, you need to be logged into OpenShift ({@code oc login ...}).
 * <p>
 * The {@code oc} binary needs to be present on {@code PATH}, as the test framework uses it.
 * <p>
 * Before running the tests in this class, the test application is deployed. It is expected that the resources
 * to be deployed are found in {@code target/classes/META-INFjkube/openshift.yml}. After the tests in this class
 * finish, the application is undeployed.
 * <p>
 * The {@link ManualApplicationDeployment @ManualApplicationDeployment} and
 * {@link CustomAppMetadata @CustomAppMetadata} annotations can be used to suppress the automatic behavior described in previous paragraph.
 * <p>
 * The {@link CustomizeApplicationDeployment @CustomizeApplicationDeployment} and
 * {@link CustomizeApplicationUndeployment @CustomizeApplicationUndeployment} annotations can be used to run
 * custom code before application deployment and after application undeployment.
 * <p>
 * The {@link TestResource @TestResource} annotation
 * can be used to inject some useful objects. The annotation can be present on a field, or on a test method parameter.
 * <p>
 * The {@link AdditionalResources @AdditionalResources} annotation can be used to deploy additional
 * OpenShift resources before the test application is deployed. These resources are automatically undeployed
 * after the test application is undeployed.
 * <p>
 * The {@link OnlyIfConfigured @OnlyIfConfigured} and {@link OnlyIfNotConfigured @OnlyIfNotConfigured} annotations
 * can be used to selectively enable/disable execution of tests based on a configuration property.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(OpenShiftTestExtension.class)
public @interface OpenShiftTest {
}
