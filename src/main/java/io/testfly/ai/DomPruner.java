package io.testfly.ai;

import io.testfly.api.TestFlyApi;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optimizes and prunes web DOM content for AI/LLM consumption.
 *
 * <p>Reduces token usage by removing scripts, styles, inline SVGs, comments,
 * and decorative markup, while preserving semantic attributes (id, name,
 * data-testid, role, aria-*, etc.) and visible interactive elements.
 */
@TestFlyApi(since = "1.9.0")
public final class DomPruner {

    public static final int DEFAULT_MAX_TOKENS = 8000;
    private static final int CHARS_PER_TOKEN = 4;

    private static final Pattern SCRIPT_PATTERN =
            Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern STYLE_PATTERN =
            Pattern.compile("<style[^>]*>.*?</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SVG_PATTERN =
            Pattern.compile("<svg[^>]*>.*?</svg>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern NOSCRIPT_PATTERN =
            Pattern.compile("<noscript[^>]*>.*?</noscript>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern IFRAME_PATTERN =
            Pattern.compile("<iframe[^>]*>.*?</iframe>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern LINK_PATTERN =
            Pattern.compile("<link[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT_PATTERN =
            Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern STYLE_ATTR_PATTERN =
            Pattern.compile("\\sstyle=([\"'][^\"']*[\"']|[^\\s>]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ON_EVENT_PATTERN =
            Pattern.compile("\\son[a-zA-Z]+=([\"'][^\"']*[\"']|[^\\s>]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MULTI_SPACE_PATTERN =
            Pattern.compile("[ \\t\\r\\n]+");

    private DomPruner() {}

    /**
     * Extracts and prunes the current page DOM using the provided WebDriver session.
     *
     * @param driver active WebDriver session
     * @return pruned HTML string
     */
    public static String prune(WebDriver driver) {
        return prune(driver, DEFAULT_MAX_TOKENS);
    }

    /**
     * Extracts and prunes the current page DOM with a specific token budget.
     *
     * @param driver    active WebDriver session
     * @param maxTokens maximum allowed tokens in the result
     * @return pruned HTML string
     */
    public static String prune(WebDriver driver, int maxTokens) {
        if (driver == null) {
            return "";
        }

        // Fast path: try in-browser DOM extraction via JavaScript if supported
        if (driver instanceof JavascriptExecutor js) {
            try {
                Object result = js.executeScript(
                        "try {" +
                        "  var clone = document.body ? document.body.cloneNode(true) : document.documentElement.cloneNode(true);" +
                        "  var removeSelectors = 'script, style, svg, noscript, iframe, link, meta';" +
                        "  var elements = clone.querySelectorAll(removeSelectors);" +
                        "  for (var i = 0; i < elements.length; i++) { elements[i].remove(); }" +
                        "  return clone.innerHTML;" +
                        "} catch(e) { return null; }"
                );
                if (result instanceof String s && !s.isBlank()) {
                    return prune(s, maxTokens);
                }
            } catch (Exception ignored) {
                // Fall back to getPageSource
            }
        }

        try {
            String pageSource = driver.getPageSource();
            return prune(pageSource, maxTokens);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Prunes a raw HTML string using default token budget.
     *
     * @param rawHtml raw HTML string
     * @return pruned HTML string
     */
    public static String prune(String rawHtml) {
        return prune(rawHtml, DEFAULT_MAX_TOKENS);
    }

    /**
     * Prunes a raw HTML string and truncates if it exceeds {@code maxTokens}.
     *
     * @param rawHtml   raw HTML string
     * @param maxTokens maximum token budget (~4 characters per token)
     * @return pruned HTML string
     */
    public static String prune(String rawHtml, int maxTokens) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return "";
        }

        String cleaned = rawHtml;

        // 1. Strip bulky and non-interactive tags
        cleaned = SCRIPT_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = STYLE_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = SVG_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = NOSCRIPT_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = IFRAME_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = LINK_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = COMMENT_PATTERN.matcher(cleaned).replaceAll("");

        // 2. Strip inline styles and event handlers
        cleaned = STYLE_ATTR_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = ON_EVENT_PATTERN.matcher(cleaned).replaceAll("");

        // 3. Normalize multiple whitespace characters
        cleaned = MULTI_SPACE_PATTERN.matcher(cleaned).replaceAll(" ").trim();

        // 4. Truncate to token budget if needed
        int maxChars = Math.max(256, maxTokens * CHARS_PER_TOKEN);
        if (cleaned.length() > maxChars) {
            cleaned = cleaned.substring(0, maxChars) + "\n<!-- [DOM truncated to fit token budget] -->";
        }

        return cleaned;
    }
}
