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

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(DeviceSelector.class);

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

    /**
     * The devices a session may use, in preference order (ADR 0002 D7):
     * <ul>
     *   <li>{@code poolIds} non-empty: those ids that are online on the platform (offline or unknown
     *       ones are skipped with a warning; none online fails);</li>
     *   <li>{@code pool}: every online device of the platform whose name matches {@code deviceName};</li>
     *   <li>otherwise exactly one device, chosen by {@link #select}.</li>
     * </ul>
     */
    public static List<MobileDeviceInfo> candidates(List<MobileDeviceInfo> devices, MobilePlatform platform, String deviceId,
                                                    String deviceName, List<String> poolIds, boolean pool) {
        List<MobileDeviceInfo> online = devices.stream()
                .filter(MobileDeviceInfo::isOnline)
                .filter(d -> platform.wireName().equalsIgnoreCase(d.platform()))
                .toList();
        if (poolIds != null && !poolIds.isEmpty()) {
            List<MobileDeviceInfo> chosen = new java.util.ArrayList<>();
            List<String> missing = new java.util.ArrayList<>();
            for (String id : poolIds) {
                online.stream().filter(d -> id.equals(d.id())).findFirst().ifPresentOrElse(chosen::add, () -> missing.add(id));
            }
            if (chosen.isEmpty()) {
                throw new DeviceSelectionException("None of the mobile.devices " + poolIds + " is an online " + platform.wireName()
                        + " device.\nDevices reported by mobilecli:\n" + list(devices));
            }
            if (!missing.isEmpty()) {
                LOGGER.warn("mobile.devices {} are not online {} devices; the pool continues with {}", missing,
                        platform.wireName(), chosen.stream().map(MobileDeviceInfo::id).toList());
            }
            return List.copyOf(chosen);
        }
        if (pool) {
            Pattern pattern = deviceName == null || deviceName.isBlank() ? null : compile(deviceName);
            List<MobileDeviceInfo> chosen = online.stream()
                    .filter(d -> pattern == null || (d.name() != null && pattern.matcher(d.name()).find()))
                    .toList();
            if (chosen.isEmpty()) {
                throw new DeviceSelectionException("mobile.pool found no online " + platform.wireName() + " device"
                        + (pattern != null ? " matching mobile.deviceName=/" + deviceName + "/" : "")
                        + ".\nDevices reported by mobilecli:\n" + list(devices) + (devices.isEmpty() ? hint(platform) : ""));
            }
            return chosen;
        }
        return List.of(select(devices, platform, deviceId, deviceName));
    }

    private static Pattern compile(String regex) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new DeviceSelectionException("mobile.deviceName is not a valid regex: " + e.getMessage());
        }
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
