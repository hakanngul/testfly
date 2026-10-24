package io.testfly.mobile;

/** A mobile {@code expect(...)} assertion that did not pass within its budget. */
public class MobileAssertionError extends AssertionError {

    public MobileAssertionError(String message) {
        super(message);
    }
}
