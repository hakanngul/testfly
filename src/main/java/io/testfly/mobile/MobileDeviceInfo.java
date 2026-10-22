package io.testfly.mobile;

/**
 * A device as reported by mobilecli ({@code devices.list} / {@code device.info}).
 *
 * @param id       mobilecli device id (Android emulator: the AVD name; iOS: the UDID)
 * @param name     display name
 * @param platform {@code android} or {@code ios}
 * @param type     {@code real}, {@code simulator} or {@code emulator}
 * @param state    {@code online} or {@code offline}
 * @param version  OS version, or {@code null}
 * @param model    model, or {@code null}
 */
public record MobileDeviceInfo(String id, String name, String platform, String type, String state, String version, String model) {

    public boolean isOnline() {
        return "online".equalsIgnoreCase(state);
    }

    public String describe() {
        return id + " (" + name + ", " + type + ", " + state + ")";
    }
}
