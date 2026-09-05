package io.testfly.exceptions;

/**
 * Thrown when a network-mocking operation fails in a way the test author should
 * see — for example, a response body that cannot be serialized to JSON, or a
 * {@code fetchOriginal()} call whose underlying CDP request could not be read.
 *
 * <p>This is a {@link RuntimeException}; it never leaves an intercepted request
 * hanging — the framework always terminates the request (continue/passthrough)
 * before surfacing this exception.
 */
public class NetworkMockException extends RuntimeException {
    public NetworkMockException(String message) { super(message); }
    public NetworkMockException(String message, Throwable cause) { super(message, cause); }
}
