package io.testfly.mobile.internal;

import io.testfly.mobile.DeviceSelectionException;
import io.testfly.mobile.MobileDeviceInfo;
import io.testfly.mobile.MobilePlatform;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Deterministic device selection (ADR 0002 D5): {@code deviceId} must match exactly one online
 * device of the platform, else the {@code deviceName} regex must, else exactly one online device of
 * the platform must exist. Anything else fails with a list of the candidates; a device is never
 * picked arbitrarily.
 */
public final class DeviceSelector {

    private DeviceSelector() {
    }

    public static MobileDeviceInfo select(List<MobileDeviceInfo> devices, MobilePlatform platform, String deviceId, String deviceName) {
        List<MobileDeviceInfo> online = devices.stream()
                .filter(MobileDeviceInfo::isOnline)
                .filter(d -> platform.wireName().equalsIgnoreCase(d.platform()))
                .toList();
        String selector;
        List<MobileDeviceInfo> matches;
        if (deviceId != null && !deviceId.isBlank()) {
            selector = "mobile.deviceId=" + deviceId;
            matches = online.stream().filter(d -> deviceId.equals(d.id())).toList();
        } else if (deviceName != null && !deviceName.isBlank()) {
            Pattern pattern;
            try {
                pattern = Pattern.compile(deviceName);
            } catch (PatternSyntaxException e) {
                throw new DeviceSelectionException("mobile.deviceName is not a valid regex: " + e.getMessage());
            }
            selector = "mobile.deviceName=/" + deviceName + "/";
            matches = online.stream().filter(d -> d.name() != null && pattern.matcher(d.name()).find()).toList();
        } else {
            selector = "the only online " + platform.wireName() + " device";
            matches = online;
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        String reason = matches.isEmpty() ? "No online " + platform.wireName() + " device matches " + selector
                : matches.size() + " online " + platform.wireName() + " devices match " + selector
                        + "; set mobile.deviceId or a narrower mobile.deviceName";
        throw new DeviceSelectionException(reason + ".\nDevices reported by mobilecli:\n" + list(devices)
                + (devices.isEmpty() ? hint(platform) : ""));
    }

    private static String list(List<MobileDeviceInfo> devices) {
        if (devices.isEmpty()) {
            return "  (none)";
        }
        return devices.stream().map(d -> "  - " + d.describe() + " [" + d.platform() + "]").collect(Collectors.joining("\n"));
    }

    private static String hint(MobilePlatform platform) {
        return platform == MobilePlatform.IOS
                ? "\nBoot a simulator (xcrun simctl boot \"<name>\") or connect a device."
                : "\nStart an emulator (emulator -avd <name>) or connect a device (adb devices).";
    }
}
