package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.api.mock.ApiMockServer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeSuite;

/**
 * Provides API mocking capabilities for test classes using WireMock.
 */
@TestFlyApi(since = "1.2.0")
public interface MockSupport {

    /**
     * Returns the singleton ApiMockServer instance.
     */
    default ApiMockServer mockServer() {
        return ApiMockServer.getInstance();
    }

    /**
     * Convenience method to stub a GET request with a JSON response.
     */
    default void stubGet(String urlPattern, int status, String jsonBody) {
        mockServer().stubGet(urlPattern, status, jsonBody);
    }

    /**
     * Resets all stubs after each test method to ensure test isolation.
     */
    @AfterMethod(alwaysRun = true)
    default void resetMocks() {
        if (ApiMockServer.getInstance() != null) {
            ApiMockServer.getInstance().reset();
        }
    }
}
