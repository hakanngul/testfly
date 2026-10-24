package io.testfly.mobile;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of one element of the device's view tree ({@code device.dump.ui}).
 *
 * @param type        native class, verbatim (e.g. {@code android.widget.EditText}, {@code TextField})
 * @param text        displayed text, or {@code null}
 * @param label       accessibility label, or {@code null}
 * @param name        accessibility name (iOS), or {@code null}
 * @param value       current value (text fields, sliders), or {@code null}
 * @param identifier  iOS accessibility identifier / Android resource-id, or {@code null}
 * @param placeholder placeholder text, or {@code null}
 * @param bounds      bounds in device pixels
 * @param visible     visibility; derived from non-empty bounds when the dump omits it
 * @param enabled     enabled state (defaults to {@code true})
 * @param selected    selected state
 * @param checked     checked state
 * @param focused     focus state
 * @param children    child nodes (empty for flat dumps)
 * @param raw         every attribute of the element as reported
 */
public record MobileNode(
        String type,
        String text,
        String label,
        String name,
        String value,
        String identifier,
        String placeholder,
        MobileBounds bounds,
        boolean visible,
        boolean enabled,
        boolean selected,
        boolean checked,
        boolean focused,
        List<MobileNode> children,
        Map<String, Object> raw) {

    public MobileNode {
        type = type == null ? "Unknown" : type;
        bounds = bounds == null ? MobileBounds.EMPTY : bounds;
        children = children == null ? List.of() : List.copyOf(children);
        raw = raw == null ? Map.of() : Map.copyOf(raw);
    }

    /** The text a reader sees: {@code text}, else {@code label}, else {@code value}, else empty. */
    public String displayText() {
        if (text != null) return text;
        if (label != null) return label;
        return value != null ? value : "";
    }

    /** The accessible name: {@code label}, else {@code text}, else empty (ARIA order). */
    public String accessibleName() {
        if (label != null) return label;
        return text != null ? text : "";
    }

    /** Short description used in error messages. */
    public String describe() {
        StringBuilder sb = new StringBuilder(type);
        if (identifier != null) sb.append(" id=").append(quote(identifier));
        if (label != null) sb.append(" label=").append(quote(label));
        if (text != null && !text.equals(label)) sb.append(" text=").append(quote(text));
        if (value != null) sb.append(" value=").append(quote(value));
        if (!visible) sb.append(" hidden");
        if (!enabled) sb.append(" disabled");
        sb.append(' ').append(bounds);
        return sb.toString();
    }

    private static String quote(String s) {
        return '"' + (s.length() > 60 ? s.substring(0, 57) + "..." : s) + '"';
    }
}
