package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures load-test parameters on a test class or method.
 *
 * <p>Values set here override the {@code loadtest:} block in {@code testfly.yml}.
 * Method-level annotations override class-level annotations. Unset fields
 * (negative integers or empty strings) fall through to the next priority level.
 *
 * <h3>Resolution priority</h3>
 * <ol>
 *   <li>Method {@code @LoadTest}</li>
 *   <li>Class {@code @LoadTest}</li>
 *   <li>{@code testfly.yml → loadtest:}</li>
 *   <li>Hardcoded defaults</li>
 * </ol>
 *
 * <pre>
 * {@literal @}LoadTest(users = 200, rampUp = "30s", hold = "2m")
 * public class ProductLoadTest extends BaseLoadTest {
 *
 *     {@literal @}Test
 *     public void searchUnderLoad() {
 *         load("/api/products?search=laptop")
 *             .assertP95Below(300);
 *     }
 *
 *     {@literal @}Test
 *     {@literal @}LoadTest(users = 500)  // only this method uses 500 users
 *     public void heavySearch() {
 *         load("/api/products?search=phone")
 *             .assertThroughputAbove(1000);
 *     }
 * }
 * </pre>
 */
@TestFlyApi(since = "1.1.0")
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface LoadTest {

    /**
     * Number of concurrent virtual users. {@code -1} means "use config default".
     */
    int users() default -1;

    /**
     * Ramp-up duration (e.g. {@code "30s"}, {@code "2m"}, {@code "1h"}).
     * Empty string means "use config default".
     */
    String rampUp() default "";

    /**
     * Hold duration — how long all users stay active.
     * Empty string means "use config default".
     */
    String hold() default "";

    /**
     * Cooldown duration — gradual user removal after hold.
     * Empty string means "use config default".
     */
    String cooldown() default "";

    /**
     * Engine override: {@code "gatling"}, {@code "jdk"}, or {@code "auto"}.
     * Empty string means "use config default".
     */
    String engine() default "";

    /**
     * Base URL override for this test.
     * Empty string means "use config default".
     */
    String baseUrl() default "";
}
