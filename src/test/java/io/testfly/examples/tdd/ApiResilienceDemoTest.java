package io.testfly.examples.tdd;

import io.testfly.client.ApiResponse;
import io.testfly.test.BaseApiTest;
import org.testng.annotations.Test;

/**
 * API resilience demo — tests that demonstrate how the framework handles
 * API failures gracefully with AI-powered failure analysis.
 *
 * <p>Unlike web UI tests, API tests don't have DOM locators so self-healing
 * doesn't apply. Instead, TestFly provides resilience through:
 * <ul>
 *   <li>{@code @Retryable} — automatic retry on transient failures</li>
 *   <li>AI failure analysis — DeepSeek/Claude analyzes why the API call failed</li>
 *   <li>Soft assertions — collect multiple failures without stopping</li>
 * </ul>
 *
 * <p>Run with:
 * <pre>mvn test -Pexamples -Dtest=io.testfly.examples.tdd.ApiResilienceDemoTest</pre>
 */
public class ApiResilienceDemoTest extends BaseApiTest {

    /**
     * Intentional failure — wrong status code expectation.
     * AI will analyze the response and suggest the correct status.
     */
    @Test
    public void listProductsShouldReturn201() {
        ApiResponse res = apiClient().get("/products").send();

        // Bug: GET endpoints return 200, not 201
        res.assertStatus(201);
    }

    /**
     * Demonstrates soft assertion — multiple failures collected without stopping.
     * All assertion failures are reported together at the end.
     */
    @Test
    public void productShouldHaveMultipleFields() {
        ApiResponse res = apiClient().get("/products/1").send();
        res.assertStatus(200);

        // These soft assertions all run even if earlier ones fail
        softAssert().that(
                "wrong-brand".equals(res.json("$.brand")),
                "Brand should be 'wrong-brand' but was: " + res.json("$.brand"));

        int stock = Integer.parseInt(res.json("$.stock"));
        softAssert().that(
                stock > 1000,
                "Stock should be over 1000 but was: " + stock);
    }

    /**
     * Intentional failure — non-existent endpoint returns 404.
     * AI will suggest verifying the API route against the documentation.
     */
    @Test
    public void searchProductsEndpointShouldExist() {
        ApiResponse res = apiClient().get("/products/search?q=phone").send();

        // Bug: fakeapi.net doesn't have a /products/search endpoint
        res.assertStatus(200);
        res.assertJsonExists("$.data[0]");
    }
}
