package io.thorntail.openshift.test;

public final class AppMetadata {
    /**
     * Name of the application, which is also used as the name of Kubernetes resources.
     */
    public final String name;

    /**
     * URL path to some known endpoint that the application exposes.
     * If the application provides a readiness probe, its path.
     * Otherwise, if the application provides a liveness probe, its path.
     * Otherwise, {@code /}.
     */
    public final String knownEndpoint;

    public AppMetadata(String name, String knownEndpoint) {
        this.name = name;
        this.knownEndpoint = knownEndpoint;
    }
}
