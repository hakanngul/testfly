package io.testfly.mobile;

import java.util.Locale;

/** Device platform. */
public enum MobilePlatform {
    ANDROID, IOS;

    /** mobilecli's name of the platform ({@code android}, {@code ios}). */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses {@code android}/{@code ios} (case-insensitive); {@code null} when unknown or blank. */
    public static MobilePlatform fromWire(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "android" -> ANDROID;
            case "ios" -> IOS;
            default -> null;
        };
    }
}
