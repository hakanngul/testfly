package io.testfly.unit;

import io.testfly.ai.AiFailureAnalyzer;
import io.testfly.config.ConfigurationLoader;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.metrics.TestTiming;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Integration test that calls the real DeepSeek API (deepseek-v4-flash)
 * through {@link AiFailureAnalyzer} and verifies the analysis is recorded.
 *
 * <p>Requires a valid API key in {@code testfly.yml} under {@code ai.apiKey}.
 * Run explicitly with:
 * <pre>mvn test -Dtest=AiFailureAnalyzerIntegrationTest</pre>
 */
public class AiFailureAnalyzerIntegrationTest {

    private static final String TEST_ID = "ai-integration-test#simulatedFailure[L1]";

    @BeforeClass
    public void setUp() {
        TestFlyConfig config = ConfigurationLoader.load();
        TestFlyContext.initialize(config);

        // Simulate a failed test in ExecutionMetrics
        ExecutionMetrics.markStart(TEST_ID);
        ExecutionMetrics.recordTestClass(TEST_ID, "ai-integration-test");
        ExecutionMetrics.recordDescription(TEST_ID, "Simulated failure for AI analysis");
        ExecutionMetrics.recordStatus(TEST_ID, "FAILED");
        ExecutionMetrics.recordError(TEST_ID, new AssertionError(
                "Expected 'Products' but found 'Login' — element not visible after 10s"));
        ExecutionMetrics.markEnd(TEST_ID);
    }

    @Test
    public void aiShouldAnalyzeFailureAndRecordResult() {
        // Act — call the real API
        AiFailureAnalyzer.analyze(TEST_ID, "https://www.saucedemo.com/", "Swag Labs");

        // Assert — analysis should be recorded
        TestTiming timing = ExecutionMetrics.getTiming(TEST_ID);
        assertNotNull(timing, "TestTiming should exist for the simulated test");

        String analysis = timing.getAiAnalysis();
        assertNotNull(analysis, "AI analysis should not be null — API call may have failed");
        assertFalse(analysis.isBlank(), "AI analysis should not be empty");

        // The analysis should contain a root cause section. The AI response follows the
        // configured framework language (e.g. English "Root Cause" or Turkish "Kök Neden").
        String normalized = analysis.toLowerCase(java.util.Locale.ROOT);
        assertTrue(normalized.contains("root cause") || normalized.contains("kök neden"),
                "Analysis should contain a root cause section. Got: " + analysis);

        System.out.println("═══════════════════════════════════════════════");
        System.out.println("  AI Failure Analysis (deepseek-v4-flash)");
        System.out.println("═══════════════════════════════════════════════");
        System.out.println(analysis);
        System.out.println("═══════════════════════════════════════════════");
    }

    @Test
    public void promptShouldContainTestContext() {
        // Verify the prompt includes relevant context
        TestTiming timing = ExecutionMetrics.getTiming(TEST_ID);
        String prompt = AiFailureAnalyzer.buildPrompt(timing, "https://www.saucedemo.com/", "Swag Labs");

        assertTrue(prompt.contains("ai-integration-test"), "Prompt should contain test ID");
        assertTrue(prompt.contains("Products"), "Prompt should contain error details");
        assertTrue(prompt.contains("https://www.saucedemo.com/"), "Prompt should contain page URL");
        assertTrue(prompt.contains("Swag Labs"), "Prompt should contain page title");
    }
}
