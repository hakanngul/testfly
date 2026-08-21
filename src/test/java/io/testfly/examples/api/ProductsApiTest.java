package io.testfly.examples.api;

import io.testfly.client.ApiResponse;
import io.testfly.test.BaseApiTest;
import org.testng.annotations.Test;

/**
 * API example tests for the Fake E-commerce API products endpoint.
 *
 * <p>Run explicitly with:
 * <pre>mvn test -Dtest=io.testfly.examples.api.ProductsApiTest</pre>
 */
public class ProductsApiTest extends BaseApiTest {

    @Test
    public void listProductsReturnsPaginatedData() {
        ApiResponse res = apiClient().get("/products?page=1&limit=5").send();

        res.assertStatus(200)
           .assertJson("$.pagination.page", 1)
           .assertJson("$.pagination.limit", 5);

        assert res.json("$.data[0].id") != null : "First product should have an id";
    }

    @Test
    public void getSingleProductReturnsExpectedFields() {
        ApiResponse res = apiClient().get("/products/1").send();

        res.assertStatus(200)
           .assertJson("$.id", 1)
           .assertBodyContains("title")
           .assertBodyContains("price");
    }

    @Test
    public void filterProductsByCategory() {
        ApiResponse res = apiClient().get("/products?category=electronics&limit=1").send();

        res.assertStatus(200);
        assert "electronics".equals(res.json("$.data[0].category"))
                : "Returned product should belong to electronics category";
    }

    @Test
    public void getProductCategoriesReturnsArray() {
        ApiResponse res = apiClient().get("/products/categories").send();

        res.assertStatus(200).assertBodyContains("electronics");
    }
}
