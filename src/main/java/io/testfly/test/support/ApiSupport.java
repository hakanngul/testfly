package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.client.ApiClient;

/**
 * Shared API client factory — single source of truth for {@code apiClient()}.
 *
 * <p>Implemented by {@code BaseTest}, {@code BaseApiTest}, {@code BaseJUnit5Test}
 * and {@code BaseCucumberSteps} so the delegation to {@link ApiClient} lives in one place.
 */
@TestFlyApi(since = "1.10.0")
public interface ApiSupport {

    /** Returns a new {@link ApiClient} for making HTTP requests. */
    default ApiClient apiClient() {
        return ApiClient.create();
    }

    /** Shortcut for {@code ApiClient.get(path)}. */
    default ApiClient apiGet(String path) {
        return ApiClient.get(path);
    }

    /** Shortcut for {@code ApiClient.post(path)}. */
    default ApiClient apiPost(String path) {
        return ApiClient.post(path);
    }

    /** Shortcut for {@code ApiClient.put(path)}. */
    default ApiClient apiPut(String path) {
        return ApiClient.put(path);
    }

    /** Shortcut for {@code ApiClient.patch(path)}. */
    default ApiClient apiPatch(String path) {
        return ApiClient.patch(path);
    }

    /** Shortcut for {@code ApiClient.delete(path)}. */
    default ApiClient apiDelete(String path) {
        return ApiClient.delete(path);
    }
}
