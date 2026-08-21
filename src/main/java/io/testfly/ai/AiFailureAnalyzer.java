package io.testfly.ai;

import io.testfly.api.TestFlyApi;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.metrics.ExecutionMetrics;
import io.testfly.metrics.TestTiming;
import io.testfly.steps.StepRecord;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.logging.Logger;

/**
 * Calls an LLM to generate a plain-English failure analysis for a failed test.
 *
 * <p>Activated when {@code ai.failureAnalysis: true} and {@code ai.apiKey} are set in
 * {@code testfly.yml}. The analysis is stored in the test metrics and surfaced in the
 * HTML report below the stack trace.
 *
 * <h3>Supported providers</h3>
 * <pre>
 * # Claude (default)
 * ai:
 *   failureAnalysis: true
 *   provider: claude
 *   apiKey: ${CLAUDE_API_KEY}
 *   model: claude-haiku-4-5-20251001
 *
 * # DeepSeek (~$0.14/1M tokens — cheapest option)
 * ai:
 *   failureAnalysis: true
 *   provider: openai-compatible
 *   baseUrl: https://api.deepseek.com
 *   apiKey: ${DEEPSEEK_API_KEY}
 *   model: deepseek-chat
 *
 * # Google Gemini Flash
 * ai:
 *   failureAnalysis: true
 *   provider: openai-compatible
 *   baseUrl: https://generativelanguage.googleapis.com/v1beta/openai
 *   apiKey: ${GEMINI_API_KEY}
 *   model: gemini-2.0-flash
 *
 * # Local Ollama (free, no API key needed)
 * ai:
 *   failureAnalysis: true
 *   provider: openai-compatible
 *   baseUrl: http://localhost:11434
 *   apiKey: ollama
 *   model: llama3.2
 * </pre>
 *
 * <p>The API call is bounded by {@code ai.timeoutSeconds} (default 20s).
 * Any failure (network error, API error, timeout) is silently suppressed — the test suite
 * result is never affected by the AI analysis step.
 */
@TestFlyApi(since = "1.8.0")
public final class AiFailureAnalyzer {

    private static final Logger LOG = Logger.getLogger(AiFailureAnalyzer.class.getName());
    private static final org.slf4j.Logger SLF4J_LOG = LoggerFactory.getLogger(AiFailureAnalyzer.class);

    private AiFailureAnalyzer() {}

    // ------------------------------------------------------------------
    // Public API — called by TestExecutionListener, TestFlyExtension, CucumberHooks
    // ------------------------------------------------------------------

    /**
     * Analyses the failure of {@code testId} and records the result via
     * {@link ExecutionMetrics#recordAiAnalysis}.
     *
     * @param testId    fully-qualified test method name
     * @param pageUrl   current page URL at the time of failure (may be null)
     * @param pageTitle current page title (may be null)
     */
    public static void analyze(String testId, String pageUrl, String pageTitle) {
        try {
            TestFlyConfig.Ai aiCfg = config();
            if (aiCfg == null || !aiCfg.isFailureAnalysis()) return;

            String apiKey = resolveApiKey(aiCfg.getApiKey());
            if (apiKey == null || apiKey.isEmpty()) {
                LOG.warning("[AiFailureAnalyzer] ai.apiKey is not configured. Skipping analysis.");
                return;
            }

            AiProvider provider = AiProviderRegistry.get(aiCfg.getProvider(), aiCfg.getBaseUrl());
            if (provider == null) {
                LOG.warning("[AiFailureAnalyzer] Unknown ai.provider: " + aiCfg.getProvider()
                        + ". Available: " + AiProviderRegistry.availableProviders());
                return;
            }

            TestTiming timing = ExecutionMetrics.getTiming(testId);
            if (timing == null) return;

            String prompt = buildPrompt(timing, pageUrl, pageTitle);
            String analysis = provider.call(apiKey, aiCfg.getModel(), prompt, aiCfg.getTimeoutSeconds());
            if (analysis != null && !analysis.isBlank()) {
                String cleanAnalysis = analysis.strip();
                ExecutionMetrics.recordAiAnalysis(testId, cleanAnalysis);
                LOG.info("[AiFailureAnalyzer] Analysis recorded for: " + testId
                        + " (provider: " + provider.name() + ", model: " + aiCfg.getModel() + ")");

                // Log via SLF4J so ReportPortal logback appender picks it up
                SLF4J_LOG.info("🤖 AI Failure Analysis for [{}]:\n{}", testId, cleanAnalysis);
            }
        } catch (Exception e) {
            LOG.warning("[AiFailureAnalyzer] Analysis failed (non-critical): " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Prompt construction
    // ------------------------------------------------------------------

    public static String buildPrompt(TestTiming timing, String pageUrl, String pageTitle) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a QA automation expert. A Selenium WebDriver test just failed.\n\n");

        sb.append("## Test Context\n");
        sb.append("- Test:     ").append(nvl(timing.getTestId(), "unknown")).append("\n");
        sb.append("- Class:    ").append(nvl(timing.getTestClassName(), "unknown")).append("\n");
        sb.append("- Browser:  ").append(nvl(timing.getBrowser(), "unknown")).append("\n");
        sb.append("- Duration: ").append(timing.getTotalTime()).append("ms\n");
        if (pageUrl   != null) sb.append("- URL:      ").append(pageUrl).append("\n");
        if (pageTitle != null) sb.append("- Title:    ").append(pageTitle).append("\n");

        if (timing.getErrorMessage() != null) {
            sb.append("\n## Error Message\n```\n")
              .append(timing.getErrorMessage()).append("\n```\n");
        }

        if (timing.getStackTrace() != null) {
            String[] lines = timing.getStackTrace().split("\n");
            int limit = Math.min(lines.length, 30);
            sb.append("\n## Stack Trace (first ").append(limit).append(" lines)\n```\n");
            for (int i = 0; i < limit; i++) sb.append(lines[i]).append("\n");
            sb.append("```\n");
        }

        List<StepRecord> steps = timing.getSteps();
        if (!steps.isEmpty()) {
            sb.append("\n## Steps Executed\n");
            for (StepRecord s : steps) {
                sb.append("- [+").append(s.getOffsetMs()).append("ms] ")
                  .append(s.getStatus()).append(": ").append(s.getName()).append("\n");
            }
        }

        sb.append("\n## Your Task\n");
        sb.append("Provide a concise failure analysis in this exact format:\n\n");
        sb.append("**Root Cause:** (1-2 sentences explaining what likely went wrong)\n\n");
        sb.append("**Suggested Fix:**\n- (bullet 1)\n- (bullet 2, if needed)\n\n");
        sb.append("Be specific and actionable. Do not repeat the error message verbatim.");

        // Language instruction
        String lang = config() != null ? config().getLanguage() : "en";
        if (lang != null && !lang.isBlank() && !"en".equalsIgnoreCase(lang)) {
            sb.append("\n\n**IMPORTANT:** Write your entire response in ")
              .append(resolveLanguageName(lang)).append(".");
        }

        return sb.toString();
    }

    /**
     * Maps language codes to human-readable names for the AI prompt.
     */
    private static String resolveLanguageName(String code) {
        return switch (code.toLowerCase()) {
            case "tr", "tur" -> "Turkish";
            case "de", "deu" -> "German";
            case "fr", "fra" -> "French";
            case "es", "spa" -> "Spanish";
            case "pt", "por" -> "Portuguese";
            case "it", "ita" -> "Italian";
            case "ru", "rus" -> "Russian";
            case "ja", "jpn" -> "Japanese";
            case "zh", "zho" -> "Chinese";
            case "ko", "kor" -> "Korean";
            case "ar", "ara" -> "Arabic";
            default -> code;
        };
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static TestFlyConfig.Ai config() {
        try { return TestFlyContext.getConfig().getAi(); } catch (Exception e) { return null; }
    }

    private static String resolveApiKey(String raw) {
        if (raw == null) return null;
        if (raw.startsWith("${") && raw.endsWith("}")) {
            String var = raw.substring(2, raw.length() - 1);
            String val = System.getenv(var);
            return val != null ? val : System.getProperty(var);
        }
        return raw;
    }

    private static String nvl(String s, String def) {
        return s != null && !s.isEmpty() ? s : def;
    }
}
