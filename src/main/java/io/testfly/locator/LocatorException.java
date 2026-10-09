package io.testfly.locator;

import io.testfly.api.TestFlyApi;

/**
 * Thrown when a {@link Locator} chain cannot resolve to a matching element.
 */
@TestFlyApi(since = "0.6.0")
public class LocatorException extends RuntimeException {

    public LocatorException(String message) {
        super(message);
    }

    public LocatorException(String message, Throwable cause) {
        super(message, cause);
    }
}
