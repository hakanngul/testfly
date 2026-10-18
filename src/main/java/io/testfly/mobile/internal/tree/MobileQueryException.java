package io.testfly.mobile.internal.tree;

/** Internal strict-resolution failure with content-redacted diagnostics. */
public final class MobileQueryException extends IllegalStateException {

    MobileQueryException(String message) {
        super(message);
    }
}
