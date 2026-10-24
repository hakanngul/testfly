package io.testfly.mobile.internal;

import io.testfly.mobile.MobileNode;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Finds the on-screen keyboard in a full view tree ({@code dump.ui} with {@code full}). */
public final class Keyboard {

    // shortcut: English key labels only; upgrade with localized labels when a device language needs them.
    private static final Set<String> DISMISS_KEYS = Set.of("done", "return", "hide keyboard", "go", "search", "send");

    private Keyboard() {
    }

    /** The keyboard: iOS {@code Keyboard} element, or an Android element of an input method ({@code …inputmethod…:id/…}). */
    public static MobileNode find(List<MobileNode> tree) {
        for (MobileNode n : LocatorEngine.flatten(tree)) {
            String id = n.identifier() == null ? "" : n.identifier();
            if (LocatorEngine.bareTypeName(n.type()).equals("keyboard") || id.contains("inputmethod")) {
                return n;
            }
        }
        return null;
    }

    /** The key that closes an iOS keyboard, or {@code null}. */
    public static MobileNode dismissKey(MobileNode keyboard) {
        for (MobileNode n : LocatorEngine.flatten(keyboard.children())) {
            String label = n.displayText() == null ? "" : n.displayText().trim().toLowerCase(Locale.ROOT);
            String type = LocatorEngine.bareTypeName(n.type());
            if ((type.equals("button") || type.equals("key")) && DISMISS_KEYS.contains(label)) {
                return n;
            }
        }
        return null;
    }
}
