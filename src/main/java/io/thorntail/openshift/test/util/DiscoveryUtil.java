package io.thorntail.openshift.test.util;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

// TODO some code duplication between AllAppsMetadataReader and DiscoveryUtil
public final class DiscoveryUtil {
    // TODO Thorntail-specific
    private static final String HEALTH = "/health";
    private static final String HEALTH_LIVE = "/health/live";
    private static final String HEALTH_READY = "/health/ready";

    private final OpenShiftClient oc;

    public DiscoveryUtil(OpenShiftClient oc) {
        this.oc = oc;
    }

    public Optional<String> getKnownEndpoint(String appName) {
        // exactly one is supposed to exist
        List<? extends HasMetadata> possibleDeployment = Arrays.asList(
                oc.deploymentConfigs().withName(appName).get(),
                oc.apps().deployments().withName(appName).get(),
                oc.extensions().deployments().withName(appName).get()
        );

        for (HasMetadata resource : possibleDeployment) {
            if (resource instanceof io.fabric8.openshift.api.model.DeploymentConfig) {
                PodTemplateSpec podTemplate = ((io.fabric8.openshift.api.model.DeploymentConfig) resource).getSpec().getTemplate();
                return Optional.of(findKnownEndpoint(podTemplate));
            } else if (resource instanceof io.fabric8.kubernetes.api.model.apps.Deployment) {
                PodTemplateSpec podTemplate = ((io.fabric8.kubernetes.api.model.apps.Deployment) resource).getSpec().getTemplate();
                return Optional.of(findKnownEndpoint(podTemplate));
            } else if (resource instanceof io.fabric8.kubernetes.api.model.extensions.Deployment) {
                PodTemplateSpec podTemplate = ((io.fabric8.kubernetes.api.model.extensions.Deployment) resource).getSpec().getTemplate();
                return Optional.of(findKnownEndpoint(podTemplate));
            }
        }

        return Optional.empty();
    }

    private static String findKnownEndpoint(PodTemplateSpec podTemplate) {
        String knownEndpoint = findHttpPathForProbe(podTemplate, Container::getReadinessProbe);
        if (knownEndpoint != null) {
            return HEALTH.equals(knownEndpoint) ? HEALTH_READY : knownEndpoint;
        }

        knownEndpoint = findHttpPathForProbe(podTemplate, Container::getLivenessProbe);
        if (knownEndpoint != null) {
            return HEALTH.equals(knownEndpoint) ? HEALTH_LIVE : knownEndpoint;
        }

        return "/";
    }

    private static String findHttpPathForProbe(PodTemplateSpec podTemplate, Function<Container, Probe> probeExtractor) {
        List<Container> containers = podTemplate.getSpec().getContainers();
        for (Container container : containers) {
            Probe probe = probeExtractor.apply(container);
            if (probe != null
                    && probe.getHttpGet() != null
                    && probe.getHttpGet().getPath() != null) {
                return probe.getHttpGet().getPath();
            }
        }
        return null;
    }


    public Optional<String> getRouteUrl(String routeName) {
        return getRouteUrl(routeName, null);
    }

    public Optional<String> getRouteUrl(String routeName, String namespace) {
        Route route = oc.routes().inNamespace(namespace).withName(routeName).get();
        if (route == null) {
            return Optional.empty();
        }
        return route.getSpec().getTls() != null
                ? Optional.of("https://" + route.getSpec().getHost())
                : Optional.of("http://" + route.getSpec().getHost());
    }
}
