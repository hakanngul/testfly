package io.testfly.mobile.internal;

import io.testfly.mobile.MobileVersionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Checks {@code server.info} against the minimum mobilecli version (ADR 0002 D1). */
public final class ServerVersion {

    /** Oldest mobilecli release TestFly was verified against. */
    public static final String MINIMUM = "1.0.20";

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerVersion.class);

    private ServerVersion() {
    }

    public static void require(String actual) {
        int[] have = parse(actual);
        if (have == null) {
            LOGGER.warn("mobilecli reported an unrecognised version '{}'; expected {} or newer", actual, MINIMUM);
            return;
        }
        if (compare(have, parse(MINIMUM)) < 0) {
            throw new MobileVersionException("mobilecli " + actual + " is older than the minimum supported version " + MINIMUM
                    + ". Upgrade mobilecli and restart 'mobilecli server start'.");
        }
    }

    static int[] parse(String version) {
        if (version == null) {
            return null;
        }
        String v = version.trim();
        if (v.startsWith("v")) {
            v = v.substring(1);
        }
        int cut = v.indexOf('-');
        if (cut >= 0) {
            v = v.substring(0, cut);
        }
        String[] parts = v.split("\\.");
        if (parts.length == 0 || parts.length > 3) {
            return null;
        }
        int[] out = new int[3];
        try {
            for (int i = 0; i < parts.length; i++) {
                out[i] = Integer.parseInt(parts[i]);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    static int compare(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }
}
