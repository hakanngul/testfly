package io.testfly.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.ai.AiFailureAnalyzer;
import io.testfly.ai.AiProvider;
import io.testfly.ai.AiProviderRegistry;
import io.testfly.ai.DomPruner;
import io.testfly.api.TestFlyApi;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.steps.StepLogger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Compiles high-level natural language goals into sequential, deterministic Selenium steps
 * with "Compile &amp; Freeze" caching.
 */
@TestFlyApi(since = "1.9.0")
public final class ActionCompiler {

    private static final Logger LOG = Logger.getLogger(ActionCompiler.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ActionCompiler() {}

    /**
     * Executes the natural language goal on the driver, compiling via AI or replaying from cache.
     *
     * @param driver active WebDriver session
     * @param goal   natural language goal (e.g. "Click delete on first item in cart")
     */
    public static void execute(WebDriver driver, String goal) {
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("Action goal cannot be null or empty");
        }

        TestFlyConfig config = TestFlyContext.getConfig();
        int explicitSeconds = (config != null && config.getTimeouts() != null)
                ? config.getTimeouts().getExplicit() : 10;
        Duration timeout = Duration.ofSeconds(explicitSeconds);

        ActionPlan plan = compile(driver, goal);
        try {
            ActionExecutor.execute(driver, plan, timeout);
        } catch (Exception e) {
            LOG.warning("[ActionCompiler] Action execution failed for goal: \"" + goal + "\". Invalidating cache and retrying... Reason: " + e.getMessage());
            // Invalidate cache and retry once
            String currentUrl = "";
            try {
                if (driver != null) currentUrl = driver.getCurrentUrl();
            } catch (Exception ignored) {}

            ActionCache.invalidate(currentUrl, goal);
            ActionPlan freshPlan = compileFromAi(driver, goal, config);
            ActionExecutor.execute(driver, freshPlan, timeout);
            if (freshPlan != null && !freshPlan.steps().isEmpty()) {
                ActionCache.put(currentUrl, goal, freshPlan);
            }
        }
    }

    /**
     * Compiles a goal into an ActionPlan, consulting ActionCache first.
     */
    public static ActionPlan compile(WebDriver driver, String goal) {
        TestFlyConfig config = TestFlyContext.getConfig();
        boolean useCache = config == null || config.getAi() == null || config.getAi().isActionCache();

        String currentUrl = "";
        try {
            if (driver != null) currentUrl = driver.getCurrentUrl();
        } catch (Exception ignored) {}

        if (useCache) {
            ActionPlan cached = ActionCache.get(currentUrl, goal);
            if (cached != null) {
                LOG.info("[ActionCompiler] ActionCache HIT for goal: \"" + goal + "\"");
                StepLogger.step("Replaying frozen AI action plan for goal: \"" + goal + "\"");
                return cached;
            }
        }

        LOG.info("[ActionCompiler] ActionCache MISS for goal: \"" + goal + "\". Compiling via AI...");
        ActionPlan compiled = compileFromAi(driver, goal, config);

        if (useCache && compiled != null && !compiled.steps().isEmpty()) {
            ActionCache.put(currentUrl, goal, compiled);
        }

        return compiled;
    }

    /**
     * Compiles an ActionPlan directly from the AI provider.
     */
    public static ActionPlan compileFromAi(WebDriver driver, String goal, TestFlyConfig config) {
        if (config == null || config.getAi() == null) {
            throw new IllegalStateException("ai configuration block is missing in testfly.yml");
        }

        TestFlyConfig.Ai aiCfg = config.getAi();
        String apiKey = AiFailureAnalyzer.resolveApiKey(aiCfg.getApiKey());
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ai.apiKey is not configured");
        }

        AiProvider provider = AiProviderRegistry.get(aiCfg.getProvider(), aiCfg.getBaseUrl());
        if (provider == null) {
            throw new IllegalStateException("Unknown ai.provider: " + aiCfg.getProvider());
        }

        String currentUrl = "";
        String currentTitle = "";
        if (driver != null) {
            try {
                currentUrl = driver.getCurrentUrl();
                currentTitle = driver.getTitle();
            } catch (Exception ignored) {}
        }

        int maxTokens = (config.getLocators() != null) ? config.getLocators().getMaxDomTokens() : 8000;
        String prunedDom = DomPruner.prune(driver, maxTokens);

        String prompt = buildPrompt(currentUrl, currentTitle, prunedDom, goal);
        String response = provider.call(apiKey, aiCfg.getModel(), prompt, aiCfg.getTimeoutSeconds());

        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Empty response from AI provider while compiling goal: " + goal);
        }

        return parsePlan(response, goal, currentUrl);
    }

    /**
     * Builds the structured evaluation prompt for the LLM.
     */
    public static String buildPrompt(String url, String title, String html, String goal) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an automated web QA action compiler.\n");
        sb.append("Given the current page state, compile the following user goal into an ordered sequence of executable actions.\n\n");

        sb.append("## Page Context\n");
        if (url != null && !url.isBlank()) sb.append("- URL:   ").append(url).append("\n");
        if (title != null && !title.isBlank()) sb.append("- Title: ").append(title).append("\n");

        sb.append("\n## Pruned DOM Content\n```html\n")
          .append(html != null ? html : "")
          .append("\n```\n\n");

        sb.append("## Goal\n\"").append(goal).append("\"\n\n");

        sb.append("## Available Action Types\n");
        sb.append("- CLICK: click element (locator)\n");
        sb.append("- TYPE: type text into input/textarea (locator, value)\n");
        sb.append("- CLEAR: clear text input (locator)\n");
        sb.append("- HOVER: mouse hover over element (locator)\n");
        sb.append("- WAIT_VISIBLE: wait for element (locator)\n");
        sb.append("- PRESS_ENTER: press enter key on element (locator)\n\n");

        sb.append("## Schema\n");
        sb.append("Respond ONLY with a JSON object in this exact schema (no additional prose or markdown fences):\n");
        sb.append("{\n");
        sb.append("  \"goal\": \"").append(goal).append("\",\n");
        sb.append("  \"steps\": [\n");
        sb.append("    {\n");
        sb.append("      \"action\": \"CLICK\",\n");
        sb.append("      \"locator\": \"CSS selector (e.g. #submit-btn, .cart-icon) or XPath without 'css=' prefix\",\n");
        sb.append("      \"value\": null,\n");
        sb.append("      \"description\": \"Description of step\"\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Parses the LLM JSON response into an {@link ActionPlan}.
     */
    public static ActionPlan parsePlan(String rawResponse, String goal, String url) {
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
            List<ActionStep> steps = new ArrayList<>();
            JsonNode stepsNode = root.get("steps");
            if (stepsNode != null && stepsNode.isArray()) {
                for (JsonNode stepNode : stepsNode) {
                    String actionStr = stepNode.has("action") ? stepNode.get("action").asText() : "CLICK";
                    ActionType actionType = ActionType.valueOf(actionStr.toUpperCase());
                    String locator = stepNode.has("locator") ? stepNode.get("locator").asText() : "";
                    String value = (stepNode.has("value") && !stepNode.get("value").isNull())
                            ? stepNode.get("value").asText() : null;
                    String description = stepNode.has("description") ? stepNode.get("description").asText() : "";
                    steps.add(new ActionStep(actionType, locator, value, description));
                }
            }
            return new ActionPlan(goal, url, steps, System.currentTimeMillis());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse AI action plan: " + rawResponse, e);
        }
    }

    /**
     * Resolves a semantic element intent (e.g. "shopping cart icon") to a Selenium By locator.
     */
    public static By resolveIntent(WebDriver driver, String intent) {
        if (intent == null || intent.isBlank()) {
            throw new IllegalArgumentException("Intent cannot be null or empty");
        }
        ActionPlan plan = compile(driver, "Find " + intent);
        if (plan != null && !plan.steps().isEmpty()) {
            return ActionExecutor.parseLocator(plan.steps().get(0).locator());
        }
        throw new IllegalStateException("Could not resolve semantic intent to locator: " + intent);
    }
}
