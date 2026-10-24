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

    /** {@code null} for a locator from a static factory: it binds to the current test's device when used. */
    private final MobileSession bound;
    private final Query query;
    /** {@code null}: the device's action timeout. */
    private final Duration ownTimeout;

    MobileLocator(MobileSession session, Query query, Duration timeout) {
        this.bound = session;
        this.query = query;
        this.ownTimeout = timeout;
    }

    // ── static factories (bind to the current test's device when used) ──

    /**
     * A locator from a selector string ({@link MobileScreen#locate(String)} syntax). Unlike
     * {@code screen().locate(...)} it binds to the device of the thread that uses it, so it can be a
     * {@code static final} field shared by parallel tests.
     */
    public static MobileLocator of(String selector) {
        return new MobileLocator(null, io.testfly.mobile.internal.SelectorParser.parse(selector), null);
    }

    /** iOS accessibility identifier / name, or Android content-desc (Appium {@code accessibilityId}). */
    public static MobileLocator accessibilityId(String id) {
        return of("accessibility=" + id);
    }

    /** iOS identifier or Android resource-id, full ({@code pkg:id/name}) or short (Appium {@code id}). */
    public static MobileLocator id(String id) {
        return of("id=" + id);
    }

    /** XPath 1.0 over the view tree; elements are named {@code XCUIElementType…} / {@code android.widget.…}. */
    public static MobileLocator xpath(String xpath) {
        return of("xpath=" + xpath);
    }

    /** iOS class chain (Appium {@code iOSClassChain}), common subset. */
    public static MobileLocator iosClassChain(String chain) {
        return of("chain=" + chain);
    }

    /** iOS NSPredicate (Appium {@code iOSNsPredicateString}), common subset. */
    public static MobileLocator iosPredicate(String predicate) {
        return of("predicate=" + predicate);
    }

    /** Native type, e.g. {@code android.widget.TextView} or {@code XCUIElementTypeButton} (Appium {@code className}). */
    public static MobileLocator className(String type) {
        return of("type=" + type);
    }

    public static MobileLocator byTestId(String testId) {
        return of("testid=" + testId);
    }

    public static MobileLocator byText(String text) {
        return of("text=" + text);
    }

    public static MobileLocator byLabel(String label) {
        return of("label=" + label);
    }

    public static MobileLocator byPlaceholder(String placeholder) {
        return of("placeholder=" + placeholder);
    }

    public static MobileLocator byRole(MobileRole role) {
        return new MobileLocator(null, LocatorEngine.byRole(Objects.requireNonNull(role, "role"), null), null);
    }

    public static MobileLocator byRole(MobileRole role, String name) {
        return new MobileLocator(null, LocatorEngine.byRole(Objects.requireNonNull(role, "role"), TextMatch.exact(name)), null);
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
        return new MobileLocator(bound, query, Objects.requireNonNull(timeout, "timeout"));
    }

    // ── actions ─────────────────────────────────────────────────────────

    public void tap() {
        act("tap", (node, rpc) -> session().driver().tap(cx(node), cy(node), rpc.get()));
    }

    public void doubleTap() {
        act("doubleTap", (node, rpc) -> {
            session().driver().tap(cx(node), cy(node), rpc.get());
            session().driver().tap(cx(node), cy(node), rpc.get());
        });
    }

    public void longPress() {
        longPress(null);
    }

    /** Long-presses for {@code duration} (the server default when {@code null}). */
    public void longPress(Duration duration) {
        act("longPress", (node, rpc) -> session().driver().longPress(cx(node), cy(node), duration, rpc.get()));
    }

    /** Focuses the element, clears it and types {@code text}. */
    public void fill(String text) {
        act("fill", (node, rpc) -> {
            session().driver().tap(cx(node), cy(node), rpc.get());
            session().driver().clearText(rpc.get());
            session().driver().typeText(text, rpc.get());
        });
    }

    /** Same as {@link #fill(String)}: clears the element, then types {@code text} (as web {@code Locator.type}). */
    public void type(String text) {
        act("type", (node, rpc) -> {
            session().driver().tap(cx(node), cy(node), rpc.get());
            session().driver().clearText(rpc.get());
            session().driver().typeText(text, rpc.get());
        });
    }

    /** Focuses the element and types {@code text} after its current content (as web {@code Locator.append}). */
    public void append(String text) {
        act("append", (node, rpc) -> {
            session().driver().tap(cx(node), cy(node), rpc.get());
            session().driver().typeText(text, rpc.get());
        });
    }

    /** Focuses the element and clears it. */
    public void clear() {
        act("clear", (node, rpc) -> {
            session().driver().tap(cx(node), cy(node), rpc.get());
            session().driver().clearText(rpc.get());
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
            session().driver().swipe(x, y, x2, y2, rpc.get());
        });
    }

    /** Two-finger pinch out (zoom in) centred on the element. */
    public void pinchOut() {
        act("pinchOut", (node, rpc) -> session().driver().pinch(cx(node), cy(node), true, 0, null, rpc.get()));
    }

    /** Two-finger pinch in (zoom out) centred on the element. */
    public void pinchIn() {
        act("pinchIn", (node, rpc) -> session().driver().pinch(cx(node), cy(node), false, 0, null, rpc.get()));
    }

    /** Presses the element, drags it onto the centre of {@code target} and releases it (reordering, sliders). */
    public void dragTo(MobileLocator target) {
        MobileNode to = Objects.requireNonNull(target, "target").node();
        act("dragTo", (node, rpc) -> session().driver().drag(cx(node), cy(node), cx(to), cy(to), null, rpc.get()));
    }

    /** Waits until the element is visible. */
    public void waitForVisible() {
        resolveVisible("waitForVisible");
    }

    /** Waits until no match is visible (or nothing matches). */
    public void waitForHidden() {
        Outcome<Boolean> outcome = Poller.poll(session(), timeout(), tree -> {
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
        return query.evaluate(session().viewTree(session().rpcTimeout()));
    }

    /** Whether exactly one visible match exists right now (no waiting; strict). */
    public boolean isVisible() {
        List<MobileNode> nodes = nodes();
        strict(nodes, "isVisible");
        return nodes.size() == 1 && nodes.get(0).visible();
    }

    /** Waits for the element and returns its displayed text ({@code text}, else label, else value). */
    public String getText() {
        return resolveVisible("getText").displayText();
    }

    /** Waits for the element and returns its {@code value} (empty when it has none). */
    public String getValue() {
        String v = resolveVisible("getValue").value();
        return v == null ? "" : v;
    }

    /** Whether at least one element matches right now (no waiting). */
    public boolean isPresent() {
        return !nodes().isEmpty();
    }

    /** Whether no match is visible right now (no waiting). */
    public boolean isHidden() {
        return nodes().stream().noneMatch(MobileNode::visible);
    }

    /** Whether exactly one match exists and it is enabled (no waiting; strict). */
    public boolean isEnabled() {
        return single("isEnabled") instanceof MobileNode n && n.enabled();
    }

    /** Whether exactly one match exists and it is disabled (no waiting; strict). */
    public boolean isDisabled() {
        return single("isDisabled") instanceof MobileNode n && !n.enabled();
    }

    public boolean isSelected() {
        return single("isSelected") instanceof MobileNode n && n.selected();
    }

    public boolean isChecked() {
        return single("isChecked") instanceof MobileNode n && n.checked();
    }

    public boolean isFocused() {
        return single("isFocused") instanceof MobileNode n && n.focused();
    }

    /** Displayed texts of every match in the current screen (no waiting). */
    public List<String> allTexts() {
        return nodes().stream().map(MobileNode::displayText).toList();
    }

    /**
     * Waits for the element and returns an attribute ({@code name}, {@code label}, {@code value},
     * {@code text}, {@code identifier}/{@code resource-id}, {@code content-desc}, {@code type},
     * {@code enabled}, …, or any raw mobilecli field); {@code null} when the element has none.
     */
    public String getAttribute(String name) {
        MobileNode n = resolveVisible("getAttribute");
        String key = switch (name) {
            case "resource-id", "resourceId" -> "identifier";
            case "content-desc", "contentDescription" -> "label";
            case "class", "className" -> "type";
            default -> name;
        };
        String v = io.testfly.mobile.internal.NsPredicate.attribute(n, key);
        return v.isEmpty() ? null : v;
    }

    /** Scrolls the content up (swipes up) until the element is visible; see {@link #scrollIntoView(SwipeDirection)}. */
    public void scrollIntoView() {
        scrollIntoView(SwipeDirection.UP);
    }

    /**
     * Swipes across the screen in {@code direction} until exactly one match is visible, the screen
     * stops changing (end of the list) or the timeout runs out.
     */
    public void scrollIntoView(SwipeDirection direction) {
        step("scrollIntoView");
        MobileSession s = session();
        long deadline = System.nanoTime() + timeout().toNanos();
        String previous = null;
        while (true) {
            List<MobileNode> tree = s.viewTree(s.rpcTimeout());
            List<MobileNode> matches = query.evaluate(tree);
            strict(matches, "scrollIntoView");
            if (matches.size() == 1 && matches.get(0).visible()) {
                return;
            }
            String signature = signature(tree);
            if (signature.equals(previous) || System.nanoTime() >= deadline) {
                throw new MobileTimeoutException("scrollIntoView on " + describe() + " found no visible element after scrolling "
                        + direction.name().toLowerCase(java.util.Locale.ROOT) + (signature.equals(previous) ? " to the end" : " for " + timeout().toMillis() + " ms") + ".");
            }
            previous = signature;
            MobileScreenSize size = s.driver().screenSize(s.rpcTimeout());
            int cx = size.width() / 2;
            int cy = size.height() / 2;
            int dx = size.width() / 4;
            int dy = size.height() / 4;
            switch (direction) {
                case UP -> s.driver().swipe(cx, cy + dy, cx, cy - dy, s.rpcTimeout());
                case DOWN -> s.driver().swipe(cx, cy - dy, cx, cy + dy, s.rpcTimeout());
                case LEFT -> s.driver().swipe(cx + dx, cy, cx - dx, cy, s.rpcTimeout());
                case RIGHT -> s.driver().swipe(cx - dx, cy, cx + dx, cy, s.rpcTimeout());
            }
        }
    }

    private static String signature(List<MobileNode> tree) {
        StringBuilder sb = new StringBuilder();
        for (MobileNode n : LocatorEngine.flatten(tree)) {
            sb.append(n.displayText()).append('@').append(n.bounds()).append('|');
        }
        return sb.toString();
    }

    private MobileNode single(String action) {
        List<MobileNode> nodes = nodes();
        strict(nodes, action);
        return nodes.size() == 1 ? nodes.get(0) : null;
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
        return bound != null ? bound : io.testfly.mobile.internal.MobileSessions.current().session();
    }

    Duration timeout() {
        return ownTimeout != null ? ownTimeout : session().actionTimeout();
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
        return new MobileLocator(bound, q, ownTimeout);
    }

    void strict(List<MobileNode> matches, String action) {
        if (matches.size() > 1 && !query.positional()) {
            throw new MobileLocatorException("Strict mode: " + describe() + " matched " + matches.size()
                    + " elements for " + action + ". Use first(), last(), nth(i) or a narrower query." + Poller.describe(matches));
        }
    }

    private MobileNode resolveVisible(String action) {
        Outcome<MobileNode> outcome = Poller.poll(session(), timeout(), tree -> {
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
        long deadline = System.nanoTime() + timeout().toNanos();
        MobileNode node = resolveActionable(action);
        body.accept(node, () -> {
            if (System.nanoTime() >= deadline) {
                throw new MobileTimeoutException(action + " on " + describe() + " ran out of its " + timeout().toMillis()
                        + " ms budget before the device call. A previous step of this action may already have run.");
            }
            return session().requestTimeout(deadline);
        });
    }

    private MobileNode resolveActionable(String action) {
        MobileBounds[] previous = new MobileBounds[1];
        Outcome<MobileNode> outcome = Poller.poll(session(), timeout(), tree -> {
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
        return new MobileTimeoutException(action + " on " + describe() + " timed out after " + timeout().toMillis()
                + " ms: " + outcome.lastFailure() + "." + Poller.describe(outcome.lastEvidence()));
    }

    private static int cx(MobileNode n) {
        return (int) Math.round(n.bounds().centerX());
    }

    private static int cy(MobileNode n) {
        return (int) Math.round(n.bounds().centerY());
    }
}
