package io.testfly.mobile;

import io.testfly.mobile.internal.LocatorEngine;
import io.testfly.mobile.internal.LocatorEngine.Query;
import io.testfly.mobile.internal.LocatorEngine.TextMatch;
import io.testfly.mobile.internal.MobileSession;
import io.testfly.mobile.internal.Poller;
import io.testfly.mobile.internal.Poller.Outcome;
import io.testfly.mobile.internal.Poller.Step;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An immutable query for elements of the device's screen (incubating, ADR 0002). Creating or
 * refining a locator does not touch the device; actions and reads resolve it against fresh view
 * trees and auto-wait.
 *
 * <ul>
 *   <li>Actions wait until exactly one element matches and it is visible, enabled and stable
 *       (bounds unchanged across two polls), then act on the centre of its bounds.</li>
 *   <li>Strict mode: several matches are an error unless {@link #first()}, {@link #last()} or
 *       {@link #nth(int)} picks one.</li>
 * </ul>
 */
public final class MobileLocator {

    private final MobileSession session;
    private final Query query;
    private final Duration timeout;

    MobileLocator(MobileSession session, Query query, Duration timeout) {
        this.session = session;
        this.query = query;
        this.timeout = timeout;
    }

    // ── refinements ─────────────────────────────────────────────────────

    /** The {@code index}-th match (0-based); a negative index counts from the end. */
    public MobileLocator nth(int index) {
        return derive(LocatorEngine.nth(query, index));
    }

    public MobileLocator first() {
        return nth(0);
    }

    public MobileLocator last() {
        return nth(-1);
    }

    /** Matches of this locator that lie inside a match of {@code container}. */
    public MobileLocator within(MobileLocator container) {
        return derive(LocatorEngine.within(query, container.query));
    }

    /** Matches of this locator or of {@code other}, in screen order (e.g. a fallback). */
    public MobileLocator or(MobileLocator other) {
        return derive(LocatorEngine.or(query, other.query));
    }

    /** Elements matched by both locators. */
    public MobileLocator and(MobileLocator other) {
        return derive(LocatorEngine.and(query, other.query));
    }

    /** Keeps matches whose subtree contains {@code text} (case-insensitive substring). */
    public MobileLocator filterHasText(String text) {
        return derive(LocatorEngine.filter(query, TextMatch.contains(text), null, null, null));
    }

    public MobileLocator filterHasText(Pattern text) {
        return derive(LocatorEngine.filter(query, TextMatch.regex(text), null, null, null));
    }

    /** Keeps matches whose subtree does not contain {@code text}. */
    public MobileLocator filterHasNotText(String text) {
        return derive(LocatorEngine.filter(query, null, TextMatch.contains(text), null, null));
    }

    /** Keeps matches that contain an element matched by {@code inner}. */
    public MobileLocator filterHas(MobileLocator inner) {
        return derive(LocatorEngine.filter(query, null, null, inner.query, null));
    }

    /** Keeps matches that contain no element matched by {@code inner}. */
    public MobileLocator filterHasNot(MobileLocator inner) {
        return derive(LocatorEngine.filter(query, null, null, null, inner.query));
    }

    /** The same query with another auto-wait budget for its actions and reads. */
    public MobileLocator withTimeout(Duration timeout) {
        return new MobileLocator(session, query, Objects.requireNonNull(timeout, "timeout"));
    }

    // ── actions ─────────────────────────────────────────────────────────

    public void tap() {
        act("tap", (node, rpc) -> session.driver().tap(cx(node), cy(node), rpc.get()));
    }

    public void doubleTap() {
        act("doubleTap", (node, rpc) -> {
            session.driver().tap(cx(node), cy(node), rpc.get());
            session.driver().tap(cx(node), cy(node), rpc.get());
        });
    }

    public void longPress() {
        longPress(null);
    }

    /** Long-presses for {@code duration} (the server default when {@code null}). */
    public void longPress(Duration duration) {
        act("longPress", (node, rpc) -> session.driver().longPress(cx(node), cy(node), duration, rpc.get()));
    }

    /** Focuses the element, clears it and types {@code text}. */
    public void fill(String text) {
        act("fill", (node, rpc) -> {
            session.driver().tap(cx(node), cy(node), rpc.get());
            session.driver().clearText(rpc.get());
            session.driver().typeText(text, rpc.get());
        });
    }

    /** Focuses the element and types {@code text} after its current content. */
    public void type(String text) {
        act("type", (node, rpc) -> {
            session.driver().tap(cx(node), cy(node), rpc.get());
            session.driver().typeText(text, rpc.get());
        });
    }

    /** Focuses the element and clears it. */
    public void clear() {
        act("clear", (node, rpc) -> {
            session.driver().tap(cx(node), cy(node), rpc.get());
            session.driver().clearText(rpc.get());
        });
    }

    /** Swipes from the element's centre in {@code direction}, half the element's size or at least 200 px. */
    public void swipe(SwipeDirection direction) {
        act("swipe", (node, rpc) -> {
            int x = cx(node);
            int y = cy(node);
            boolean horizontal = direction == SwipeDirection.LEFT || direction == SwipeDirection.RIGHT;
            int distance = (int) Math.max(200, (horizontal ? node.bounds().width() : node.bounds().height()) / 2);
            int x2 = direction == SwipeDirection.LEFT ? x - distance : direction == SwipeDirection.RIGHT ? x + distance : x;
            int y2 = direction == SwipeDirection.UP ? y - distance : direction == SwipeDirection.DOWN ? y + distance : y;
            session.driver().swipe(x, y, x2, y2, rpc.get());
        });
    }

    /** Waits until the element is visible. */
    public void waitForVisible() {
        resolveVisible("waitForVisible");
    }

    /** Waits until no match is visible (or nothing matches). */
    public void waitForHidden() {
        Outcome<Boolean> outcome = Poller.poll(session, timeout, tree -> {
            List<MobileNode> visible = query.evaluate(tree).stream().filter(MobileNode::visible).toList();
            return visible.isEmpty() ? Step.done(true) : Step.retry(visible.size() + " visible match(es)", visible);
        });
        if (!outcome.passed()) {
            throw timeout("waitForHidden", outcome);
        }
    }

    // ── reads ───────────────────────────────────────────────────────────

    /** Number of matches in the current screen (no waiting). */
    public int count() {
        return nodes().size();
    }

    /** Matches in the current screen (no waiting). */
    public List<MobileNode> nodes() {
        return query.evaluate(session.driver().viewTree(session.rpcTimeout()));
    }

    /** Whether exactly one visible match exists right now (no waiting; strict). */
    public boolean isVisible() {
        List<MobileNode> nodes = nodes();
        strict(nodes, "isVisible");
        return nodes.size() == 1 && nodes.get(0).visible();
    }

    /** Waits for the element and returns its displayed text ({@code text}, else label, else value). */
    public String text() {
        return resolveVisible("text").displayText();
    }

    /** Waits for the element and returns its {@code value} (empty when it has none). */
    public String value() {
        String v = resolveVisible("value").value();
        return v == null ? "" : v;
    }

    /** Waits for the element and returns its node snapshot. */
    public MobileNode node() {
        return resolveVisible("node");
    }

    // ── internals ───────────────────────────────────────────────────────

    Query query() {
        return query;
    }

    MobileSession session() {
        return session;
    }

    Duration timeout() {
        return timeout;
    }

    /** Human-readable query, e.g. {@code getByRole(button, name="Save").first()}. */
    public String describe() {
        return query.describe();
    }

    @Override
    public String toString() {
        return "MobileLocator(" + describe() + ")";
    }

    /**
     * Logs the action as a test step, like web locators do ("Tap getByText(...)"). Typed text is
     * not logged. Outside a running test (scripts) nothing is logged.
     */
    private void step(String action) {
        if (io.testfly.internal.TestFlyContext.getCurrentTestId() != null) {
            io.testfly.steps.StepLogger.step(Character.toUpperCase(action.charAt(0)) + action.substring(1) + " " + describe());
        }
    }

    private MobileLocator derive(Query q) {
        return new MobileLocator(session, q, timeout);
    }

    void strict(List<MobileNode> matches, String action) {
        if (matches.size() > 1 && !query.positional()) {
            throw new MobileLocatorException("Strict mode: " + describe() + " matched " + matches.size()
                    + " elements for " + action + ". Use first(), last(), nth(i) or a narrower query." + Poller.describe(matches));
        }
    }

    private MobileNode resolveVisible(String action) {
        Outcome<MobileNode> outcome = Poller.poll(session, timeout, tree -> {
            List<MobileNode> matches = query.evaluate(tree);
            strict(matches, action);
            if (matches.isEmpty()) {
                return Step.retry("no element matches", List.of());
            }
            MobileNode n = matches.get(0);
            return n.visible() ? Step.done(n) : Step.retry("the element is not visible", matches);
        });
        if (!outcome.passed()) {
            throw timeout(action, outcome);
        }
        return outcome.value();
    }

    /**
     * Resolves an actionable element, then runs {@code action}. Every request of the action is
     * bounded by {@code min(rpcMs, time left of this locator's budget)} (ADR 0002 D6), so a stalled
     * server cannot hold the caller past the budget.
     */
    private void act(String action, java.util.function.BiConsumer<MobileNode, java.util.function.Supplier<Duration>> body) {
        step(action);
        long deadline = System.nanoTime() + timeout.toNanos();
        MobileNode node = resolveActionable(action);
        body.accept(node, () -> {
            if (System.nanoTime() >= deadline) {
                throw new MobileTimeoutException(action + " on " + describe() + " ran out of its " + timeout.toMillis()
                        + " ms budget before the device call. A previous step of this action may already have run.");
            }
            return session.requestTimeout(deadline);
        });
    }

    private MobileNode resolveActionable(String action) {
        MobileBounds[] previous = new MobileBounds[1];
        Outcome<MobileNode> outcome = Poller.poll(session, timeout, tree -> {
            List<MobileNode> matches = query.evaluate(tree);
            strict(matches, action);
            if (matches.isEmpty()) {
                previous[0] = null;
                return Step.retry("no element matches", List.of());
            }
            MobileNode n = matches.get(0);
            if (!n.visible()) {
                previous[0] = null;
                return Step.retry("the element is not visible", matches);
            }
            if (!n.enabled()) {
                previous[0] = null;
                return Step.retry("the element is disabled", matches);
            }
            if (!n.bounds().equals(previous[0])) {
                previous[0] = n.bounds();
                return Step.retry("the element is not stable yet (bounds " + n.bounds() + ")", matches);
            }
            return Step.done(n);
        });
        if (!outcome.passed()) {
            throw timeout(action, outcome);
        }
        return outcome.value();
    }

    private MobileTimeoutException timeout(String action, Outcome<?> outcome) {
        return new MobileTimeoutException(action + " on " + describe() + " timed out after " + timeout.toMillis()
                + " ms: " + outcome.lastFailure() + "." + Poller.describe(outcome.lastEvidence()));
    }

    private static int cx(MobileNode n) {
        return (int) Math.round(n.bounds().centerX());
    }

    private static int cy(MobileNode n) {
        return (int) Math.round(n.bounds().centerY());
    }
}
