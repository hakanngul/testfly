package io.testfly.mobile;

/** The mobilecli server cannot be reached (connection refused, unknown host). */
public class MobileServerUnavailableException extends MobileException {

    public MobileServerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
