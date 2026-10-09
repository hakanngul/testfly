package io.testfly.exceptions;

import io.testfly.api.TestFlyApi;

/**
 * Thrown when a network-mocking operation fails in a way the test author should
 * see — for example, a response body that cannot be serialized to JSON, or a
 * {@code fetchOriginal()} call whose underlying CDP request could not be read.
 *
 * <p>This is a {@link RuntimeException}; it never leaves an intercepted request
 * hanging — the framework always terminates the request (continue/passthrough)
 * before surfacing this exception.
 */
@TestFlyApi(since = "1.0.2")
public class NetworkMockException extends RuntimeException {
    public NetworkMockException(String message) { super(message); }
    public NetworkMockException(String message, Throwable cause) { super(message, cause); }
}
