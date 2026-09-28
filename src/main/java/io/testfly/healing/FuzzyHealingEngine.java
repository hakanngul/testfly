package io.testfly.healing;

import io.testfly.api.TestFlyApi;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A zero-token, pure-Java heuristic matching engine for self-healing.
 * 
 * <p>Uses Levenshtein distance and attribute weighting to score elements on the page
 * against the expected locator clues, providing an instantaneous fallback before 
 * reaching out to external AI providers.
 */
@TestFlyApi(since = "1.9.0")
public final class FuzzyHealingEngine {

    // CSS selector targeting typical interactive elements
    private static final String INTERACTIVE_ELEMENTS = "button, a, input, [role='button'], [role='link'], [tabindex='0']";
    private static final int THRESHOLD_SCORE = 5;

    private FuzzyHealingEngine() {
    }

    /**
     * Calculates the Levenshtein distance between two strings.
     */
    public static int getLevenshteinDistance(String a, String b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Strings must not be null");
        }
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) {
            costs[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }

    /**
     * Attempts to heal a broken locator using fuzzy logic scoring.
     *
     * @param driver          the active WebDriver
     * @param originalLocator the locator that failed
     * @return the best matching WebElement if score >= THRESHOLD_SCORE, otherwise null
     */
    public static WebElement tryFuzzyHeal(WebDriver driver, By originalLocator) {
        if (driver == null || originalLocator == null) {
            return null;
        }

        String desc = originalLocator.toString().toLowerCase();
        List<String> clues = extractClues(desc);

        if (clues.isEmpty()) {
            return null;
        }

        List<WebElement> candidates;
        try {
            candidates = driver.findElements(By.cssSelector(INTERACTIVE_ELEMENTS));
        } catch (Exception e) {
            return null;
        }

        WebElement bestMatch = null;
        int highestScore = 0;

        for (WebElement el : candidates) {
            int score = scoreElement(el, clues);
            if (score > highestScore) {
                highestScore = score;
                bestMatch = el;
            }
        }

        return highestScore >= THRESHOLD_SCORE ? bestMatch : null;
    }

    /**
     * Evaluates a single element against the extracted clues.
     */
    private static int scoreElement(WebElement el, List<String> clues) {
        int score = 0;
        try {
            String text = el.getText() != null ? el.getText().toLowerCase().trim() : "";
            String id = el.getAttribute("id") != null ? el.getAttribute("id").toLowerCase() : "";
            String classes = el.getAttribute("class") != null ? el.getAttribute("class").toLowerCase() : "";
            String placeholder = el.getAttribute("placeholder") != null ? el.getAttribute("placeholder").toLowerCase() : "";
            String value = el.getAttribute("value") != null ? el.getAttribute("value").toLowerCase() : "";

            for (String clue : clues) {
                if (clue.length() < 3) continue; // Skip very short clues

                // Exact attribute matches
                if (id.contains(clue)) score += 5;
                if (classes.contains(clue)) score += 3;
                if (placeholder.contains(clue)) score += 4;
                if (value.contains(clue)) score += 4;

                // Fuzzy text matching
                if (!text.isEmpty()) {
                    if (text.contains(clue)) {
                        score += 5;
                    } else {
                        // Allow 1 typo for words <= 5 chars, 2 typos for longer words
                        int maxDistance = clue.length() <= 5 ? 1 : 2;
                        if (getLevenshteinDistance(text, clue) <= maxDistance) {
                            score += 4;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Stale element or generic webdriver exception during iteration, ignore scoring this element.
        }
        return score;
    }

    /**
     * Extracts meaningful word tokens from a locator string.
     */
    public static List<String> extractClues(String locatorDesc) {
        List<String> clues = new ArrayList<>();
        // Match word characters (alphanumeric), min 3 chars to avoid noise like "div", "span"
        Matcher m = Pattern.compile("[a-zA-Z0-9]{3,}").matcher(locatorDesc);
        
        while (m.find()) {
            String word = m.group();
            // Skip common noise words from locator syntax
            if (!word.equals("cssselector") && !word.equals("xpath") && !word.equals("contains") 
                && !word.equals("text") && !word.equals("data") && !word.equals("testid")) {
                clues.add(word);
            }
        }
        return clues;
    }
}
