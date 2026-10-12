package io.testfly.client;

import io.testfly.api.TestFlyApi;

/**
 * Common view of an API failure, whether it was raised as an {@link ApiException}
 * (request or assertion failure) or as an {@link ApiAssertionError} (schema or
 * contract validation failure).
 *
 * <pre>
 * try {
 *     res.assertSchema("schemas/user.json");
 * } catch (AssertionError e) {
 *     if (e instanceof ApiFailure failure) log(failure.getStatus(), failure.getUrl());
 *     throw e;
 * }
 * </pre>
 */
@TestFlyApi(since = "1.1.0")
public interface ApiFailure {

    /** HTTP status of the response, or {@code -1} when no response was received. */
    int getStatus();

    /** Raw response body, or {@code null} when no response was received. */
    String getBody();

    /** Request URL, or {@code null} when unknown. */
    String getUrl();

    /** HTTP method, or {@code null} when unknown. */
    String getMethod();
}
