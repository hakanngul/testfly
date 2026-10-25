package io.testfly.mobile;

import io.testfly.mobile.internal.LocatorEngine;
import io.testfly.mobile.internal.LocatorEngine.TextMatch;
import io.testfly.mobile.internal.MobileSession;
import io.testfly.mobile.internal.SelectorParser;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Entry point for locating elements on the device screen (incubating, ADR 0002). Names follow
 * Mobilewright/Playwright: {@code getByRole}, {@code getByText}, {@code getByTestId}, ...
 */
public final class MobileScreen {

    private final MobileSession session;

    MobileScreen(MobileSession session) {
        this.session = session;
    }

    /** Elements of {@code role} (native classes of both platforms). */
    public MobileLocator getByRole(MobileRole role) {
        return locator(LocatorEngine.byRole(Objects.requireNonNull(role, "role"), null));
    }

    /** Elements of {@code role} whose accessible name (label, else text) equals {@code name}. */
    public MobileLocator getByRole(MobileRole role, String name) {
        return locator(LocatorEngine.byRole(Objects.requireNonNull(role, "role"), TextMatch.exact(name)));
    }

    public MobileLocator getByRole(MobileRole role, Pattern name) {
        return locator(LocatorEngine.byRole(Objects.requireNonNull(role, "role"), TextMatch.regex(name)));
    }

    /** Elements whose text (else label, else value) equals {@code text}. */
    public MobileLocator getByText(String text) {
        return getByText(text, true);
    }

    /** {@code exact=false}: case-insensitive substring. */
    public MobileLocator getByText(String text, boolean exact) {
        return locator(LocatorEngine.byText(exact ? TextMatch.exact(text) : TextMatch.contains(text)));
    }

    public MobileLocator getByText(Pattern text) {
        return locator(LocatorEngine.byText(TextMatch.regex(text)));
    }

    /** Elements whose accessibility label equals {@code label}. */
    public MobileLocator getByLabel(String label) {
        return getByLabel(label, true);
    }

    public MobileLocator getByLabel(String label, boolean exact) {
        return locator(LocatorEngine.byLabel(exact ? TextMatch.exact(label) : TextMatch.contains(label)));
    }

    /** iOS accessibility identifier or Android resource-id ({@code name} also matches {@code pkg:id/name}). */
    public MobileLocator getByTestId(String testId) {
        return locator(LocatorEngine.byTestId(Objects.requireNonNull(testId, "testId")));
    }

    public MobileLocator getByPlaceholder(String placeholder) {
        return getByPlaceholder(placeholder, true);
    }

    public MobileLocator getByPlaceholder(String placeholder, boolean exact) {
        return locator(LocatorEngine.byPlaceholder(exact ? TextMatch.exact(placeholder) : TextMatch.contains(placeholder)));
    }

    /** Elements of a raw native class, e.g. {@code android.widget.Switch} or {@code XCUIElementTypeDatePicker}. */
    public MobileLocator getByType(String type) {
        return locator(LocatorEngine.byType(Objects.requireNonNull(type, "type")));
    }

    /**
     * Locator from a selector string: a bare string is a test id; otherwise {@code testid=},
     * {@code text=}, {@code label=}, {@code placeholder=}, {@code type=} or
     * {@code role=button;name=Sign in} (exact matches). Used by {@link MobileFind}.
     */
    public MobileLocator locate(String selector) {
        return locator(SelectorParser.parse(selector));
    }

    /** Swipes across the middle of the screen, half its width or height. */
    public void swipe(SwipeDirection direction) {
        MobileScreenSize size = session.driver().screenSize(session.rpcTimeout());
        int cx = size.width() / 2;
        int cy = size.height() / 2;
        int dx = size.width() / 4;
        int dy = size.height() / 4;
        switch (direction) {
            case UP -> session.driver().swipe(cx, cy + dy, cx, cy - dy, session.rpcTimeout());
            case DOWN -> session.driver().swipe(cx, cy - dy, cx, cy + dy, session.rpcTimeout());
            case LEFT -> session.driver().swipe(cx + dx, cy, cx - dx, cy, session.rpcTimeout());
            case RIGHT -> session.driver().swipe(cx - dx, cy, cx + dx, cy, session.rpcTimeout());
        }
    }

    /** Taps the screen at {@code (x, y)} in screen points (no element lookup). */
    public void tap(int x, int y) {
        session.driver().tap(x, y, session.rpcTimeout());
    }

    public void doubleTap(int x, int y) {
        tap(x, y);
        tap(x, y);
    }

    public void longPress(int x, int y) {
        longPress(x, y, null);
    }

    /** Long-presses at {@code (x, y)} for {@code duration} (the server default when {@code null}). */
    public void longPress(int x, int y, java.time.Duration duration) {
        session.driver().longPress(x, y, duration, session.rpcTimeout());
    }

        /** The current view tree (no waiting). */
    public List<MobileNode> viewTree() {
        return session.viewTree(session.rpcTimeout());
    }

    /** PNG bytes of the current screen. */
    public byte[] screenshot() {
        return session.driver().screenshot(session.rpcTimeout());
    }

    MobilePlatform platform() {
        return session.platform();
    }

    MobileLocator locator(LocatorEngine.Query query) {
        return new MobileLocator(session, query, session.actionTimeout());
    }
}
