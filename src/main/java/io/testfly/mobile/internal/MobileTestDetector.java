package io.testfly.mobile.internal;

import io.testfly.mobile.MobileException;
import io.testfly.mobile.MobilePlatform;
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

    /**
     * The platform {@code @MobileTest(platform = …)} asks for, the method's before the class's;
     * {@code null} when neither sets one.
     */
    public static MobilePlatform platform(Class<?> testClass, Method method) {
        for (MobileTest t : new MobileTest[] {method != null ? method.getAnnotation(MobileTest.class) : null,
                testClass != null ? testClass.getAnnotation(MobileTest.class) : null}) {
            if (t != null && t.platform().length > 0) {
                if (t.platform().length > 1) {
                    throw new MobileException("@MobileTest(platform = ...) takes one platform, got "
                            + java.util.Arrays.toString(t.platform()) + "; write one test per platform");
                }
                return t.platform()[0];
            }
        }
        return null;
    }

    public static boolean isMobileScenario(Collection<String> tags) {
        return tags != null && tags.stream().anyMatch(t -> CUCUMBER_TAG.equalsIgnoreCase(t));
    }
}
