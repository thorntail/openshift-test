package io.thorntail.openshift.test;

public class OpenShiftTestException extends Exception {
    public OpenShiftTestException(String message) {
        super(message);
    }

    public OpenShiftTestException(String message, Throwable cause) {
        super(message, cause);
    }
}
