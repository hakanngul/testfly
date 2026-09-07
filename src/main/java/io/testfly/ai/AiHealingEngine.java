package io.testfly.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.api.TestFlyApi;
import io.testfly.config.TestFlyConfig;
import io.testfly.healing.HealEvent;
import io.testfly.healing.HealLog;
import io.testfly.healing.HealingCache;
import io.testfly.internal.TestFlyContext;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.logging.Logger;

/**
 * AI-driven locator self-healing engine.
 *
 * <p>When static locator fallback strategies fail, this engine sends the pruned DOM
 * and context of the failing locator to an LLM (Claude, Gemini, DeepSeek, OpenAI)
 * to synthesize an accurate replacement selector.
 */
@TestFlyApi(since = "1.9.0")
public final class AiHealingEngine {

    private static final Logger LOG = Logger.getLogger(AiHealingEngine.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiHealingEngine() {}

    /**
     * Attempts to heal a failing locator using LLM intelligence.
     *
     * @param driver          active WebDriver session
     * @param originalLocator the failing {@link By} locator
     * @param testId          current test identifier
     * @return a visible {@link WebElement} if successfully healed, or {@code null}
     */
    public static WebElement heal(WebDriver driver, By originalLocator, String testId) {
        if (driver == null || originalLocator == null) {
            return null;
        }

        try {
            TestFlyConfig config = TestFlyContext.getConfig();
            if (config == null) {
                return null;
            }

            TestFlyConfig.Locators locators = config.getLocators();
            if (locators == null || !locators.isAiHealing()) {
                return null;
            }

            TestFlyConfig.Ai aiCfg = config.getAi();
            if (aiCfg == null) {
                LOG.fine("[AiHealingEngine] ai configuration is missing. Skipping AI healing.");
                return null;
            }

            String apiKey = AiFailureAnalyzer.resolveApiKey(aiCfg.getApiKey());
            if (apiKey == null || apiKey.isBlank()) {
                LOG.fine("[AiHealingEngine] ai.apiKey is not configured. Skipping AI healing.");
                return null;
            }

            AiProvider provider = AiProviderRegistry.get(aiCfg.getProvider(), aiCfg.getBaseUrl());
            if (provider == null) {
                LOG.warning("[AiHealingEngine] Unknown ai.provider: " + aiCfg.getProvider());
                return null;
            }

            int maxTokens = locators.getMaxDomTokens() > 0 ? locators.getMaxDomTokens() : DomPruner.DEFAULT_MAX_TOKENS;
            String prunedDom = DomPruner.prune(driver, maxTokens);
            if (prunedDom.isBlank()) {
                LOG.fine("[AiHealingEngine] Pruned DOM is empty. Skipping AI healing.");
                return null;
            }

            String currentUrl = "";
            String currentTitle = "";
            try {
                currentUrl = driver.getCurrentUrl();
                currentTitle = driver.getTitle();
            } catch (Exception ignored) {}

            String prompt = buildPrompt(originalLocator.toString(), currentUrl, currentTitle, prunedDom);
            String response = provider.call(apiKey, aiCfg.getModel(), prompt, aiCfg.getTimeoutSeconds());

            if (response == null || response.isBlank()) {
                LOG.fine("[AiHealingEngine] Received empty response from AI provider.");
                return null;
            }

            By healedBy = parseHealedLocator(response);
            if (healedBy == null) {
                LOG.fine("[AiHealingEngine] Could not parse a valid locator from AI response.");
                return null;
            }

            WebElement element = tryFindVisible(driver, healedBy);
            if (element != null) {
                String originalDesc = originalLocator.toString();
                String healedDesc = healedBy.toString();
                HealEvent event = new HealEvent(testId, originalDesc, healedDesc, "ai-healed");
                HealLog.record(event);
                HealingCache.put(originalDesc, healedDesc);
                LOG.info("[AiHealingEngine] Successfully healed locator: " + originalDesc + " -> " + healedDesc);
                return element;
            } else {
                LOG.fine("[AiHealingEngine] AI suggested locator (" + healedBy + ") was not visible on the page.");
            }

        } catch (Exception e) {
            LOG.warning("[AiHealingEngine] AI healing failed (non-critical): " + e.getMessage());
        }

        return null;
    }

    /**
     * Builds the structured repair prompt for the LLM.
     */
    public static String buildPrompt(String originalLocator, String url, String title, String prunedDom) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert QA automation locator repair assistant.\n");
        sb.append("A Selenium locator in an automated test failed to locate the expected element.\n\n");

        sb.append("## Context\n");
        sb.append("- Original Locator: ").append(originalLocator).append("\n");
        if (url != null && !url.isBlank()) {
            sb.append("- Page URL: ").append(url).append("\n");
        }
        if (title != null && !title.isBlank()) {
            sb.append("- Page Title: ").append(title).append("\n");
        }

        sb.append("\n## Cleaned DOM Snapshot\n```html\n")
          .append(prunedDom)
          .append("\n```\n\n");

        sb.append("## Task\n");
        sb.append("Identify the element that corresponds to the original intention of the failing locator.\n");
        sb.append("Respond ONLY with a JSON object in this exact schema (no additional markdown or prose):\n");
        sb.append("{\n");
        sb.append("  \"type\": \"cssSelector\" | \"xpath\" | \"id\" | \"name\",\n");
        sb.append("  \"value\": \"the selector string\",\n");
        sb.append("  \"confidence\": 0.95,\n");
        sb.append("  \"reason\": \"brief explanation of why this element matches\"\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Parses the LLM text response into a Selenium {@link By}.
     */
    public static By parseHealedLocator(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }

        String json = rawResponse.trim();
        if (json.startsWith("```")) {
            // Strip markdown fences
            int firstNewline = json.indexOf('\n');
            int lastFence = json.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                json = json.substring(firstNewline + 1, lastFence).trim();
            }
        }

        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.has("type") || !root.has("value")) {
                return null;
            }

            String type = root.get("type").asText().trim();
            String value = root.get("value").asText().trim();

            if (value.isEmpty()) {
                return null;
            }

            return switch (type.toLowerCase()) {
                case "css", "cssselector", "by.cssselector" -> By.cssSelector(value);
                case "xpath", "by.xpath" -> By.xpath(value);
                case "id", "by.id" -> By.id(value);
                case "name", "by.name" -> By.name(value);
                case "classname", "by.classname" -> By.className(value);
                case "linktext", "by.linktext" -> By.linkText(value);
                default -> {
                    // If type is unknown, infer from value
                    if (value.startsWith("//") || value.startsWith(".//") || value.startsWith("(")) {
                        yield By.xpath(value);
                    } else {
                        yield By.cssSelector(value);
                    }
                }
            };
        } catch (Exception e) {
            LOG.fine("[AiHealingEngine] Failed to parse JSON from AI response: " + e.getMessage());
            return null;
        }
    }

    private static WebElement tryFindVisible(WebDriver driver, By by) {
        try {
            List<WebElement> found = driver.findElements(by);
            return found.stream()
                    .filter(e -> {
                        try {
                            return e.isDisplayed();
                        } catch (Exception x) {
                            return false;
                        }
                    })
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
