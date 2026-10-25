package io.testfly.inspector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Match counts of selectors on the screen the open Mobile Inspector shows, for {@link MobileFindCodeVision}.
 * The counting runs in the inspector's page (media/selectors.js, TestFly's matching rules): a selector not
 * counted yet is sent there, and the editors are refreshed when the answer comes back.
 */
public final class LocatorCounts {

    /** The open inspector. */
    public interface Target {
        /** e.g. "Pixel 8" */
        String label();

        /** "android" or "ios" */
        String platform();

        /** Asks the page to count the selectors; the answer comes through {@link #resolved}. */
        void resolve(long requestId, List<String> selectors);

        /** Shows the selector's matches in the inspector. */
        void reveal(String selector);
    }

    /** A count, or the reason the selector is invalid. */
    public record Count(int count, String error) {
    }

    private record Request(long generation, List<String> selectors) {
    }

    private static final Object LOCK = new Object();
    private static Target target;
    private static long generation;
    private static long nextId;
    private static final Map<String, Count> CACHE = new HashMap<>();
    private static final Set<String> PENDING = new HashSet<>();
    private static final Map<Long, Request> REQUESTS = new HashMap<>();
    private static volatile Runnable onChange = () -> {
    };

    private LocatorCounts() {
    }

    /** Called when counts change: the IDE restarts its code vision pass. */
    public static void onChange(Runnable listener) {
        onChange = listener;
    }

    public static Target target() {
        synchronized (LOCK) {
            return target;
        }
    }

    /** A new screen (or another device): earlier counts no longer hold. */
    public static void screenRead(Target t) {
        synchronized (LOCK) {
            target = t;
            generation++;
            CACHE.clear();
            PENDING.clear();
            REQUESTS.clear();
        }
        onChange.run();
    }

    /** The inspector closed, or has no device any more. */
    public static void closed(Target t) {
        synchronized (LOCK) {
            if (target != t) {
                return;
            }
            target = null;
            generation++;
            CACHE.clear();
            PENDING.clear();
            REQUESTS.clear();
        }
        onChange.run();
    }

    /** The counts known for the selectors; the others are requested and missing from the result until they arrive. */
    public static Map<String, Count> counts(List<String> selectors) {
        Map<String, Count> known = new HashMap<>();
        List<String> missing = new ArrayList<>();
        Target t;
        long id;
        synchronized (LOCK) {
            t = target;
            if (t == null) {
                return known;
            }
            for (String s : selectors) {
                Count c = CACHE.get(s);
                if (c != null) {
                    known.put(s, c);
                } else if (!PENDING.contains(s) && !missing.contains(s)) {
                    missing.add(s);
                }
            }
            if (missing.isEmpty()) {
                return known;
            }
            id = ++nextId;
            REQUESTS.put(id, new Request(generation, missing));
            PENDING.addAll(missing);
        }
        t.resolve(id, missing);
        return known;
    }

    /** The page's answer to request {@code id}, in the order of its selectors. */
    public static void resolved(long id, List<Count> results) {
        synchronized (LOCK) {
            Request r = REQUESTS.remove(id);
            if (r == null || r.generation != generation) {
                return; // an answer for a screen that is gone
            }
            for (int i = 0; i < r.selectors.size(); i++) {
                PENDING.remove(r.selectors.get(i));
                if (i < results.size()) {
                    CACHE.put(r.selectors.get(i), results.get(i));
                }
            }
        }
        onChange.run();
    }

    /** The code vision text, as the VS Code extension's code lens shows it. */
    public static String label(Count c, String platform, String deviceLabel, boolean hasSelector) {
        String on = " on " + deviceLabel;
        if (!hasSelector) {
            return "⊘ no " + ("ios".equalsIgnoreCase(platform) ? "iOS" : "Android") + " selector";
        }
        if (c == null) {
            return "… counting" + on;
        }
        if (c.error() != null && !c.error().isEmpty()) {
            return "✗ " + c.error();
        }
        if (c.count() < 0) {
            return "… not read yet" + on;
        }
        return c.count() == 1 ? "✓ 1 match" + on : c.count() == 0 ? "✗ no match" + on : "⚠ " + c.count() + " matches" + on;
    }
}
