package io.testfly.wait;

import io.testfly.api.TestFlyApi;

/**
 * The kind of interaction an element is about to receive. It decides which
 * actionability checks {@link WaitEngine#waitForActionable(org.openqa.selenium.WebElement, ActionKind)}
 * applies. Every kind requires the element to be visible and stable.
 */
@TestFlyApi(since = "1.1.0")
public enum ActionKind {
    /** Click, double-click, right-click: enabled, and not covered by another element. */
    CLICK(true, true),
    /** Hover: not covered by another element; a disabled element may still be hovered. */
    HOVER(false, true),
    /** Type, clear, select: enabled; being covered does not matter for keyboard input. */
    EDIT(true, false);

    private final boolean requiresEnabled;
    private final boolean requiresHitTarget;

    ActionKind(boolean requiresEnabled, boolean requiresHitTarget) {
        this.requiresEnabled = requiresEnabled;
        this.requiresHitTarget = requiresHitTarget;
    }

    public boolean requiresEnabled() {
        return requiresEnabled;
    }

    public boolean requiresHitTarget() {
        return requiresHitTarget;
    }
}
