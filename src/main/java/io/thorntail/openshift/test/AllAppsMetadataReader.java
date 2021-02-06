package io.thorntail.openshift.test;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.openshift.client.OpenShiftClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

// TODO some code duplication between AllAppsMetadataReader and DiscoveryUtil
final class AllAppsMetadataReader {
    // TODO Thorntail-specific
    private static final String HEALTH = "/health";
    private static final String HEALTH_LIVE = "/health/live";
    private static final String HEALTH_READY = "/health/ready";

    private final OpenShiftClient oc;

    AllAppsMetadataReader(OpenShiftClient oc) {
        this.oc = oc;
    }

    AllAppsMetadata read(Path yaml) throws IOException {
        List<HasMetadata> resources = oc.load(Files.newInputStream(yaml)).get();

        List<AppMetadata> result = new ArrayList<>();
        for (HasMetadata resource : resources) {
            String name = resource.getMetadata().getName();
            if (resource instanceof io.fabric8.openshift.api.model.DeploymentConfig) {
                PodTemplateSpec podTemplate = ((io.fabric8.openshift.api.model.DeploymentConfig) resource).getSpec().getTemplate();
                result.add(build(name, podTemplate));
            } else if (resource instanceof io.fabric8.kubernetes.api.model.apps.Deployment) {
                PodTemplateSpec podTemplate = ((io.fabric8.kubernetes.api.model.apps.Deployment) resource).getSpec().getTemplate();
                result.add(build(name, podTemplate));
            } else if (resource instanceof io.fabric8.kubernetes.api.model.extensions.Deployment) {
                PodTemplateSpec podTemplate = ((io.fabric8.kubernetes.api.model.extensions.Deployment) resource).getSpec().getTemplate();
                result.add(build(name, podTemplate));
            }
        }

        return new AllAppsMetadata(result);
    }

    private AppMetadata build(String name, PodTemplateSpec podTemplate) {
        String knownEndpoint = findKnownEndpoint(podTemplate);

        return new AppMetadata(name, knownEndpoint);
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
}
