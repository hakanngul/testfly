package io.testfly.examples.api;

import io.testfly.client.ApiResponse;
import io.testfly.test.BaseApiTest;
import org.testng.annotations.Test;

/**
 * API example tests for the Fake E-commerce API users endpoint.
 *
 * <p>Run explicitly with:
 * <pre>mvn test -Dtest=io.testfly.examples.api.UsersApiTest</pre>
 */
public class UsersApiTest extends BaseApiTest {

    @Test
    public void listUsersReturnsPaginatedData() {
        ApiResponse res = apiClient().get("/users?page=1&limit=5").send();

        res.assertStatus(200)
           .assertJson("$.pagination.page", 1)
           .assertJson("$.pagination.limit", 5);

        assert res.json("$.data[0].id") != null : "First user should have an id";
    }

    @Test
    public void getSingleUserReturnsExpectedFields() {
        ApiResponse res = apiClient().get("/users/1").send();

        res.assertStatus(200)
           .assertJson("$.id", 1)
           .assertBodyContains("email")
           .assertBodyContains("username");
    }

    @Test
    public void userOrdersEndpointExists() {
        ApiResponse res = apiClient().get("/users/1/orders").send();

        res.assertStatus(200);
        assert res.json("$.data") != null || res.body() != null
                : "User orders response should contain data";
    }
}
