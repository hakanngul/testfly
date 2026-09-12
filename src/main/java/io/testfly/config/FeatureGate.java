package io.testfly.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.testfly.api.TestFlyApi;
import io.testfly.internal.TestFlyContext;

/**
 * Single resolution point for the {@code features:} master switchboard in
 * {@code testfly.yml}.
 *
 * <p>
 * Each optional framework module keeps its own detailed settings
 * ({@code recording.enabled}, {@code locators.selfHealing},
 * {@code network.interceptEnabled}, …). {@code features:} sits
 * <em>above</em> them as a uniform on/off panel:
 *
 * <pre>
 * features:
 *   ai: false          # disable every AI/agentic surface
 *   recording: true    # force video recording on
 *   healing: false     # disable locator self-healing
 * </pre>
 *
 * <h2>
 * Resolution</h2>
 * The override is tri-state. An explicit {@code true}/{@code false} wins over
 * the module's own flag; an absent key (or {@code null}) defers to it, so
 * omitting the whole {@code features:} block reproduces pre-1.0.5 behaviour
 * exactly.
 *
 * <p>
 * Modules whose own flag lives under a differently-named key are mapped here,
 * so call sites never need to know which spelling a module uses:
 *
 * <table border="1">
 * <caption>Feature → module flag</caption>
 * <tr>
 * <th>{@code features.*}</th>
 * <th>module default</th>
 * </tr>
 * <tr>
 * <td>{@code healing}</td>
 * <td>{@code locators.selfHealing || locators.aiHealing}</td>
 * </tr>
 * <tr>
 * <td>{@code network}</td>
 * <td>{@code network.interceptEnabled}</td>
 * </tr>
 * <tr>
 * <td>{@code consoleErrors}</td>
 * <td>{@code browser.captureConsoleErrors}</td>
 * </tr>
 * </table>
 *
 * <h2>
 * Uninitialized context</h2>
 * {@link TestFlyContext#getConfig()} throws before framework bootstrap. Every
 * method here swallows that and falls back to the caller-supplied module
 * default, so a unit test that never boots the framework is never blocked by
 * the umbrella.
 *
 * <h2>
 * Custom features</h2>
 * Plugins may gate their own behaviour with a private feature name — unknown
 * names are accepted and logged once as a warning so a typo in
 * {@code testfly.yml} is visible instead of silently ignored.
 */
@TestFlyApi(since = "1.0.5")
public final class FeatureGate {

    /**
     * Every AI/agentic surface: failure analysis, patch generation, AI healing,
     * {@code aiAssert}, {@code act()}.
     */
    public static final String AI = "ai";
    /** MP4/GIF video capture of browser execution. */
    public static final String RECORDING = "recording";
    /** DOM snapshot and execution timeline capture. */
    public static final String TRACING = "tracing";
    /** CDP network interception, route mocking and URL blocklists. */
    public static final String NETWORK = "network";
    /** Locator self-healing, including the AI fallback. */
    public static final String HEALING = "healing";
    /** Visual regression comparison. */
    public static final String VISUAL = "visual";
    /** Core Web Vitals collection. */
    public static final String PERFORMANCE = "performance";
    /** Flakiness history scoring. */
    public static final String FLAKINESS = "flakiness";
    /** Automatic skipping of quarantined tests. */
    public static final String QUARANTINE = "quarantine";
    /** TestRail / Xray result push. */
    public static final String TEST_MANAGEMENT = "testManagement";
    /** Slack / Teams run notifications. */
    public static final String NOTIFICATIONS = "notifications";
    /** Browser console error collection. */
    public static final String CONSOLE_ERRORS = "consoleErrors";
    /** Load testing execution engine (Gatling / virtual threads). */
    public static final String LOADTEST = "loadtest";

    private static final Set<String> KNOWN;
    static {
        Set<String> names = new LinkedHashSet<>();
        names.add(AI);
        names.add(RECORDING);
        names.add(TRACING);
        names.add(NETWORK);
        names.add(HEALING);
        names.add(VISUAL);
        names.add(PERFORMANCE);
        names.add(FLAKINESS);
        names.add(QUARANTINE);
        names.add(TEST_MANAGEMENT);
        names.add(NOTIFICATIONS);
        names.add(CONSOLE_ERRORS);
        names.add(LOADTEST);
        KNOWN = Collections.unmodifiableSet(names);
    }

    /**
     * Feature names already warned about, so a typo is reported once rather than
     * per call.
     */
    private static final Set<String> WARNED = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private FeatureGate() {
        // utility class
    }

    /**
     * Returns the feature names the framework resolves out of the box. Plugins
     * may use names outside this set.
     */
    public static Set<String> knownFeatures() {
        return KNOWN;
    }

    /**
     * Resolves a feature that has no module-level flag of its own, i.e. it is
     * on unless {@code features.<name>} explicitly turns it off.
     *
     * @param feature feature name, e.g. {@link #VISUAL}
     */
    public static boolean enabled(String feature) {
        return enabled(feature, true);
    }

    /**
     * Resolves a feature against the module's own configuration.
     *
     * @param feature       feature name, e.g. {@link #RECORDING}
     * @param moduleDefault the value the module's own settings produce; used
     *                      when {@code features.<name>} is absent and when the
     *                      framework context is not initialized
     * @return the override when set, otherwise {@code moduleDefault}
     */
    public static boolean enabled(String feature, boolean moduleDefault) {
        Boolean override = override(feature);
        return (override != null) ? override : moduleDefault;
    }

    /**
     * Raw tri-state lookup.
     *
     * @return the explicit {@code features.<name>} value, or {@code null} when
     *         the feature is not configured or the context is unavailable
     */
    public static Boolean override(String feature) {
        if (feature == null || feature.isBlank()) {
            return null;
        }
        Map<String, Boolean> overrides = overrides();
        if (overrides == null || !overrides.containsKey(feature)) {
            return null;
        }
        warnIfUnknown(feature, overrides);
        return overrides.get(feature);
    }

    /**
     * Human-readable rendering of the explicitly configured overrides, e.g.
     * {@code "ai=false, recording=true"}. Empty when the {@code features:}
     * block is absent — used for the bootstrap log line.
     */
    public static String summary() {
        Map<String, Boolean> overrides = overrides();
        if (overrides == null || overrides.isEmpty()) {
            return "";
        }
        Map<String, Boolean> ordered = new LinkedHashMap<>(overrides);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Boolean> e : ordered.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e.getKey()).append('=')
                    .append(Boolean.TRUE.equals(e.getValue()) ? "ON" : "OFF");
        }
        return sb.toString();
    }

    /** Clears the unknown-name warning cache. Test support. */
    public static void resetWarnings() {
        WARNED.clear();
    }

    private static Map<String, Boolean> overrides() {
        try {
            TestFlyConfig cfg = TestFlyContext.getConfig();
            return (cfg != null) ? cfg.getFeatures() : null;
        } catch (RuntimeException e) {
            // Framework not bootstrapped — the umbrella must never block a module
            // that would otherwise run.
            return null;
        }
    }

    private static void warnIfUnknown(String feature, Map<String, Boolean> overrides) {
        if (KNOWN.contains(feature) || !WARNED.add(feature)) {
            return;
        }
        // Same channel as ConfigurationLoader's unknown-key warning, so config
        // diagnostics reach the user even when java.util.logging is unconfigured.
        System.err.println("[TestFly] Unknown feature name in testfly.yml: 'features." + feature
                + "' — ignored. Known features: " + KNOWN
                + (overrides.size() > 1 ? " (plugin-defined names are also accepted)" : ""));
    }
}
