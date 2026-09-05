package io.testfly.healing;

import io.testfly.api.TestFlyApi;
import io.testfly.internal.TestFlyContext;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automatic locator fallback engine.
 *
 * <p>When a locator fails, this class analyses the original {@link By} descriptor
 * and generates plausible alternative strategies in order:
 * <ol>
 *   <li><b>id</b> — extracts {@code id} from a CSS selector ({@code #foo}) or
 *       XPath ({@code @id='foo'}) and retries via {@code By.id}.</li>
 *   <li><b>name</b> — extracts {@code name} from CSS ({@code [name='foo']}) or
 *       XPath ({@code @name='foo'}) and retries via {@code By.name}.</li>
 *   <li><b>text</b> — extracts visible text from XPath ({@code text()='foo'} or
 *       {@code contains(text(),'foo')}) and retries via XPath text match.</li>
 *   <li><b>partial CSS</b> — strips tag prefixes from a compound CSS selector
 *       and retries with just the last class or attribute component.</li>
 * </ol>
 *
 * <p>Enable via {@code locators.selfHealing: true} in {@code testfly.yml}.
 *
 * <p>Healing is transparent: the test continues; a {@link HealEvent} is logged
 * to {@link HealLog} and surfaced in the HTML report and
 * {@code target/healed-locators.json}.
 */
@TestFlyApi(since = "1.8.0")
public final class SelfHealingLocator {

    private SelfHealingLocator() {}

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** Returns {@code true} if self-healing is enabled in config. */
    public static boolean isEnabled() {
        try {
            io.testfly.config.TestFlyConfig.Locators loc =
                    TestFlyContext.getConfig().getLocators();
            return loc != null && loc.isSelfHealing();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Attempts to find an element using alternative strategies derived from {@code original}.
     *
     * <p>Checks the {@link HealingCache} first — if this locator was healed in a
     * previous run, the cached result is tried directly (fast path, no fallback chain).
     * On cache miss, the full fallback strategy chain is executed.
     *
     * @param driver   current WebDriver session
     * @param original the failing {@link By} locator
     * @param testId   current test identifier (for heal logging)
     * @return a visible {@link WebElement} found by a fallback strategy, or {@code null} if all fail
     */
    public static WebElement tryHeal(WebDriver driver, By original, String testId) {
        String desc = original.toString();

        // Fast path: check persistent cache first
        String cachedHeal = HealingCache.get(desc);
        if (cachedHeal != null) {
            By cachedBy = parseBy(cachedHeal);
            if (cachedBy != null) {
                WebElement visible = tryFind(driver, cachedBy);
                if (visible != null) {
                    HealEvent event = new HealEvent(testId, desc, cachedHeal, "cache");
                    HealLog.record(event);
                    HealingCache.put(desc, cachedHeal);
                    return visible;
                }
            }
        }

        // Slow path: run fallback strategy chain
        List<FallbackEntry> fallbacks = buildFallbacks(desc);

        for (FallbackEntry fb : fallbacks) {
            WebElement visible = tryFind(driver, fb.by);
            if (visible != null) {
                HealEvent event = new HealEvent(testId, desc, fb.by.toString(), fb.strategy);
                HealLog.record(event);
                HealingCache.put(desc, fb.by.toString());
                return visible;
            }
        }
        return null;
    }

    private static WebElement tryFind(WebDriver driver, By by) {
        try {
            List<WebElement> found = driver.findElements(by);
            return found.stream()
                    .filter(e -> { try { return e.isDisplayed(); } catch (Exception x) { return false; } })
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parses a {@code By.toString()} output back into a {@link By} instance.
     * Handles the common formats: {@code By.id: foo}, {@code By.name: foo},
     * {@code By.className: foo}, {@code By.cssSelector: foo}, {@code By.xpath: foo}.
     */
    private static By parseBy(String desc) {
        if (desc == null || !desc.contains(":")) return null;
        String type = desc.substring(0, desc.indexOf(':')).trim();
        String value = desc.substring(desc.indexOf(':') + 1).trim();
        return switch (type) {
            case "By.id" -> By.id(value);
            case "By.name" -> By.name(value);
            case "By.className" -> By.className(value);
            case "By.cssSelector" -> By.cssSelector(value);
            case "By.xpath" -> By.xpath(value);
            case "By.tagName" -> By.tagName(value);
            case "By.linkText" -> By.linkText(value);
            case "By.partialLinkText" -> By.partialLinkText(value);
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // Fallback builder — pure logic, no browser dependency
    // ------------------------------------------------------------------

    /**
     * Builds an ordered list of fallback {@link By} strategies from a locator description string.
     * Exposed as package-accessible for unit testing.
     */
    public static List<FallbackEntry> buildFallbacks(String desc) {
        List<FallbackEntry> list = new ArrayList<>();

        if (desc == null || desc.isEmpty()) return list;

        boolean isCss   = desc.startsWith("By.cssSelector:");
        boolean isXpath = desc.startsWith("By.xpath:");

        String value = desc.contains(":") ? desc.substring(desc.indexOf(':') + 1).trim() : desc.trim();

        // 1. Extract id -------------------------------------------------------
        if (isCss) {
            // CSS: #login-btn or .container#main or input#email
            Matcher m = Pattern.compile("#([\\w\\-]+)").matcher(value);
            if (m.find()) list.add(new FallbackEntry(By.id(m.group(1)), "id-from-css"));
        }
        if (isXpath) {
            Matcher m = Pattern.compile("@id=['\"]([^'\"]+)['\"]").matcher(value);
            if (m.find()) list.add(new FallbackEntry(By.id(m.group(1)), "id-from-xpath"));
        }

        // 2. Extract name -----------------------------------------------------
        if (isCss) {
            Matcher m = Pattern.compile("\\[name=['\"]([^'\"]+)['\"]\\]").matcher(value);
            if (m.find()) list.add(new FallbackEntry(By.name(m.group(1)), "name-from-css"));
        }
        if (isXpath) {
            Matcher m = Pattern.compile("@name=['\"]([^'\"]+)['\"]").matcher(value);
            if (m.find()) list.add(new FallbackEntry(By.name(m.group(1)), "name-from-xpath"));
        }

        // 3. Extract text (XPath only) ----------------------------------------
        if (isXpath) {
            // text()='exact' or text()="exact"
            Matcher exact = Pattern.compile("text\\(\\)=['\"]([^'\"]+)['\"]").matcher(value);
            if (exact.find()) {
                String text = exact.group(1);
                list.add(new FallbackEntry(
                        By.xpath("//*[normalize-space(text())='" + text.replace("'", "\\'") + "']"),
                        "exact-text-from-xpath"));
            }
            // contains(text(),'partial')
            Matcher contains = Pattern.compile("contains\\(text\\(\\),['\"]([^'\"]+)['\"]\\)").matcher(value);
            if (contains.find()) {
                String text = contains.group(1);
                list.add(new FallbackEntry(
                        By.xpath("//*[contains(normalize-space(text()),'" + text.replace("'", "\\'") + "')]"),
                        "contains-text-from-xpath"));
            }
        }

        // 4. Last class segment from CSS (.submit-btn → .submit-btn) ----------
        if (isCss && value.contains(".")) {
            // Strip any tag prefix; keep the last .classname segment
            Matcher m = Pattern.compile("\\.([\\w\\-]+)(?:[^\\w\\-]|$)").matcher(value);
            String lastClass = null;
            while (m.find()) lastClass = m.group(1);
            if (lastClass != null && !lastClass.isEmpty()) {
                list.add(new FallbackEntry(By.className(lastClass), "class-from-css"));
            }
        }

        // 5. data-testid attribute from CSS ([data-testid='foo']) --------------
        if (isCss) {
            Matcher m = Pattern.compile("\\[data-testid=['\"]([^'\"]+)['\"]\\]").matcher(value);
            if (m.find()) list.add(new FallbackEntry(
                    By.cssSelector("[data-testid='" + m.group(1) + "']"), "data-testid-from-css"));
        }

        // 6. placeholder attribute from CSS -----------------------------------
        if (isCss) {
            Matcher m = Pattern.compile("\\[placeholder=['\"]([^'\"]+)['\"]\\]").matcher(value);
            if (m.find()) list.add(new FallbackEntry(
                    By.cssSelector("[placeholder='" + m.group(1) + "']"), "placeholder-from-css"));
        }

        return list;
    }

    // ------------------------------------------------------------------
    // Inner value type
    // ------------------------------------------------------------------

    public record FallbackEntry(By by, String strategy) {
    }
}
