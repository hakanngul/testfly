package io.testfly.examples.loadtest;

import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadTest;

import java.time.Duration;

import org.testng.annotations.Test;

/**
 * Simple load test against https://test.k6.io — the public k6 demo site.
 *
 * <p>
 * Demonstrates high-concurrency Gatling load testing with zero boilerplate:
 * <ul>
 * <li>Single GET endpoint with class-level {@link LoadTest} configuration</li>
 * <li>Method-level configuration override</li>
 * <li>Multi-step user browsing flow with multiple endpoints</li>
 * <li>Functional API smoke test coexisting in the same test class</li>
 * </ul>
 */
@LoadTest(baseUrl = "https://test.k6.io", users = 5, rampUp = "2s", hold = "5s")
public class K6SimpleLoadTest extends BaseLoadTest {

    /**
     * Loads the homepage under traffic using Gatling.
     * Asserts the site responds quickly and without errors.
     */
    @Test
    public void homepageUnderLoad() {
        load("/")
                .run()
                .assertP95Below(2000) // p95 < 2s
                .assertErrorRateBelow(0.05) // < 5% errors
                .assertNoStatus(500); // no server errors
    }

    /**
     * Loads the contacts page.
     * Uses method-level annotation to override user count and duration.
     */
    @Test
    @LoadTest(users = 8, rampUp = "2s", hold = "5s")
    public void contactsPageUnderLoad() {
        load("/contacts.php")
                .run()
                .assertP95Below(3000)
                .assertErrorRateBelow(0.10);
    }

    /**
     * Multi-step browsing scenario: user hits homepage, then reads the news page.
     */
    @Test
    @LoadTest(users = 5, rampUp = "2s", hold = "5s")
    public void multiStepBrowsingFlow() {
        loadScenario("Browse Flow")
                .step("Home").get("/")
                .and()
                .step("News").get("/news.php")
                .and()
                .run()
                .assertP95Below(3000)
                .assertErrorRateBelow(0.10);
    }

    /**
     * Functional smoke test coexisting with load tests in the same class.
     * Uses the standard apiClient() — single request, no concurrency.
     */
    @Test
    public void functionalSmokeTest() {
        apiClient()
                .to("https://test.k6.io")
                .get("/")
                .send()
                .assertStatus(200);
    }

    @Test
    public void assertionsWork() {
        load("/")
                .users(10)
                .hold(Duration.ofSeconds(5))
                .run()
                .assertThroughputAbove(50) // RPS > 50
                .assertP95Below(2000) // p95 < 2000ms (public demo site)
                .assertErrorRateBelow(0.05) // Hata oranı < %5
                .assertNoStatus(500); // 500 server error olmamalı
    }

}
