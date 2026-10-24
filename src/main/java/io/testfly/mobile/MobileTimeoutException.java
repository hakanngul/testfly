package io.testfly.mobile;

/** An action, assertion or JSON-RPC request ran out of time. */
public class MobileTimeoutException extends MobileException {

    public MobileTimeoutException(String message) {
        super(message);
    }

    public MobileTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
