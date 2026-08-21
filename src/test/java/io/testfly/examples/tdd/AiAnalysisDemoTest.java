package io.testfly.examples.tdd;

import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Intentionally failing test to demonstrate AI Failure Analysis.
 *
 * <p>When this test fails, the framework calls DeepSeek (deepseek-v4-flash)
 * to analyze the failure. The result appears in the HTML report
 * ({@code target/testfly-report.html}) under the "🤖 AI Failure Analysis" panel.
 *
 * <p>Run with:
 * <pre>mvn test -Dtest=io.testfly.examples.tdd.AiAnalysisDemoTest</pre>
 *
 * <p>Then open: {@code target/testfly-report.html}
 */
public class AiAnalysisDemoTest extends BaseTest {

    @Test
    public void intentionalFailureToTriggerAiAnalysis() {
        open();

        // This assertion will fail — the AI will analyze why
        assertEquals(getDriver().getTitle(), "Nonexistent Page",
                "This assertion intentionally fails to trigger AI analysis");
    }
}
