package io.testfly.mobile.internal;

import io.testfly.mobile.MobileLocatorException;
import io.testfly.mobile.MobileNode;
import io.testfly.mobile.MobileRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Client-side evaluation of mobile locators over one view-tree snapshot (ADR 0002 D6).
 *
 * <p>The matching rules (text/label/test-id semantics, role resolution, chained queries with a
 * bounds fallback for flat dumps) are ported from Mobilewright's query engine
 * ({@code @mobilewright/core}, Apache-2.0); see NOTICE.
 */
public final class LocatorEngine {

    private LocatorEngine() {
    }

    // ── text matching ───────────────────────────────────────────────────

    /** A string matcher: exact, case-insensitive substring, or regex. */
    public record TextMatch(String value, Pattern pattern, boolean exact) {

        public static TextMatch exact(String value) {
            return new TextMatch(value, null, true);
        }

        public static TextMatch contains(String value) {
            return new TextMatch(value, null, false);
        }

        public static TextMatch regex(Pattern pattern) {
            return new TextMatch(null, pattern, false);
        }

        public boolean test(String actual) {
            if (actual == null) {
                return false;
            }
            if (pattern != null) {
                return pattern.matcher(actual).find();
            }
            return exact ? actual.equals(value) : actual.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
        }

        @Override
        public String toString() {
            return pattern != null ? "/" + pattern.pattern() + "/" : exact ? '"' + value + '"' : "~\"" + value + '"';
        }
    }

    // ── queries ─────────────────────────────────────────────────────────

    /** An immutable query; {@link #evaluate} returns matches in document order without duplicates. */
    public abstract static class Query {

        public abstract List<MobileNode> evaluate(List<MobileNode> roots);

        public abstract String describe();

        /** True when the query already picks one position (nth/first/last), which lifts strict mode. */
        public boolean positional() {
            return false;
        }

        @Override
        public String toString() {
            return describe();
        }
    }

    private abstract static class Leaf extends Query {
        abstract boolean matches(MobileNode node);

        @Override
        public List<MobileNode> evaluate(List<MobileNode> roots) {
            List<MobileNode> out = new ArrayList<>();
            for (MobileNode node : flatten(roots)) {
                if (matches(node)) {
                    out.add(node);
                }
            }
            return out;
        }
    }

    public static Query byText(TextMatch match) {
        return new Leaf() {
            boolean matches(MobileNode n) {
                return match.test(n.displayText());
            }

            public String describe() {
                return "getByText(" + match + ")";
            }
        };
    }

    public static Query byLabel(TextMatch match) {
        return new Leaf() {
            boolean matches(MobileNode n) {
                return n.label() != null && match.test(n.label());
            }

            public String describe() {
                return "getByLabel(" + match + ")";
            }
        };
    }

    public static Query byPlaceholder(TextMatch match) {
        return new Leaf() {
            boolean matches(MobileNode n) {
                return n.placeholder() != null && match.test(n.placeholder());
            }

            public String describe() {
                return "getByPlaceholder(" + match + ")";
            }
        };
    }

    /** iOS accessibility identifier or Android resource-id; {@code name} also matches {@code <package>:id/name}. */
    public static Query byTestId(String id) {
        return new Leaf() {
            boolean matches(MobileNode n) {
                if (id.equals(n.identifier()) || id.equals(n.raw().get("key")) || id.equals(n.raw().get("resourceId"))) {
                    return true;
                }
                return n.identifier() != null && n.identifier().endsWith(":id/" + id);
            }

            public String describe() {
                return "getByTestId(\"" + id + "\")";
            }
        };
    }

    /** A query that fails with {@code message} when used, e.g. a page field without a definition for this platform. */
    public static Query missing(String message) {
        return new Query() {
            @Override
            public List<MobileNode> evaluate(List<MobileNode> roots) {
                throw new MobileLocatorException(message);
            }

            @Override
            public String describe() {
                return "missing(" + message + ")";
            }
        };
    }

    /** Raw native class, case-insensitive (no normalisation). */
    public static Query byType(String type) {
        return new Leaf() {
            boolean matches(MobileNode n) {
                return n.type().equalsIgnoreCase(type);
            }

            public String describe() {
                return "getByType(\"" + type + "\")";
            }
        };
    }

    /** Role match; {@code name} (nullable) is matched against the accessible name (label, then text). */
    public static Query byRole(MobileRole role, TextMatch name) {
        return new Leaf() {
            boolean matches(MobileNode n) {
                return matchesRole(n, role) && (name == null || name.test(n.accessibleName()));
            }

            public String describe() {
                return "getByRole(" + role.name().toLowerCase(Locale.ROOT) + (name != null ? ", name=" + name : "") + ")";
            }
        };
    }

    /** Index into the parent's matches; a negative index counts from the end. */
    public static Query nth(Query parent, int index) {
        return new Query() {
            public List<MobileNode> evaluate(List<MobileNode> roots) {
                List<MobileNode> all = parent.evaluate(roots);
                int i = index < 0 ? all.size() + index : index;
                return i >= 0 && i < all.size() ? List.of(all.get(i)) : List.of();
            }

            public String describe() {
                return parent.describe() + (index == 0 ? ".first()" : index == -1 ? ".last()" : ".nth(" + index + ")");
            }

            public boolean positional() {
                return true;
            }
        };
    }

    /** Elements matching either query, in document order. */
    public static Query or(Query left, Query right) {
        return new Query() {
            public List<MobileNode> evaluate(List<MobileNode> roots) {
                Set<MobileNode> matched = identitySet(left.evaluate(roots));
                matched.addAll(right.evaluate(roots));
                return inDocumentOrder(roots, matched);
            }

            public String describe() {
                return left.describe() + ".or(" + right.describe() + ")";
            }
        };
    }

    /** Elements matching both queries. */
    public static Query and(Query left, Query right) {
        return new Query() {
            public List<MobileNode> evaluate(List<MobileNode> roots) {
                Set<MobileNode> rightMatches = identitySet(right.evaluate(roots));
                return left.evaluate(roots).stream().filter(rightMatches::contains).toList();
            }

            public String describe() {
                return left.describe() + ".and(" + right.describe() + ")";
            }
        };
    }

    /**
     * {@code child} matches inside each {@code parent} match: tree descendants first, and nodes
     * contained in the parent's bounds when the dump is flat.
     */
    public static Query within(Query child, Query parent) {
        return new Query() {
            public List<MobileNode> evaluate(List<MobileNode> roots) {
                Set<MobileNode> out = identitySet(List.of());
                for (MobileNode p : parent.evaluate(roots)) {
                    out.addAll(matchesInside(p, child, roots));
                }
                return inDocumentOrder(roots, out);
            }

            public String describe() {
                return child.describe() + ".within(" + parent.describe() + ")";
            }
        };
    }

    /** Keeps parent matches whose subtree contains / does not contain text or another query's match. */
    public static Query filter(Query parent, TextMatch hasText, TextMatch hasNotText, Query has, Query hasNot) {
        return new Query() {
            public List<MobileNode> evaluate(List<MobileNode> roots) {
                return parent.evaluate(roots).stream().filter(n -> {
                    List<MobileNode> inside = new ArrayList<>();
                    inside.add(n);
                    inside.addAll(descendantsOf(n, roots));
                    if (hasText != null && inside.stream().noneMatch(d -> hasText.test(d.displayText()))) return false;
                    if (hasNotText != null && inside.stream().anyMatch(d -> hasNotText.test(d.displayText()))) return false;
                    if (has != null && matchesInside(n, has, roots).isEmpty()) return false;
                    return hasNot == null || matchesInside(n, hasNot, roots).isEmpty();
                }).toList();
            }

            public String describe() {
                StringBuilder sb = new StringBuilder(parent.describe()).append(".filter(");
                List<String> parts = new ArrayList<>();
                if (hasText != null) parts.add("hasText=" + hasText);
                if (hasNotText != null) parts.add("hasNotText=" + hasNotText);
                if (has != null) parts.add("has=" + has.describe());
                if (hasNot != null) parts.add("hasNot=" + hasNot.describe());
                return sb.append(String.join(", ", parts)).append(')').toString();
            }

            public boolean positional() {
                return parent.positional();
            }
        };
    }

    // ── role resolution ─────────────────────────────────────────────────

    /**
     * Reduces a native type to the bare name the role table uses: Android's package path and iOS's
     * {@code XCUIElementType} prefix are dropped ({@code android.widget.EditText} → {@code edittext}).
     */
    public static String bareTypeName(String type) {
        String lower = type.toLowerCase(Locale.ROOT);
        String afterPackage = lower.contains(".") ? lower.substring(lower.lastIndexOf('.') + 1) : lower;
        return afterPackage.startsWith("xcuielementtype") ? afterPackage.substring("xcuielementtype".length()) : afterPackage;
    }

    public static boolean matchesRole(MobileNode node, MobileRole role) {
        String bare = bareTypeName(node.type());
        // React Native renders everything as ReactViewGroup: a button only when marked clickable/accessible.
        if (bare.equals("reactviewgroup")) {
            return role == MobileRole.BUTTON
                    && (isTrue(node.raw().get("clickable")) || isTrue(node.raw().get("accessible")));
        }
        return role.nativeTypes().contains(bare);
    }

    private static boolean isTrue(Object v) {
        return Boolean.TRUE.equals(v) || "true".equals(v);
    }

    // ── tree helpers ────────────────────────────────────────────────────

    /** Depth-first, document order. */
    public static List<MobileNode> flatten(List<MobileNode> roots) {
        List<MobileNode> out = new ArrayList<>();
        walk(roots, out);
        return out;
    }

    private static void walk(List<MobileNode> nodes, List<MobileNode> out) {
        for (MobileNode n : nodes) {
            out.add(n);
            walk(n.children(), out);
        }
    }

    /**
     * Matches of {@code query} inside {@code node}: its tree children first; when that finds nothing
     * (flat dumps), every other node contained in its bounds.
     */
    static List<MobileNode> matchesInside(MobileNode node, Query query, List<MobileNode> roots) {
        List<MobileNode> inTree = query.evaluate(node.children());
        return inTree.isEmpty() ? query.evaluate(containedIn(node, roots)) : inTree;
    }

    private static List<MobileNode> containedIn(MobileNode node, List<MobileNode> roots) {
        List<MobileNode> out = new ArrayList<>();
        for (MobileNode n : flatten(roots)) {
            if (n != node && node.bounds().contains(n.bounds())) {
                out.add(n);
            }
        }
        return out;
    }

    /** Tree descendants, or, for a node without children, every other node inside its bounds. */
    static List<MobileNode> descendantsOf(MobileNode node, List<MobileNode> roots) {
        if (!node.children().isEmpty()) {
            return flatten(node.children());
        }
        return containedIn(node, roots);
    }

    private static Set<MobileNode> identitySet(List<MobileNode> nodes) {
        Set<MobileNode> set = Collections.newSetFromMap(new IdentityHashMap<>());
        set.addAll(nodes);
        return set;
    }

    private static List<MobileNode> inDocumentOrder(List<MobileNode> roots, Set<MobileNode> matched) {
        List<MobileNode> out = new ArrayList<>();
        for (MobileNode n : flatten(roots)) {
            if (matched.contains(n)) {
                out.add(n);
            }
        }
        return out;
    }
}
