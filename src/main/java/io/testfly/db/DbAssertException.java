package io.testfly.db;

import io.testfly.api.TestFlyApi;

/**
 * Thrown when a database assertion fails (e.g. row not found, wrong value).
 * Extends {@link AssertionError} so test frameworks treat it as a test failure.
 */
@TestFlyApi(since = "0.11.0")
public class DbAssertException extends AssertionError {

    public DbAssertException(String message) {
        super(message);
    }

    public DbAssertException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
