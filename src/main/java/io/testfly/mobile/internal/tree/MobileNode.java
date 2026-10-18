package io.testfly.mobile.internal.tree;

import java.util.List;
import java.util.Map;

/**
 * Immutable normalized snapshot data, independent of a wire schema.
 *
 * <p>Text, type, testId and bounds may be absent ({@code null}). A {@code null} visible or
 * enabled flag means unknown, never false. Attributes and children must be non-null and
 * contain no null entries; both are defensively copied. Bounds do not define ancestry.
 */
public record MobileNode(String type, String text, String testId, MobileBounds bounds,
                         Boolean visible, Boolean enabled, Map<String, String> attributes,
                         List<MobileNode> children) {

    public MobileNode {
        attributes = Map.copyOf(attributes);
        children = List.copyOf(children);
    }

    /** Redacts node content, including identifiers, raw types and attribute values. */
    @Override
    public String toString() {
        return "MobileNode[bounds=" + bounds + ", visible=" + visible
                + ", enabled=" + enabled + ", children=" + children.size() + "]";
    }
}
