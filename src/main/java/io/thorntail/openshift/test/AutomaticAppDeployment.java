package io.thorntail.openshift.test;

import io.thorntail.openshift.test.config.Config;

final class AutomaticAppDeployment {
    static final String CONFIG_KEY = "ts.skip-deployment";

    static boolean isEnabled() {
        return !isDisabled();
    }

    static boolean isDisabled() {
        return Config.get().getAsBoolean(CONFIG_KEY, false);
    }
}
