package io.testfly.mobile;

/** mobilecli does not know the requested device. */
public class DeviceNotFoundException extends MobileException {

    public DeviceNotFoundException(String message) {
        super(message);
    }
}
