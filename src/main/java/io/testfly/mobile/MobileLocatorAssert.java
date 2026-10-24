package io.testfly.mobile;

import io.testfly.mobile.internal.LocatorEngine.TextMatch;
import io.testfly.mobile.internal.Poller;
import io.testfly.mobile.internal.Poller.Outcome;
import io.testfly.mobile.internal.Poller.Step;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Assertions on one {@link MobileLocator}; create with {@link MobileAssertions#expect(MobileLocator)}.
 * Single-element assertions apply strict mode; {@link #toHaveCount(int)} and {@link #toBeHidden()}
 * look at every match.
 */
public final class MobileLocatorAssert {

    private final MobileLocator locator;
    private final boolean negated;
    private final String message;
    private final Duration timeout;

    MobileLocatorAssert(MobileLocator locator, boolean negated, String message) {
        this(locator, negated, message, locator.timeout());
    }

    private MobileLocatorAssert(MobileLocator locator, boolean negated, String message, Duration timeout) {
        this.locator = locator;
        this.negated = negated;
        this.message = message;
        this.timeout = timeout;
    }

    /** The negated assertion: {@code expect(x).not().toBeVisible()}. */
    public MobileLocatorAssert not() {
        return new MobileLocatorAssert(locator, !negated, message, timeout);
    }

    /** Another retry budget for this assertion. */
    public MobileLocatorAssert withTimeout(Duration timeout) {
        return new MobileLocatorAssert(locator, negated, message, Objects.requireNonNull(timeout, "timeout"));
    }

    // ── state ───────────────────────────────────────────────────────────

    /** Exactly one visible match. Negated: no visible match (absent counts as not visible). */
    public void toBeVisible() {
        if (negated) {
            all("toBeVisible", nodes -> nodes.stream().anyMatch(MobileNode::visible), "a match is visible");
        } else {
            single("toBeVisible", MobileNode::visible, "the element is not visible");
        }
    }

    /** No visible match (absent counts as hidden). Negated: same as {@code toBeVisible()}. */
    public void toBeHidden() {
        not().toBeVisible();
    }

    public void toBeEnabled() {
        single("toBeEnabled", MobileNode::enabled, negated ? "the element is enabled" : "the element is disabled");
    }

    public void toBeDisabled() {
        single("toBeDisabled", n -> !n.enabled(), negated ? "the element is disabled" : "the element is enabled");
    }

    public void toBeSelected() {
        single("toBeSelected", MobileNode::selected, negated ? "the element is selected" : "the element is not selected");
    }

    public void toBeChecked() {
        single("toBeChecked", MobileNode::checked, negated ? "the element is checked" : "the element is not checked");
    }

    public void toBeFocused() {
        single("toBeFocused", MobileNode::focused, negated ? "the element is focused" : "the element is not focused");
    }

    // ── content ─────────────────────────────────────────────────────────

    /** Displayed text (text, else label, else value) equals {@code expected}. */
    public void toHaveText(String expected) {
        text("toHaveText(\"" + expected + "\")", TextMatch.exact(expected));
    }

    public void toHaveText(Pattern expected) {
        text("toHaveText(/" + expected.pattern() + "/)", TextMatch.regex(expected));
    }

    /**
     * Displayed text equals {@code expected}; {@code ignoreCase} also ignores case, and whitespace
     * runs and leading/trailing blanks are normalized (as Playwright's {@code ignoreCase}).
     */
    public void toHaveText(String expected, boolean ignoreCase) {
        if (!ignoreCase) {
            toHaveText(expected);
            return;
        }
        String words = String.join("\\s+", java.util.Arrays.stream(expected.strip().split("\\s+")).map(Pattern::quote).toList());
        text("toHaveText(\"" + expected + "\", ignoreCase)",
                TextMatch.regex(Pattern.compile("^\\s*" + words + "\\s*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)));
    }

    /**
     * No text and no value (an empty text field; an iOS field showing only its placeholder counts
     * as empty).
     */
    public void toBeEmpty() {
        single("toBeEmpty", MobileLocatorAssert::empty, negated ? "the element is empty" : "the element has content");
    }

    /** Displayed text contains {@code expected} (case-insensitive). */
    public void toContainText(String expected) {
        text("toContainText(\"" + expected + "\")", TextMatch.contains(expected));
    }

    /** {@code value} attribute (text fields, sliders) equals {@code expected}. */
    public void toHaveValue(String expected) {
        single("toHaveValue(\"" + expected + "\")", n -> expected.equals(n.value() == null ? "" : n.value()),
                "value differs");
    }

    /** Number of matches equals {@code expected}. */
    public void toHaveCount(int expected) {
        all("toHaveCount(" + expected + ")", nodes -> nodes.size() == expected, "count differs");
    }

    // ── engine ──────────────────────────────────────────────────────────

    private static boolean empty(MobileNode n) {
        boolean noText = n.text() == null || n.text().isBlank();
        boolean noValue = n.value() == null || n.value().isBlank() || n.value().equals(n.placeholder());
        return noText && noValue;
    }

    private void text(String name, TextMatch match) {
        single(name, n -> match.test(n.displayText()), "text differs");
    }

    /** One element (strict); passes when {@code condition} (negated if {@code not()}) holds. */
    private void single(String name, Predicate<MobileNode> condition, String failure) {
        Outcome<Boolean> outcome = Poller.poll(locator.session(), timeout, tree -> {
            List<MobileNode> matches = locator.query().evaluate(tree);
            locator.strict(matches, name);
            if (matches.isEmpty()) {
                return Step.retry("no element matches", List.of());
            }
            MobileNode n = matches.get(0);
            return condition.test(n) != negated ? Step.done(true) : Step.retry(failure + ": " + n.describe(), matches);
        });
        verify(name, outcome);
    }

    /** All matches; passes when {@code condition} (negated if {@code not()}) holds for the list. */
    private void all(String name, Predicate<List<MobileNode>> condition, String failure) {
        Outcome<Boolean> outcome = Poller.poll(locator.session(), timeout, tree -> {
            List<MobileNode> matches = locator.query().evaluate(tree);
            return condition.test(matches) != negated ? Step.done(true)
                    : Step.retry(failure + " (" + matches.size() + " match(es))", matches);
        });
        verify(name, outcome);
    }

    private void verify(String name, Outcome<?> outcome) {
        if (outcome.passed()) {
            return;
        }
        String prefix = message != null && !message.isBlank() ? message + "\n" : "";
        throw new MobileAssertionError(prefix + "expect(" + locator.describe() + ")" + (negated ? ".not()" : "") + "." + name
                + " failed after " + timeout.toMillis() + " ms: " + outcome.lastFailure() + Poller.describe(outcome.lastEvidence()));
    }
}
