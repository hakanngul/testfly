package io.testfly.sharding;

import io.testfly.api.TestFlyApi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional annotation to provide an estimated execution duration for a test method or class.
 * Used by {@link SmartTestSharder} when historical metrics are not yet available.
 */
@TestFlyApi(since = "1.2.0")
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface TestWeight {

    /**
     * Estimated execution duration in seconds.
     */
    int seconds() default 5;

    /**
     * Optional weight factor multiplier (default: 1.0).
     */
    double multiplier() default 1.0;
}
