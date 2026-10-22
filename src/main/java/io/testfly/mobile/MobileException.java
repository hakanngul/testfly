package io.testfly.mobile;

/**
 * Base of every native mobile failure (incubating, ADR 0002).
 */
public class MobileException extends RuntimeException {

    public MobileException(String message) {
        super(message);
    }

    public MobileException(String message, Throwable cause) {
        super(message, cause);
    }
}
