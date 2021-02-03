package io.thorntail.openshift.test;

import io.thorntail.openshift.test.config.Config;

final class RetainOnFailure {
    static final String CONFIG_KEY = "ts.retain-on-failure";

    static boolean isEnabled() {
        return Config.get().getAsBoolean(CONFIG_KEY, false);
    }
}
