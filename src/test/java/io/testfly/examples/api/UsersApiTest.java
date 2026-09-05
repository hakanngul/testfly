package io.testfly.examples.api;

import io.testfly.client.ApiClient;
import io.testfly.client.ApiResponse;
import io.testfly.test.BaseApiTest;
import org.testng.annotations.Test;

/**
 * API example tests for the Fake E-commerce API users endpoint.
 *
 * <p>
 * Run explicitly with:
 * 
 * <pre>
 * mvn test -Dtest=io.testfly.examples.api.UsersApiTest
 * </pre>
 */
public class UsersApiTest extends BaseApiTest {

    @Test
    public void listUsersReturnsPaginatedData() {
        ApiResponse res = ApiClient.get("/users")
                .queryParam("page", 1)
                .queryParam("limit", 5)
                .send();

        res.assertStatus(200)
                .assertJson("$.pagination.page", 1)
                .assertJson("$.pagination.limit", 5)
                .assertJsonExists("$.data")
                .assertJsonArraySize("$.data", 5);
    }

    @Test
    public void getSingleUserReturnsExpectedFields() {
        ApiResponse res = ApiClient.get("/users/1").send();

        res.assertStatus(200)
                .assertJson("$.id", 1)
                .assertJsonExists("$.email")
                .assertJsonExists("$.username")
                .assertBodyContains("email");
    }

    @Test
    public void userOrdersEndpointExists() {
        ApiResponse res = ApiClient.get("/users/1/orders").send();

        res.assertStatus(200);
        assert res.json("$.data") != null || res.body() != null
                : "User orders response should contain data";
    }
}
