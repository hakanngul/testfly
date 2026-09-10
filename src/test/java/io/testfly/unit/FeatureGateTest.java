package io.testfly.unit;

import io.testfly.config.ConfigurationLoader;
import io.testfly.config.FeatureGate;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Verifies the {@code features:} master switchboard: tri-state resolution in
 * {@link FeatureGate}, its wiring into every module predicate on
 * {@link TestFlyConfig}, YAML binding, and the loader's tolerance of unknown
 * keys.
 *
 * <p>
 * {@link TestFlyContext} is JVM-global static state, so every test here runs
 * single-threaded and resets the context afterwards.
 */
@Test(singleThreaded = true)
public class FeatureGateTest {

    private File tmp;

    @AfterMethod
    public void cleanup() {
        TestFlyContext.reset();
        FeatureGate.resetWarnings();
        System.clearProperty("testfly.config");
        if (tmp != null) {
            tmp.delete();
            tmp = null;
        }
    }

    // ----------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------

    private TestFlyConfig newConfig() {
        TestFlyConfig config = new TestFlyConfig();
        TestFlyConfig.Browser browser = new TestFlyConfig.Browser();
        browser.setName("chrome");
        config.setBrowser(browser);
        return config;
    }

    private TestFlyConfig configWith(String feature, Boolean value) {
        TestFlyConfig config = newConfig();
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put(feature, value);
        config.setFeatures(features);
        TestFlyContext.setConfig(config);
        return config;
    }

    private TestFlyConfig loadYaml(String yaml) throws Exception {
        tmp = File.createTempFile("testfly-features-", ".yml");
        Files.writeString(tmp.toPath(), yaml);
        System.setProperty("testfly.config", tmp.getAbsolutePath());
        return ConfigurationLoader.load();
    }

    private static final String MINIMAL_YAML = String.join("\n",
            "browser:",
            "  name: chrome",
            "execution:",
            "  mode: local",
            "  baseUrl: https://example.com",
            "timeouts:",
            "  explicit: 10",
            "  pageLoad: 30",
            "");

    /** Captures System.err written by {@code body}. */
    private String captureErr(Runnable body) {
        PrintStream original = System.err;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setErr(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            body.run();
        } finally {
            System.setErr(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    // ----------------------------------------------------------
    // Tri-state resolution
    // ----------------------------------------------------------

    @Test
    public void enabled_noContextAtAll_returnsModuleDefault() {
        // TestFlyContext.getConfig() throws before bootstrap; the umbrella must
        // never block a module that would otherwise run.
        assertTrue(FeatureGate.enabled("recording", true));
        assertFalse(FeatureGate.enabled("recording", false));
    }

    @Test
    public void enabled_noFeaturesBlock_defersToModuleDefault() {
        TestFlyContext.setConfig(newConfig());
        assertTrue(FeatureGate.enabled("recording", true));
        assertFalse(FeatureGate.enabled("recording", false));
    }

    @Test
    public void enabled_explicitFalse_overridesTrueModuleDefault() {
        configWith(FeatureGate.RECORDING, Boolean.FALSE);
        assertFalse(FeatureGate.enabled(FeatureGate.RECORDING, true));
    }

    @Test
    public void enabled_explicitTrue_overridesFalseModuleDefault() {
        configWith(FeatureGate.RECORDING, Boolean.TRUE);
        assertTrue(FeatureGate.enabled(FeatureGate.RECORDING, false));
    }

    @Test
    public void enabled_absentKey_defersToModuleDefault() {
        configWith(FeatureGate.RECORDING, Boolean.TRUE);
        assertFalse(FeatureGate.enabled(FeatureGate.TRACING, false));
        assertTrue(FeatureGate.enabled(FeatureGate.TRACING, true));
    }

    @Test
    public void override_returnsRawTriState() {
        configWith(FeatureGate.AI, Boolean.FALSE);
        assertEquals(FeatureGate.override(FeatureGate.AI), Boolean.FALSE);
        assertNull(FeatureGate.override(FeatureGate.RECORDING), "absent key must be null");
    }

    @Test
    public void override_nullOrBlankName_returnsNull() {
        configWith(FeatureGate.AI, Boolean.FALSE);
        assertNull(FeatureGate.override(null));
        assertNull(FeatureGate.override("   "));
    }

    // ----------------------------------------------------------
    // AI kill-switch
    // ----------------------------------------------------------

    @Test
    public void aiEnabled_defaultsTrue() {
        assertTrue(new TestFlyConfig.Ai().isEnabled(),
                "AI must stay on by default so existing configs are unaffected");
    }

    @Test
    public void featuresAiFalse_disablesAiEvenWhenAiEnabledTrue() {
        TestFlyConfig config = configWith(FeatureGate.AI, Boolean.FALSE);
        TestFlyConfig.Ai ai = new TestFlyConfig.Ai();
        ai.setEnabled(true);
        config.setAi(ai);

        assertFalse(ai.isEnabled(), "umbrella must win over ai.enabled");
    }

    @Test
    public void aiEnabledFalse_withoutUmbrella_disablesAi() {
        TestFlyContext.setConfig(newConfig());
        TestFlyConfig.Ai ai = new TestFlyConfig.Ai();
        ai.setEnabled(false);

        assertFalse(ai.isEnabled());
    }

    // ----------------------------------------------------------
    // Module predicate wiring
    // ----------------------------------------------------------

    @Test
    public void featuresRecordingTrue_forcesRecordingOn() {
        TestFlyConfig config = configWith(FeatureGate.RECORDING, Boolean.TRUE);
        TestFlyConfig.Recording rec = new TestFlyConfig.Recording();
        config.setRecording(rec);

        assertTrue(rec.isEnabled(), "umbrella forces the recorder on");
        assertTrue(rec.shouldRecord());
    }

    @Test
    public void featuresRecordingFalse_disablesRecordAllEvenWhenModeOn() {
        TestFlyConfig config = configWith(FeatureGate.RECORDING, Boolean.FALSE);
        TestFlyConfig.Recording rec = new TestFlyConfig.Recording();
        rec.setEnabled(true);
        rec.setMode("on");
        config.setRecording(rec);

        assertFalse(rec.isEnabled());
        assertFalse(rec.shouldRecord());
        assertFalse(rec.isRecordAll(), "a disabled recorder must not record on pass");
    }

    @Test
    public void recordAll_requiresEnabledFlag() {
        // Documents the intentional tightening: mode alone no longer implies recording.
        TestFlyContext.setConfig(newConfig());
        TestFlyConfig.Recording rec = new TestFlyConfig.Recording();
        rec.setMode("on");

        assertFalse(rec.isRecordAll(), "enabled=false + mode=on must not record");
        rec.setEnabled(true);
        assertTrue(rec.isRecordAll());
    }

    @Test
    public void featuresHealingFalse_disablesBothHealingFlags() {
        TestFlyConfig config = configWith(FeatureGate.HEALING, Boolean.FALSE);
        TestFlyConfig.Locators locators = new TestFlyConfig.Locators();
        locators.setSelfHealing(true);
        locators.setAiHealing(true);
        config.setLocators(locators);

        assertFalse(locators.isSelfHealing());
        assertFalse(locators.isAiHealing());
    }

    @Test
    public void featuresHealingTrue_forcesSelfHealingOn() {
        TestFlyConfig config = configWith(FeatureGate.HEALING, Boolean.TRUE);
        config.setLocators(new TestFlyConfig.Locators());

        assertTrue(config.getLocators().isSelfHealing());
    }

    @Test
    public void featuresNetworkFalse_disablesIntercept() {
        TestFlyConfig config = configWith(FeatureGate.NETWORK, Boolean.FALSE);
        TestFlyConfig.Network net = new TestFlyConfig.Network();
        net.setInterceptEnabled(true);
        config.setNetwork(net);

        assertFalse(net.isInterceptEnabled());
    }

    @Test
    public void featuresConsoleErrorsFalse_disablesCapture() {
        TestFlyConfig config = configWith(FeatureGate.CONSOLE_ERRORS, Boolean.FALSE);
        config.getBrowser().setCaptureConsoleErrors(true);

        assertFalse(config.getBrowser().isCaptureConsoleErrors());
    }

    @Test
    public void quarantine_defaultsOn() {
        TestFlyContext.setConfig(newConfig());
        assertTrue(new TestFlyConfig.Quarantine().isEnabled(), "quarantine defaults on");
    }

    @Test
    public void featuresQuarantineFalse_disablesQuarantine() {
        TestFlyConfig config = configWith(FeatureGate.QUARANTINE, Boolean.FALSE);
        TestFlyConfig.Quarantine quarantine = new TestFlyConfig.Quarantine();
        config.setQuarantine(quarantine);

        assertFalse(quarantine.isEnabled());
    }

    @Test
    public void featuresTracingFalse_disablesTracing() {
        TestFlyConfig config = configWith(FeatureGate.TRACING, Boolean.FALSE);
        TestFlyConfig.Tracing tracing = new TestFlyConfig.Tracing();
        tracing.setEnabled(true);
        config.setTracing(tracing);

        assertFalse(tracing.isEnabled());
    }

    @Test
    public void featuresPerformanceFalse_disablesCapture() {
        TestFlyConfig config = configWith(FeatureGate.PERFORMANCE, Boolean.FALSE);
        TestFlyConfig.Performance performance = new TestFlyConfig.Performance();
        performance.setCaptureOnEveryTest(true);
        config.setPerformance(performance);

        assertFalse(performance.isCaptureOnEveryTest());
    }

    @Test
    public void featuresTestManagementFalse_disablesTestRailAndXray() {
        TestFlyConfig config = configWith(FeatureGate.TEST_MANAGEMENT, Boolean.FALSE);
        TestFlyConfig.TestManagement tm = new TestFlyConfig.TestManagement();
        TestFlyConfig.TestManagement.TestRail testRail = new TestFlyConfig.TestManagement.TestRail();
        testRail.setEnabled(true);
        TestFlyConfig.TestManagement.Xray xray = new TestFlyConfig.TestManagement.Xray();
        xray.setEnabled(true);
        tm.setTestrail(testRail);
        tm.setXray(xray);
        config.setTestManagement(tm);

        assertFalse(testRail.isEnabled());
        assertFalse(xray.isEnabled());
    }

    // ----------------------------------------------------------
    // YAML binding
    // ----------------------------------------------------------

    @Test
    public void featuresBlock_bindsFromYaml() throws Exception {
        TestFlyConfig config = loadYaml(MINIMAL_YAML + String.join("\n",
                "features:",
                "  ai: false",
                "  recording: true",
                ""));

        assertEquals(config.getFeatures().size(), 2);
        assertEquals(config.getFeatures().get("ai"), Boolean.FALSE);
        assertEquals(config.getFeatures().get("recording"), Boolean.TRUE);
    }

    @Test
    public void featuresBlock_absent_isEmptyNotNull() throws Exception {
        TestFlyConfig config = loadYaml(MINIMAL_YAML);

        assertNotNull(config.getFeatures(), "must never be null");
        assertTrue(config.getFeatures().isEmpty());
    }

    @Test
    public void featuresBlock_drivesModulesAfterYamlLoad() throws Exception {
        TestFlyConfig config = loadYaml(MINIMAL_YAML + String.join("\n",
                "ai:",
                "  enabled: true",
                "  apiKey: dummy-key",
                "recording:",
                "  enabled: false",
                "features:",
                "  ai: false",
                "  recording: true",
                ""));
        TestFlyContext.setConfig(config);

        assertFalse(config.getAi().isEnabled(), "features.ai=false beats ai.enabled=true");
        assertTrue(config.getRecording().isEnabled(), "features.recording=true beats recording.enabled=false");
    }

    @Test
    public void setFeatures_nullBecomesEmpty() {
        TestFlyConfig config = newConfig();
        config.setFeatures(null);
        assertNotNull(config.getFeatures());
        assertTrue(config.getFeatures().isEmpty());
    }

    // ----------------------------------------------------------
    // Loader tolerance for unknown keys
    // ----------------------------------------------------------

    @Test
    public void unknownTopLevelKey_warnsButStillLoads() throws Exception {
        String[] output = new String[1];
        TestFlyConfig[] loaded = new TestFlyConfig[1];

        output[0] = captureErr(() -> {
            try {
                loaded[0] = loadYaml(MINIMAL_YAML + "totallyBogusKey: 123\n");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertNotNull(loaded[0], "a typo must not abort the suite");
        assertEquals(loaded[0].getBrowser().getName(), "chrome");
        assertTrue(output[0].contains("totallyBogusKey"), "warning should name the key");
        assertTrue(output[0].contains("ignored"), "warning should say the key was ignored");
    }

    @Test
    public void unknownNestedKey_warnsButStillLoads() throws Exception {
        String[] output = new String[1];
        TestFlyConfig[] loaded = new TestFlyConfig[1];

        output[0] = captureErr(() -> {
            try {
                loaded[0] = loadYaml(MINIMAL_YAML + String.join("\n",
                        "browser:",
                        "  name: chrome",
                        "  headles: true",
                        ""));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertNotNull(loaded[0]);
        assertEquals(loaded[0].getBrowser().getName(), "chrome");
        assertTrue(output[0].contains("headles"), "should flag the misspelled nested key");
        assertTrue(output[0].contains("Browser"), "should name the owning bean");
    }

    @Test
    public void unknownFeatureName_isToleratedAndWarns() throws Exception {
        TestFlyConfig config = loadYaml(MINIMAL_YAML + String.join("\n",
                "features:",
                "  recordng: false",
                ""));
        TestFlyContext.setConfig(config);

        // Warnings are emitted once per name, so clear the cache before capturing.
        FeatureGate.resetWarnings();
        String output = captureErr(() -> {
            // Resolution never throws on an unrecognised name — a plugin may own it.
            assertFalse(FeatureGate.enabled("recordng", true));
        });
        assertTrue(output.contains("recordng"), "a typo'd feature name should be reported");
        assertTrue(output.contains("Unknown feature name"), "warning should be self-explanatory");

        assertEquals(FeatureGate.override("recordng"), Boolean.FALSE);
        assertTrue(FeatureGate.summary().contains("recordng"));
    }

    // ----------------------------------------------------------
    // Kill-switch actually blocks at the call site
    // ----------------------------------------------------------

    /**
     * A configured apiKey must not matter once AI is switched off — no provider is
     * reached.
     */
    private TestFlyConfig configWithLiveLookingAi(String feature, Boolean value) {
        TestFlyConfig config = configWith(feature, value);
        TestFlyConfig.Ai ai = new TestFlyConfig.Ai();
        ai.setEnabled(true);
        ai.setApiKey("would-be-used-if-not-blocked");
        ai.setFailureAnalysis(true);
        config.setAi(ai);
        return config;
    }

    @Test
    public void aiDisabled_actThrowsInsteadOfCallingProvider() {
        TestFlyConfig config = configWithLiveLookingAi(FeatureGate.AI, Boolean.FALSE);

        IllegalStateException e = expectThrows(IllegalStateException.class,
                () -> io.testfly.agent.ActionCompiler.compileFromAi(null, "click the button", config));
        assertTrue(e.getMessage().contains("AI features are disabled"), e.getMessage());
    }

    @Test
    public void aiDisabled_aiAssertFailsWithReasonInsteadOfCallingProvider() {
        configWithLiveLookingAi(FeatureGate.AI, Boolean.FALSE);

        io.testfly.assertion.ai.AiAssertEngine.AiAssertionResult result = io.testfly.assertion.ai.AiAssertEngine
                .verify(null, "<html><body>cart</body></html>", "page shows a cart", true);

        assertFalse(result.isPassed(), "a disabled feature must never report a pass");
        assertTrue(result.reason().contains("AI features are disabled"), result.reason());
    }

    @Test
    public void aiEnabled_actFailsOnMissingKeyRatherThanTheKillSwitch() {
        // Control: with the umbrella open, the same call gets past the gate and
        // fails later, proving the gate above is what blocked it.
        TestFlyConfig config = configWithLiveLookingAi(FeatureGate.AI, Boolean.TRUE);
        config.getAi().setApiKey(null);

        IllegalStateException e = expectThrows(IllegalStateException.class,
                () -> io.testfly.agent.ActionCompiler.compileFromAi(null, "click the button", config));
        assertFalse(e.getMessage().contains("AI features are disabled"), e.getMessage());
    }

    // ----------------------------------------------------------
    // FeatureGate API surface
    // ----------------------------------------------------------

    @Test
    public void knownFeatures_coversEveryWiredModule() {
        assertEquals(FeatureGate.knownFeatures().size(), 12);
        assertTrue(FeatureGate.knownFeatures().containsAll(java.util.List.of(
                FeatureGate.AI, FeatureGate.RECORDING, FeatureGate.TRACING,
                FeatureGate.NETWORK, FeatureGate.HEALING, FeatureGate.VISUAL,
                FeatureGate.PERFORMANCE, FeatureGate.FLAKINESS, FeatureGate.QUARANTINE,
                FeatureGate.TEST_MANAGEMENT, FeatureGate.NOTIFICATIONS,
                FeatureGate.CONSOLE_ERRORS)));
    }

    @Test
    public void summary_rendersExplicitOverrides() {
        configWith(FeatureGate.AI, Boolean.FALSE);
        assertEquals(FeatureGate.summary(), "ai=OFF");
    }

    @Test
    public void summary_emptyWhenNothingConfigured() {
        TestFlyContext.setConfig(newConfig());
        assertEquals(FeatureGate.summary(), "");
    }

    @Test
    public void enabled_noModuleDefaultArgument_isOnUnlessExplicitlyOff() {
        TestFlyContext.setConfig(newConfig());
        assertTrue(FeatureGate.enabled(FeatureGate.VISUAL), "visual defaults on");

        configWith(FeatureGate.VISUAL, Boolean.FALSE);
        assertFalse(FeatureGate.enabled(FeatureGate.VISUAL));
    }
}
