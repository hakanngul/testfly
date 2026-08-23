package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.client.ApiClient;

/**
 * Shared API client factory — single source of truth for {@code apiClient()}.
 *
 * <p>Implemented by {@code BaseTest} and {@code BaseApiTest} so the delegation
 * to {@link ApiClient} lives in one place.
 */
@TestFlyApi(since = "1.10.0")
public interface ApiSupport {

    /** Returns a new {@link ApiClient} for making HTTP requests. */
    default ApiClient apiClient() {
        return ApiClient.create();
    }
}
