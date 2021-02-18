package io.thorntail.openshift.test.util;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.openshift.client.internal.readiness.OpenShiftReadiness;

public final class ReadinessUtil {
    private static final OpenShiftReadinessAccess ACCESS = new OpenShiftReadinessAccess();

    public static boolean isReady(HasMetadata resource) {
        return ACCESS.isReady(resource);
    }

    public static boolean isReadinessApplicable(HasMetadata resource) {
        return ACCESS.isReadinessApplicable(resource);
    }

    // ---

    private static final class OpenShiftReadinessAccess extends OpenShiftReadiness {
        @Override
        public boolean isReadinessApplicable(HasMetadata item) {
            return super.isReadinessApplicable(item);
        }
    }
}
