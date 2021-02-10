package io.thorntail.openshift.test;

import io.thorntail.openshift.test.config.Config;

public class DefaultTimeout {
    static final String CONFIG_KEY = "ts.default-timeout";

    public static int getMinutes() {
        return Config.get().getAsInt(CONFIG_KEY, 5);
    }
}
