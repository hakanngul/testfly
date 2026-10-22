package io.testfly.mobile;

/** The mobilecli server is older than the minimum version TestFly supports. */
public class MobileVersionException extends MobileException {

    public MobileVersionException(String message) {
        super(message);
    }
}
