package io.testfly.mobile.internal;

import io.testfly.mobile.MobileTest;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Decides whether a test is a native mobile test (ADR 0002 D4): the class (or a superclass, the
 * annotation is inherited) or the method carries {@link MobileTest}, or a Cucumber scenario carries
 * {@code @mobile}. The base classes are annotated, so no JUnit type is ever loaded here.
 */
public final class MobileTestDetector {

    public static final String CUCUMBER_TAG = "@mobile";

    private MobileTestDetector() {
    }

    public static boolean isMobileTest(Class<?> testClass, Method method) {
        return (testClass != null && testClass.isAnnotationPresent(MobileTest.class))
                || (method != null && method.isAnnotationPresent(MobileTest.class));
    }

    public static boolean isMobileScenario(Collection<String> tags) {
        return tags != null && tags.stream().anyMatch(t -> CUCUMBER_TAG.equalsIgnoreCase(t));
    }
}
