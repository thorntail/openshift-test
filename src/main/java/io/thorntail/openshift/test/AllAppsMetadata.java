package io.thorntail.openshift.test;

import java.util.List;

public final class AllAppsMetadata {
    public final List<AppMetadata> all;

    public AllAppsMetadata(List<AppMetadata> all) {
        this.all = all;
    }

    public AppMetadata requireSingle() throws OpenShiftTestException {
        if (all.size() == 1) {
            return all.get(0);
        }

        throw new OpenShiftTestException("Exactly one app required: " + all);
    }
}
