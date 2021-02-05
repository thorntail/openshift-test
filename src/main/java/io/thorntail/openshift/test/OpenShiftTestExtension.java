package io.thorntail.openshift.test;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.openshift.client.OpenShiftClient;
import io.restassured.RestAssured;
import io.thorntail.openshift.test.config.Config;
import io.thorntail.openshift.test.injection.InjectionPoint;
import io.thorntail.openshift.test.injection.TestResource;
import io.thorntail.openshift.test.injection.WithName;
import io.thorntail.openshift.test.util.AwaitUtil;
import io.thorntail.openshift.test.util.DiscoveryUtil;
import io.thorntail.openshift.test.util.OpenShiftUtil;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.fusesource.jansi.Ansi.ansi;
import static org.junit.platform.commons.util.AnnotationUtils.findAnnotatedFields;

final class OpenShiftTestExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback,
        TestInstancePostProcessor, ParameterResolver,
        LifecycleMethodExecutionExceptionHandler, TestExecutionExceptionHandler {

    private static Path getResourcesYaml() {
        return Paths.get("target", "classes", "META-INF", "jkube", "openshift.yml");
    }

    private Store getStore(ExtensionContext context) {
        return context.getStore(Namespace.create(getClass()));
    }

    private Optional<ManualApplicationDeployment> getManualDeploymentAnnotation(ExtensionContext context) {
        return context.getElement().map(it -> it.getAnnotation(ManualApplicationDeployment.class));
    }

    private OpenShiftClient getOpenShiftClient(ExtensionContext context) {
        OpenShiftClientResource clientResource = getStore(context).getOrComputeIfAbsent(
                OpenShiftClientResource.class.getName(),
                ignored -> OpenShiftClientResource.createDefault(),
                OpenShiftClientResource.class);
        return clientResource.client;
    }

    private DiscoveryUtil getDiscoveryUtil(ExtensionContext context) {
        OpenShiftClient oc = getOpenShiftClient(context);
        return getStore(context).getOrComputeIfAbsent(DiscoveryUtil.class.getName(),
                ignored -> new DiscoveryUtil(oc),
                DiscoveryUtil.class);
    }

    private AllAppsMetadata getAllAppsMetadata(ExtensionContext context) {
        return getStore(context).getOrComputeIfAbsent(AllAppsMetadata.class.getName(),
                ignored -> {
                    Optional<AnnotatedElement> element = context.getElement();
                    if (element.isPresent()) {
                        AnnotatedElement annotatedElement = element.get();
                        CustomAppMetadata[] annotations = annotatedElement.getAnnotationsByType(CustomAppMetadata.class);
                        if (annotations.length > 0) {
                            List<AppMetadata> result = new ArrayList<>();
                            for (CustomAppMetadata annotation : annotations) {
                                result.add(new AppMetadata(annotation.name(), annotation.knownEndpoint()));
                            }
                            return new AllAppsMetadata(result);
                        }
                    }

                    try {
                        return new AllAppsMetadataReader(getOpenShiftClient(context)).read(getResourcesYaml());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                AllAppsMetadata.class);
    }

    private AwaitUtil getAwaitUtil(ExtensionContext context) {
        OpenShiftClient oc = getOpenShiftClient(context);
        DiscoveryUtil discovery = getDiscoveryUtil(context);
        AllAppsMetadata metadata = getAllAppsMetadata(context);
        return getStore(context).getOrComputeIfAbsent(AwaitUtil.class.getName(),
                ignored -> new AwaitUtil(oc, discovery, metadata),
                AwaitUtil.class);
    }

    private OpenShiftUtil getOpenShiftUtil(ExtensionContext context) {
        OpenShiftClient oc = getOpenShiftClient(context);
        AwaitUtil await = getAwaitUtil(context);
        return getStore(context).getOrComputeIfAbsent(OpenShiftUtil.class.getName(),
                ignogred -> new OpenShiftUtil(oc, await),
                OpenShiftUtil.class);
    }

    private void initTestsStatus(ExtensionContext context) {
        getStore(context).put(TestsStatus.class.getName(), new TestsStatus());
    }

    private TestsStatus getTestsStatus(ExtensionContext context) {
        TestsStatus testsStatus = getStore(context).get(TestsStatus.class.getName(), TestsStatus.class);
        if (testsStatus == null) {
            throw new IllegalStateException("missing " + TestsStatus.class.getSimpleName() + ", this is test framework bug");
        }
        return testsStatus;
    }

    // ---

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        initTestsStatus(context);

        try {
            doBeforeAll(context);
        } catch (Exception e) {
            getTestsStatus(context).failed = true;
            throw e;
        }
    }

    private void doBeforeAll(ExtensionContext context) throws Exception {
        System.out.println("---------- OpenShiftTest set up ----------");

        createEphemeralNamespaceIfNecessary(context);

        deployAdditionalResources(context);

        runPublicStaticVoidMethods(CustomizeApplicationDeployment.class, context);

        if (!getManualDeploymentAnnotation(context).isPresent()) {
            Path yaml = getResourcesYaml();
            if (!Files.exists(yaml)) {
                throw new OpenShiftTestException("Missing " + yaml);
            }

            ImageOverrides.apply(yaml, getOpenShiftClient(context));

            System.out.println("deploying application");
            new Command("oc", "apply", "-f", yaml.toString()).runAndWait();

/*
            awaitImageStreams(context, yaml);

            Optional<String> binary = findNativeBinary();
            if (binary.isPresent()) {
                new Command("oc", "start-build", getAppMetadata(context).name, "--from-file=" + binary.get(), "--follow")
                        .runAndWait();
            } else {
                // when generating Kubernetes resources, Quarkus expects that all application files
                // will reside in `/deployments/target`, but if we did just `oc start-build my-app --from-dir=target`,
                // the files would end up in `/deployments`
                // using `tar` works around that nicely, because the tarball created with this command will have
                // a single root directory named `target`, which the S2I builder image will unpack to `/deployments`
                new Command("tar", "czf", "app.tar.gz", "target").runAndWait();
                new Command("oc", "start-build", getAppMetadata(context).name, "--from-archive=app.tar.gz", "--follow")
                        .runAndWait();
                new Command("rm", "app.tar.gz").runAndWait();
            }
*/
        }

        setUpRestAssured(context);

        getAwaitUtil(context).awaitKnownApps();
    }

    private void createEphemeralNamespaceIfNecessary(ExtensionContext context) throws IOException, InterruptedException {
        if (EphemeralNamespace.isEnabled()) {
            StringBuilder currentNamespace = new StringBuilder();
            new Command("oc", "project", "--short").outputToString(currentNamespace).runAndWait();
            getStore(context).put(PreviousNamespace.class.getName(), new PreviousNamespace(currentNamespace.toString()));

            EphemeralNamespace namespace = EphemeralNamespace.newWithRandomName();
            getStore(context).put(EphemeralNamespace.class.getName(), namespace);

            System.out.println(ansi().a("using ephemeral namespace ").fgYellow().a(namespace.name).reset());
            new Command("oc", "new-project", namespace.name).runAndWait();

            // image streams pointing to the original namespace where builds were performed
            try (Stream<Path> imagestreams = Files.find(Paths.get("target"), 1,
                    (path, ignored) -> path.getFileName().toString().endsWith("-is.yml"))) {
                List<Path> yamls = imagestreams.collect(Collectors.toList());
                for (Path yaml : yamls) {
                    new Command("oc", "apply", "-f", yaml.toString()).runAndWait();
                }
            }
        }
    }

    private void deployAdditionalResources(ExtensionContext context) throws IOException, InterruptedException {
        Optional<AnnotatedElement> element = context.getElement();
        if (element.isPresent()) {
            TestsStatus testsStatus = getTestsStatus(context);
            OpenShiftClient oc = getOpenShiftClient(context);
            AwaitUtil awaitUtil = getAwaitUtil(context);

            AnnotatedElement annotatedElement = element.get();
            AdditionalResources[] annotations = annotatedElement.getAnnotationsByType(AdditionalResources.class);
            for (AdditionalResources additionalResources : annotations) {
                AdditionalResourcesDeployed deployed = AdditionalResourcesDeployed.deploy(additionalResources,
                        testsStatus, oc, awaitUtil);

                if (EphemeralNamespace.isDisabled()) {
                    // when using ephemeral namespaces, we don't delete additional resources because:
                    // - when an ephemeral namespace is dropped, everything is destroyed anyway
                    // - when retain on failure is enabled and failure occurs,
                    //   everything in the ephemeral namespace must be kept intact
                    getStore(context).put(new Object(), deployed);
                }
            }
        }
    }

/*
    private void awaitImageStreams(ExtensionContext context, Path openshiftResources) throws IOException {
        OpenShiftClient oc = getOpenShiftClient(context);
        AppMetadata metadata = getAppMetadata(context);
        AwaitUtil awaitUtil = getAwaitUtil(context);

        oc.load(Files.newInputStream(openshiftResources))
                .get()
                .stream()
                .flatMap(it -> it instanceof ImageStream ? Stream.of(it) : Stream.empty())
                .map(it -> it.getMetadata().getName())
                .filter(it -> !it.equals(metadata.name))
                .forEach(awaitUtil::awaitImageStream);
    }
*/

    private void setUpRestAssured(ExtensionContext context) throws OpenShiftTestException {
        AllAppsMetadata apps = getAllAppsMetadata(context);
        if (apps.all.size() != 1) {
            return;
        }

        AppMetadata app = apps.all.get(0);

        DiscoveryUtil discovery = getDiscoveryUtil(context);

        Optional<String> url = discovery.getRouteUrl(app.name);
        if (!url.isPresent()) {
            return;
        }

        RestAssured.baseURI = url.get();
        if (url.get().startsWith("https://")) {
            RestAssured.useRelaxedHTTPSValidation();
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        boolean testsFailed = getTestsStatus(context).failed;

        if (testsFailed) {
            System.out.println("---------- OpenShiftTest failure ----------");
            System.out.println(ansi().a("test ").fgYellow().a(context.getDisplayName()).reset()
                    .a(" failed, gathering debug data"));

            new Command("oc", "status", "--suggest").runAndWait();

            OpenShiftUtil openShiftUtil = getOpenShiftUtil(context);
            Path logDirectory = Paths.get("target", "logs", openShiftUtil.getNamespace());
            Files.createDirectories(logDirectory);
            for (Pod pod : openShiftUtil.getPods()) {
                String podName = pod.getMetadata().getName();
                new Command("oc", "logs", podName)
                        .outputToFile(logDirectory.resolve(podName + ".log"))
                        .runAndWait();
            }
        }

        System.out.println("---------- OpenShiftTest tear down ----------");

        boolean shouldUndeployApplication = true;
        if (EphemeralNamespace.isEnabled()) {
            // when using ephemeral namespaces, we don't undeploy the application because:
            // - when an ephemeral namespace is dropped, everything is destroyed anyway
            // - when retain on failure is enabled and failure occurs,
            //   everything in the ephemeral namespace must be kept intact
            shouldUndeployApplication = false;
        }
        if (RetainOnFailure.isEnabled() && testsFailed) {
            shouldUndeployApplication = false;

            if (EphemeralNamespace.isDisabled()) {
                // when using ephemeral namespaces, a different message will be printed in dropEphemeralNamespaceIfNecessary
                System.out.println(ansi().a("test ").fgYellow().a(context.getDisplayName()).reset()
                        .a(" failed, not deleting any resources"));
            }
        }
        if (getManualDeploymentAnnotation(context).isPresent()) {
            shouldUndeployApplication = false;
        }

        if (shouldUndeployApplication) {
            System.out.println("undeploying application");
            new Command("oc", "delete", "-f", getResourcesYaml().toString(), "--ignore-not-found").runAndWait();
        }

        // TODO not yet clear if this method should be invoked (or not) in presence of ephemeral namespaces,
        //  test failures, or retain on failure
        runPublicStaticVoidMethods(CustomizeApplicationUndeployment.class, context);

        dropEphemeralNamespaceIfNecessary(context);
    }

    private void dropEphemeralNamespaceIfNecessary(ExtensionContext context) throws IOException, InterruptedException {
        PreviousNamespace previousNamespace = getStore(context).get(PreviousNamespace.class.getName(), PreviousNamespace.class);
        if (previousNamespace != null) {
            new Command("oc", "project", previousNamespace.name).runAndWait();
        }

        EphemeralNamespace ephemeralNamespace = getStore(context).get(EphemeralNamespace.class.getName(), EphemeralNamespace.class);
        TestsStatus status = getTestsStatus(context);
        if (ephemeralNamespace != null) {
            if (RetainOnFailure.isEnabled() && status.failed) {
                System.out.println(ansi().a("test ").fgYellow().a(context.getDisplayName()).reset()
                        .a(" failed, keeping ephemeral namespace ").fgYellow().a(ephemeralNamespace.name).reset()
                        .a(" intact"));
            } else {
                System.out.println(ansi().a("dropping ephemeral namespace ").fgYellow().a(ephemeralNamespace.name).reset());
                new Command("oc", "delete", "project", ephemeralNamespace.name).runAndWait();
            }
        }
    }

    private void runPublicStaticVoidMethods(Class<? extends Annotation> annotation, ExtensionContext context) throws Exception {
        for (Method method : context.getRequiredTestClass().getMethods()) {
            if (method.getAnnotation(annotation) != null) {
                if (!isPublicStaticVoid(method)) {
                    throw new OpenShiftTestException("@" + annotation.getSimpleName()
                            + " method " + method.getDeclaringClass().getSimpleName() + "." + method.getName()
                            + " must be public static void");
                }

                Parameter[] parameters = method.getParameters();
                Object[] arguments = new Object[parameters.length];
                for (int i = 0; i < parameters.length; i++) {
                    Parameter parameter = parameters[i];
                    InjectionPoint injectionPoint = InjectionPoint.forParameter(parameter);
                    arguments[i] = valueFor(injectionPoint, context);
                }

                method.invoke(null, arguments);
            }
        }
    }

    private static boolean isPublicStaticVoid(Method method) {
        return Modifier.isPublic(method.getModifiers())
                && Modifier.isStatic(method.getModifiers())
                && Void.TYPE.equals(method.getReturnType());
    }

    // ---

    @Override
    public void beforeEach(ExtensionContext context) {
        System.out.println(ansi().a("---------- running test ")
                .fgYellow().a(context.getParent().map(ctx -> ctx.getDisplayName() + ".").orElse(""))
                .a(context.getDisplayName()).reset().a(" ----------"));
    }

    // ---

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
        injectDependencies(testInstance, context);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) throws ParameterResolutionException {
        return parameter.isAnnotated(TestResource.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) throws ParameterResolutionException {
        InjectionPoint injectionPoint = InjectionPoint.forParameter(parameter.getParameter());
        try {
            return valueFor(injectionPoint, context);
        } catch (OpenShiftTestException e) {
            throw new ParameterResolutionException(e.getMessage(), e);
        }
    }

    private void injectDependencies(Object instance, ExtensionContext context) throws Exception {
        for (Field field : findAnnotatedFields(instance.getClass(), TestResource.class, ignored -> true)) {
            InjectionPoint injectionPoint = InjectionPoint.forField(field);
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            field.set(instance, valueFor(injectionPoint, context));
        }
    }

    private Object valueFor(InjectionPoint injectionPoint, ExtensionContext context) throws OpenShiftTestException {
        if (OpenShiftClient.class.equals(injectionPoint.type())) {
            return getOpenShiftClient(context);
        } else if (AppMetadata.class.equals(injectionPoint.type())) {
            return getAllAppsMetadata(context).requireSingle();
        } else if (AllAppsMetadata.class.equals(injectionPoint.type())) {
            return getAllAppsMetadata(context);
        } else if (AwaitUtil.class.equals(injectionPoint.type())) {
            return getAwaitUtil(context);
        } else if (OpenShiftUtil.class.equals(injectionPoint.type())) {
            return getOpenShiftUtil(context);
        } else if (Config.class.equals(injectionPoint.type())) {
            return Config.get();
        } else if (URL.class.equals(injectionPoint.type())) {
            return getURL(injectionPoint, context);
        } else if (URI.class.equals(injectionPoint.type())) {
            return getURI(injectionPoint, context);
        } else {
            throw new OpenShiftTestException("Unsupported type " + injectionPoint.type().getSimpleName()
                    + " for @TestResource " + injectionPoint.description());
        }
    }

    private URL getURL(InjectionPoint injectionPoint, ExtensionContext context) throws OpenShiftTestException {
        try {
            return new URL(getRouteAddress(injectionPoint, context));
        } catch (MalformedURLException e) {
            throw new OpenShiftTestException("Couldn't construct URL for " + injectionPoint.type().getSimpleName()
                    + " for @TestResource " + injectionPoint.description());
        }
    }

    private URI getURI(InjectionPoint injectionPoint, ExtensionContext context) throws OpenShiftTestException {
        try {
            return new URI(getRouteAddress(injectionPoint, context));
        } catch (URISyntaxException e) {
            throw new OpenShiftTestException("Couldn't construct URI for " + injectionPoint.type().getSimpleName()
                    + " for @TestResource " + injectionPoint.description());
        }
    }

    private String getRouteAddress(InjectionPoint injectionPoint, ExtensionContext context) throws OpenShiftTestException {
        String routeName = injectionPoint.isAnnotationPresent(WithName.class)
                ? injectionPoint.getAnnotation(WithName.class).value()
                : getAllAppsMetadata(context).requireSingle().name;

        String namespace = injectionPoint.isAnnotationPresent(WithName.class)
                ? injectionPoint.getAnnotation(WithName.class).inNamespace()
                : null;
        if (WithName.CURRENT_NAMESPACE.equals(namespace)) {
            namespace = null;
        }

        DiscoveryUtil discovery = getDiscoveryUtil(context);
        return discovery.getRouteUrl(routeName, namespace)
                .orElseThrow(() -> new OpenShiftTestException("Missing route " + routeName));
    }

    // ---

    @Override
    public void handleBeforeAllMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        failureOccured(context);
        throw throwable;
    }

    @Override
    public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        failureOccured(context);
        throw throwable;
    }

    @Override
    public void handleAfterEachMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        failureOccured(context);
        throw throwable;
    }

    @Override
    public void handleAfterAllMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        failureOccured(context);
        throw throwable;
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        failureOccured(context);
        throw throwable;
    }

    private void failureOccured(ExtensionContext context) {
        getTestsStatus(context).failed = true;
    }
}
