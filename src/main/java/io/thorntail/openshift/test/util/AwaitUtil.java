package io.thorntail.openshift.test.util;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.Handlers;
import io.fabric8.kubernetes.client.ResourceHandler;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.internal.readiness.OpenShiftReadiness;
import io.thorntail.openshift.test.AllAppsMetadata;
import io.thorntail.openshift.test.AppMetadata;
import io.thorntail.openshift.test.DefaultTimeout;
import io.thorntail.openshift.test.OpenShiftTestException;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.fusesource.jansi.Ansi.ansi;

public final class AwaitUtil {
    private final OpenShiftClient oc;
    private final DiscoveryUtil discovery;
    private final AllAppsMetadata apps;

    public AwaitUtil(OpenShiftClient oc, DiscoveryUtil discovery, AllAppsMetadata apps) {
        this.oc = oc;
        this.discovery = discovery;
        this.apps = apps;
    }

    public void awaitImageStream(String imageStream) {
        System.out.println(ansi().a("waiting for image stream ").fgYellow().a(imageStream).reset().a(" to populate"));
        await().atMost(DefaultTimeout.getMinutes(), TimeUnit.MINUTES).until(imageStreamHasTags(oc, imageStream));
    }

    private static Callable<Boolean> imageStreamHasTags(OpenShiftClient oc, String imageStream) {
        return () -> !oc.imageStreams().withName(imageStream).get().getSpec().getTags().isEmpty();
    }

    public void awaitKnownApps() {
        for (AppMetadata app : apps.all) {
            awaitApp(app.name);
        }
    }

    public void awaitApp(String name) {
        awaitReadiness(Arrays.asList(
                oc.deploymentConfigs().withName(name).get(),
                oc.apps().deployments().withName(name).get(),
                oc.extensions().deployments().withName(name).get()
        ));

        Optional<String> baseUrl = discovery.getRouteUrl(name);
        Optional<String> knownEndpoint = discovery.getKnownEndpoint(name);

        if (!baseUrl.isPresent() || !knownEndpoint.isPresent()) {
            return;
        }

        System.out.println(ansi().a("waiting for route ").fgYellow().a(name).reset()
                .a(" to start responding at ").fgYellow().a(knownEndpoint.get()).reset());
        await().ignoreExceptions().atMost(DefaultTimeout.getMinutes(), TimeUnit.MINUTES).untilAsserted(() -> {
            given()
                    .baseUri(baseUrl.get())
                    .basePath(knownEndpoint.get())
            .when()
                    .get()
            .then()
                    .statusCode(200);
        });
    }

    public void awaitReadiness(List<HasMetadata> resources) {
        resources.stream()
                .filter(Objects::nonNull)
                .filter(it -> OpenShiftReadiness.isReadinessApplicable(it.getClass()))
                .forEach(it -> {
                    System.out.println(ansi().a("waiting for ").a(readableKind(it.getKind())).a(" ")
                            .fgYellow().a(it.getMetadata().getName()).reset().a(" to become ready"));
                    await().pollInterval(1, TimeUnit.SECONDS)
                            .atMost(DefaultTimeout.getMinutes(), TimeUnit.MINUTES)
                            .until(() -> {
                                HasMetadata current = oc.resource(it).fromServer().get();
                                if (current == null) {
                                    ResourceHandler<HasMetadata, ?> handler = Handlers.get(it.getKind(), it.getApiVersion());
                                    if (handler != null && !handler.getApiVersion().equals(it.getApiVersion())) {
                                        throw new OpenShiftTestException("Couldn't load " + readableKind(it.getKind()) + " '"
                                                + it.getMetadata().getName() + "' from API server, most likely because"
                                                + " the 'apiVersion' doesn't match: has '" + it.getApiVersion() + "', but"
                                                + " should have '" + handler.getApiVersion() + "'");
                                    }
                                    throw new OpenShiftTestException("Couldn't load " + readableKind(it.getKind()) + " '"
                                            + it.getMetadata().getName() + "' from API server");
                                }
                                return OpenShiftReadiness.isReady(current);
                            });
                });
    }

    // DeploymentConfig -> deployment config
    private static String readableKind(String kind) {
        StringBuilder result = new StringBuilder(kind.length());
        boolean shouldAppendSpaceBeforeUpperCase = false;
        for (int i = 0; i < kind.length(); i++) {
            char c = kind.charAt(i);
            if (Character.isUpperCase(c)) {
                if (shouldAppendSpaceBeforeUpperCase) {
                    result.append(' ');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
            shouldAppendSpaceBeforeUpperCase = true; // only false for the first character
        }
        return result.toString();
    }
}
