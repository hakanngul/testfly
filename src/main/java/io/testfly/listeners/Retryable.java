package io.testfly.listeners;

import io.testfly.api.TestFlyApi;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test method as eligible for retry on failure.
 *
 * Retry count is controlled by {@code retry.maxAttempts} in testfly.yml.
 * Retry can be globally disabled via {@code retry.enabled: false} regardless
 * of this annotation being present.
 *
 * Usage:
 * <pre>
 *   {@literal @}Retryable
 *   {@literal @}Test
 *   public void flakyTest() { ... }
 * </pre>
 */
@TestFlyApi(since = "0.5.0")
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Retryable {

    /**
     * Maximum number of retry attempts after the first failure.
     * {@code -1} (default) falls back to {@code retry.maxAttempts} in
     * {@code testfly.yml}. Positive values override the config.
     *
     * <p>Total runs = {@code maxAttempts + 1}.
     */
    int maxAttempts() default -1;
}