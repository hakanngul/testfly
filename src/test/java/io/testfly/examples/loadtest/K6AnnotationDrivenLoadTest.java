package io.testfly.examples.loadtest;

import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadTest;
import org.testng.annotations.Test;

import java.time.Duration;

/**
 * Annotation-driven load test — all configuration via {@code @LoadTest},
 * zero fluent overrides in the test body.
 *
 * <p>
 * Demonstrates the "convention over configuration" approach: the annotation
 * carries users, rampUp, hold, and baseUrl; the test body is a single line.
 *
 * <h3>Config resolution</h3>
 * 
 * <pre>
 * Class @LoadTest(baseUrl, users=10, rampUp="5s", hold="10s")
 *   └─ Method @LoadTest(users=25)  ← overrides only users
 *       └─ Effective: baseUrl from class, users=25, rampUp="5s", hold="10s"
 * </pre>
 */
@LoadTest(baseUrl = "https://test.k6.io", users = 10, rampUp = "5s", hold = "10s", cooldown = "3s")
public class K6AnnotationDrivenLoadTest extends BaseLoadTest {

    /**
     * Default annotation config: 10 users, 5s ramp-up, 10s hold.
     */
    @Test(enabled = true) (JDK Engine) is implemented
    public void defaultLoad() {
        load("/")
                .run()
                .assertP95Below(2000)
                .assertErrorRateBelow(0.05);
    }

    /**
     * Method-level override: 25 users instead of 10.
     * All other values (baseUrl, rampUp, hold, cooldown) inherited from class.
     */
    @Test(enabled = true)
    @LoadTest(users = 25)
    public void heavierLoad() {
        load("/")
                .run()
                .assertP95Below(3000)
                .assertErrorRateBelow(0.10);
    }

    /**
     * Method-level override: different hold duration and engine.
     */
    @Test(enabled = true)
    @LoadTest(hold = "20s", engine = "jdk")
    public void longerHoldWithJdkEngine() {
        load("/cart.php")
                .run()
                .assertP95Below(3000)
                .assertNoStatus(500);
    }

    /**
     * Fluent override beats annotation — .users(50) wins over @LoadTest(users=25).
     */
    @Test(enabled = true)
    @LoadTest(users = 25)
    public void fluentOverridesAnnotation() {
        load("/")
                .users(50) // overrides annotation's 25
                .hold(Duration.ofSeconds(5)) // overrides annotation's 10s
                .run()
                .assertThroughputAbove(1);
    }
}
