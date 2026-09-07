package io.testfly.assertion.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.ai.AiFailureAnalyzer;
import io.testfly.ai.AiProvider;
import io.testfly.ai.AiProviderRegistry;
import io.testfly.api.TestFlyApi;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import org.openqa.selenium.WebDriver;

import java.util.logging.Logger;

/**
 * Core engine for AI-driven semantic assertions.
 *
 * <p>Evaluates whether a web page DOM or element sub-tree satisfies or violates
 * natural language expectations using LLM reasoning.
 */
@TestFlyApi(since = "1.9.0")
public final class AiAssertEngine {

    private static final Logger LOG = Logger.getLogger(AiAssertEngine.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiAssertEngine() {}

    /**
     * Verifies whether the provided HTML context satisfies or violates the condition.
     *
     * @param driver            active WebDriver session (for title/url metadata)
     * @param contextHtml       pruned HTML of the page or element
     * @param condition         natural language description of the expectation
     * @param expectSatisfaction {@code true} for satisfies (positive), {@code false} for violates (negative)
     * @return {@link AiAssertionResult} containing pass status, confidence, and reason
     */
    public static AiAssertionResult verify(WebDriver driver, String contextHtml, String condition, boolean expectSatisfaction) {
        if (condition == null || condition.isBlank()) {
            return new AiAssertionResult(false, 0.0, "Condition statement is empty or null");
        }

        try {
            TestFlyConfig config = TestFlyContext.getConfig();
            if (config == null || config.getAi() == null) {
                return new AiAssertionResult(false, 0.0, "ai configuration block is missing in testfly.yml");
            }

            TestFlyConfig.Ai aiCfg = config.getAi();
            String apiKey = AiFailureAnalyzer.resolveApiKey(aiCfg.getApiKey());
            if (apiKey == null || apiKey.isBlank()) {
                return new AiAssertionResult(false, 0.0, "ai.apiKey is not configured");
            }

            AiProvider provider = AiProviderRegistry.get(aiCfg.getProvider(), aiCfg.getBaseUrl());
            if (provider == null) {
                return new AiAssertionResult(false, 0.0, "Unknown ai.provider: " + aiCfg.getProvider());
            }

            String currentUrl = "";
            String currentTitle = "";
            if (driver != null) {
                try {
                    currentUrl = driver.getCurrentUrl();
                    currentTitle = driver.getTitle();
                } catch (Exception ignored) {}
            }

            String prompt = buildPrompt(currentUrl, currentTitle, contextHtml, condition, expectSatisfaction);
            String response = provider.call(apiKey, aiCfg.getModel(), prompt, aiCfg.getTimeoutSeconds());

            if (response == null || response.isBlank()) {
                return new AiAssertionResult(false, 0.0, "Empty response from AI provider");
            }

            return parseResult(response, expectSatisfaction);

        } catch (Exception e) {
            LOG.warning("[AiAssertEngine] AI assertion failed: " + e.getMessage());
            return new AiAssertionResult(false, 0.0, "Assertion execution error: " + e.getMessage());
        }
    }

    /**
     * Constructs the structured evaluation prompt.
     */
    public static String buildPrompt(String url, String title, String html, String condition, boolean expectSatisfaction) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert automated QA semantic validator.\n");
        sb.append("Evaluate whether the following web page/element state satisfies the natural language requirement.\n\n");

        sb.append("## Page Context\n");
        if (url != null && !url.isBlank()) sb.append("- URL:   ").append(url).append("\n");
        if (title != null && !title.isBlank()) sb.append("- Title: ").append(title).append("\n");

        sb.append("\n## DOM / Element Content\n```html\n")
          .append(html != null ? html : "")
          .append("\n```\n\n");

        sb.append("## Requirement\n");
        if (expectSatisfaction) {
            sb.append("Verify that the content SATISFIES this condition: \"").append(condition).append("\"\n");
        } else {
            sb.append("Verify that the content DOES NOT CONTAIN or VIOLATES this condition: \"").append(condition).append("\"\n");
            sb.append("If this forbidden condition is present or observed, test fails.\n");
        }

        sb.append("\n## Task\n");
        sb.append("Respond ONLY with a JSON object in this exact schema (no additional prose or markdown):\n");
        sb.append("{\n");
        sb.append("  \"passed\": true,\n");
        sb.append("  \"confidence\": 0.95,\n");
        sb.append("  \"reason\": \"Brief explanation of your evaluation\"\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Parses the LLM JSON response.
     */
    public static AiAssertionResult parseResult(String rawResponse, boolean expectSatisfaction) {
        String json = rawResponse.trim();
        if (json.startsWith("```")) {
            int firstNewline = json.indexOf('\n');
            int lastFence = json.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                json = json.substring(firstNewline + 1, lastFence).trim();
            }
        }

        try {
            JsonNode root = MAPPER.readTree(json);
            boolean passed = root.has("passed") && root.get("passed").asBoolean();
            double confidence = root.has("confidence") ? root.get("confidence").asDouble() : 1.0;
            String reason = root.has("reason") ? root.get("reason").asText() : "";

            return new AiAssertionResult(passed, confidence, reason);
        } catch (Exception e) {
            LOG.fine("[AiAssertEngine] Could not parse AI response as JSON: " + e.getMessage());
            return new AiAssertionResult(false, 0.0, "Malformed AI response: " + rawResponse);
        }
    }

    /**
     * Evaluation result of a semantic assertion.
     */
    public record AiAssertionResult(boolean isPassed, double confidence, String reason) {}
}
