package io.thorntail.openshift.test;

import io.thorntail.openshift.test.config.Config;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.AnnotatedElement;
import java.util.Optional;

import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;

public class OnlyIfNotConfiguredCondition implements ExecutionCondition {
    private static final ConditionEvaluationResult ENABLED_BY_DEFAULT = ConditionEvaluationResult.enabled(
            "@OnlyIfNotConfigured is not present");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Optional<AnnotatedElement> element = context.getElement();
        Optional<OnlyIfNotConfigured> annotation = findAnnotation(element, OnlyIfNotConfigured.class);
        if (annotation.isPresent()) {
            String key = annotation.get().value();
            return Config.get().getAsBoolean(key, false)
                    ? ConditionEvaluationResult.disabled("Configuration property " + key + " is enabled")
                    : ConditionEvaluationResult.enabled("Configuration property " + key + " is disabled");
        }
        return ENABLED_BY_DEFAULT;
    }
}