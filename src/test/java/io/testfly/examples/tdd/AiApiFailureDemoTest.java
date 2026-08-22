package io.testfly.examples.tdd;

import io.testfly.client.ApiResponse;
import io.testfly.test.BaseApiTest;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Intentionally failing API tests that trigger AI Failure Analysis on HTTP/response issues.
 *
 * <p>Each test demonstrates a different category of API failure — wrong status code,
 * missing response fields, and incorrect data types.
 *
 * <p>Run with:
 * <pre>mvn test -Pexamples -Dtest=io.testfly.examples.tdd.AiApiFailureDemoTest</pre>
 */
public class AiApiFailureDemoTest extends BaseApiTest {

    /**
     * Wrong HTTP status — the endpoint returns 200, not 201.
     * AI should detect the incorrect status expectation and suggest the correct one.
     */
    @Test
    public void listProductsShouldReturn201Created() {
        ApiResponse res = apiClient().get("/products").send();
        res.assertStatus(201);
    }

    /**
     * Wrong JSON field name — the response uses {@code "data"}, not {@code "results"}.
     * AI should compare the expected vs actual response structure.
     */
    @Test
    public void productsResponseShouldContainResultsArray() {
        ApiResponse res = apiClient().get("/products?limit=3").send();
        res.assertStatus(200);

        assertNotNull(res.json("$.results"),
                "Response should contain a 'results' array");
    }

    /**
     * Wrong data type — the product price is a number, not a string.
     * AI should flag the type mismatch in the assertion.
     */
    @Test
    public void productPriceShouldBeString() {
        ApiResponse res = apiClient().get("/products/1").send();
        res.assertStatus(200);

        Object price = res.json("$.price");
        assertTrue(price instanceof String,
                "Product price should be a string value");
    }

    /**
     * Non-existent endpoint — returns 404, but the test expects 200.
     * AI should suggest verifying the API route against the documentation.
     */
    @Test
    public void adminEndpointShouldBeAccessible() {
        ApiResponse res = apiClient().get("/admin/dashboard").send();
        res.assertStatus(200);
    }

    /**
     * Wrong pagination value — requests page 999 of a small dataset.
     * AI should detect that the page is out of range and the response is empty.
     */
    @Test
    public void page999ShouldReturnProducts() {
        ApiResponse res = apiClient().get("/products?page=999&limit=5").send();
        res.assertStatus(200);

        assertNotNull(res.json("$.data[0]"),
                "Page 999 should still return product data");
    }
}
