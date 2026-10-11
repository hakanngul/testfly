package io.testfly.accessibility;

import io.testfly.api.TestFlyApi;
import io.testfly.internal.A11yProbe;
import io.testfly.locator.Role;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * A snapshot of the accessibility tree (roles, names and states) that can be printed as
 * YAML and compared with a template, in the style of Playwright's ARIA snapshots.
 *
 * <pre>
 * - navigation "Main":
 *   - link "Home"
 *   - link "Docs"
 * - heading "Welcome" [level=1]
 * - button "Submit" [disabled]
 * </pre>
 *
 * <p><b>Template matching.</b> A template is a constraint, not a full serialization:
 * <ul>
 *   <li>the role must be equal; a quoted name matches as a case-insensitive substring and
 *       {@code /regex/} matches with {@code find()};</li>
 *   <li>every attribute in the template must be present in the page ({@code [checked]},
 *       {@code [level=2]}); extra attributes on the page are ignored;</li>
 *   <li>children match as an ordered subsequence of the direct children, gaps allowed;</li>
 *   <li>the top-level entries match against the children of the captured element, or of
 *       any element below it, so a template may describe just the part you care about.</li>
 * </ul>
 *
 * <p>Only the 36 roles of {@link Role} are reported, plus {@code text}. Other elements are
 * transparent, so their children are hoisted into the parent.
 */
@TestFlyApi(since = "1.1.0")
public final class AriaSnapshot {

    private static final Pattern ENTRY = Pattern.compile(
            "^([a-z]+)(?:\\s+(\"(?:[^\"\\\\]|\\\\.)*\"|/(?:[^/\\\\]|\\\\.)*/i?))?((?:\\s*\\[[^\\]]*\\])*)$");
    private static final Pattern ATTR = Pattern.compile("\\[([^\\]=\\s]+)(?:=([^\\]]*))?\\]");
    private static final int MAX_DIFF_LINES = 200;
    private static final Set<String> ROLES;

    static {
        java.util.HashSet<String> roles = new java.util.HashSet<>();
        for (Role r : Role.values()) {
            roles.add(r.ariaName());
        }
        roles.add("text");
        ROLES = Set.copyOf(roles);
    }

    private AriaSnapshot() {
    }

    /**
     * One node. For nodes captured from a page, {@code name} is the accessible name. For
     * template nodes, {@code name} is a substring to look for or {@code namePattern} is a
     * regular expression; both may be {@code null} to accept any name.
     */
    public record AriaNode(String role, String name, Pattern namePattern, Map<String, Object> attrs,
            List<AriaNode> children) {
    }

    /** Outcome of {@link #match}: whether the template matched and, if not, why. */
    public record MatchResult(boolean matches, String diff) {
    }

    // ------------------------------------------------------------------
    // Capture
    // ------------------------------------------------------------------

    /**
     * Captures the accessibility tree under {@code root}.
     *
     * @param root the element to start from, or {@code null} for the document body
     */
    public static List<AriaNode> capture(WebDriver driver, WebElement root) {
        A11yProbe.Tree tree = A11yProbe.tree(driver, root, A11yProbe.DEFAULT_MAX_DEPTH, A11yProbe.DEFAULT_MAX_NODES);
        return convert(tree.nodes());
    }

    private static List<AriaNode> convert(List<A11yProbe.TreeNode> nodes) {
        List<AriaNode> out = new ArrayList<>();
        for (A11yProbe.TreeNode n : nodes) {
            out.add(new AriaNode(n.role(), n.name(), null, n.attrs(), convert(n.children())));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // YAML output
    // ------------------------------------------------------------------

    /** Prints the tree as YAML that {@link #parseTemplate} can read back. */
    public static String toYaml(List<AriaNode> nodes) {
        StringBuilder sb = new StringBuilder();
        write(nodes, 0, sb);
        return sb.toString();
    }

    private static void write(List<AriaNode> nodes, int indent, StringBuilder sb) {
        for (AriaNode n : nodes) {
            StringBuilder line = new StringBuilder(n.role());
            if (n.name() != null && !n.name().isEmpty()) {
                line.append(" \"").append(n.name().replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            }
            if (n.attrs() != null) {
                for (String key : List.of("level", "checked", "disabled", "expanded", "pressed", "selected")) {
                    Object v = n.attrs().get(key);
                    if (v == null) {
                        continue;
                    }
                    line.append(" [").append(key);
                    if (!Boolean.TRUE.equals(v)) {
                        line.append('=').append(v);
                    }
                    line.append(']');
                }
            }
            boolean hasChildren = n.children() != null && !n.children().isEmpty();
            sb.append("  ".repeat(indent)).append("- ").append(yamlScalar(line.toString()));
            sb.append(hasChildren ? ":" : "").append('\n');
            if (hasChildren) {
                write(n.children(), indent + 1, sb);
            }
        }
    }

    /** Single-quotes a scalar only when plain YAML would misread it. */
    private static String yamlScalar(String s) {
        boolean risky = s.contains(": ") || s.contains(" #") || s.endsWith(":") || s.contains("\t");
        return risky ? "'" + s.replace("'", "''") + "'" : s;
    }

    // ------------------------------------------------------------------
    // Template parsing
    // ------------------------------------------------------------------

    /**
     * Parses a YAML template.
     *
     * @throws IllegalArgumentException if the YAML or an entry is malformed or uses an unknown role
     */
    public static List<AriaNode> parseTemplate(String yaml) {
        Object root;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            root = new Yaml(new SafeConstructor(options)).load(yaml);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid ARIA snapshot template (not valid YAML): " + e.getMessage(), e);
        }
        if (root == null) {
            throw new IllegalArgumentException("Invalid ARIA snapshot template: it is empty.");
        }
        if (!(root instanceof List<?> list)) {
            throw new IllegalArgumentException(
                    "Invalid ARIA snapshot template: expected a YAML list of entries such as '- button \"Save\"'.");
        }
        return parseList(list);
    }

    private static List<AriaNode> parseList(List<?> list) {
        List<AriaNode> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String s) {
                out.add(parseEntry(s, List.of()));
            } else if (item instanceof Map<?, ?> map) {
                if (map.size() != 1) {
                    throw new IllegalArgumentException(
                            "Invalid ARIA snapshot template: an entry with children must have exactly one key, got: "
                                    + map.keySet());
                }
                Map.Entry<?, ?> e = map.entrySet().iterator().next();
                if (!(e.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("Invalid ARIA snapshot template entry: " + e.getKey()
                            + ". Quote element names that YAML reads as booleans or numbers.");
                }
                out.add(parseEntry(key, childrenOf(key, e.getValue())));
            } else {
                throw new IllegalArgumentException("Invalid ARIA snapshot template entry: " + item);
            }
        }
        return out;
    }

    private static List<AriaNode> childrenOf(String key, Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> l) {
            return parseList(l);
        }
        if (value instanceof String text) {
            return List.of(new AriaNode("text", text.trim(), null, Map.of(), List.of()));
        }
        throw new IllegalArgumentException("Invalid ARIA snapshot template entry '" + key + "': children must be a list.");
    }

    private static AriaNode parseEntry(String entry, List<AriaNode> children) {
        Matcher m = ENTRY.matcher(entry.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid ARIA snapshot template entry '" + entry
                    + "'. Expected: role [\"name\" | /regex/] [attr] [attr=value]");
        }
        String role = m.group(1);
        if (!ROLES.contains(role)) {
            throw new IllegalArgumentException("Unknown role '" + role + "' in ARIA snapshot template entry '" + entry
                    + "'. Valid roles: " + ROLES.stream().sorted().collect(Collectors.joining(", ")));
        }
        String name = null;
        Pattern pattern = null;
        String rawName = m.group(2);
        if (rawName != null) {
            if (rawName.startsWith("\"")) {
                name = rawName.substring(1, rawName.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
            } else {
                boolean ci = rawName.endsWith("/i");
                String body = rawName.substring(1, rawName.length() - (ci ? 2 : 1));
                try {
                    pattern = Pattern.compile(body, ci ? Pattern.CASE_INSENSITIVE : 0);
                } catch (PatternSyntaxException e) {
                    throw new IllegalArgumentException("Invalid regular expression in ARIA snapshot template entry '"
                            + entry + "': " + e.getDescription(), e);
                }
            }
        }
        Map<String, Object> attrs = new LinkedHashMap<>();
        Matcher a = ATTR.matcher(m.group(3) == null ? "" : m.group(3));
        while (a.find()) {
            String value = a.group(2);
            attrs.put(a.group(1), value == null ? Boolean.TRUE : coerce(value.trim()));
        }
        return new AriaNode(role, name, pattern, attrs, children);
    }

    private static Object coerce(String v) {
        if (v.equals("true")) {
            return Boolean.TRUE;
        }
        if (v.equals("false")) {
            return Boolean.FALSE;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return v;
        }
    }

    // ------------------------------------------------------------------
    // Matching
    // ------------------------------------------------------------------

    /** Checks the page tree against a template; see the class description for the rules. */
    public static MatchResult match(List<AriaNode> actual, List<AriaNode> template) {
        if (matchesAnywhere(actual, template)) {
            return new MatchResult(true, "");
        }
        StringBuilder diff = new StringBuilder("ARIA snapshot mismatch.\n");
        diff.append(firstUnmatched(actual, template, "the captured element")).append('\n');
        diff.append("--- expected (template) ---\n").append(toYaml(template));
        diff.append("--- actual (captured) ---\n").append(limitLines(toYaml(actual)));
        return new MatchResult(false, diff.toString());
    }

    private static boolean matchesAnywhere(List<AriaNode> actual, List<AriaNode> template) {
        if (matchList(actual, template)) {
            return true;
        }
        for (AriaNode node : actual) {
            if (matchesAnywhere(node.children(), template)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchList(List<AriaNode> actual, List<AriaNode> template) {
        int i = 0;
        for (AriaNode t : template) {
            boolean found = false;
            while (i < actual.size()) {
                if (matchNode(actual.get(i++), t)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchNode(AriaNode a, AriaNode t) {
        return a.role().equals(t.role()) && matchName(a, t) && matchAttrs(a, t)
                && (t.children().isEmpty() || matchList(a.children(), t.children()));
    }

    private static boolean matchName(AriaNode a, AriaNode t) {
        String actual = a.name() == null ? "" : a.name();
        if (t.namePattern() != null) {
            return t.namePattern().matcher(actual).find();
        }
        if (t.name() != null) {
            return actual.toLowerCase(Locale.ROOT).contains(t.name().toLowerCase(Locale.ROOT));
        }
        return true;
    }

    private static boolean matchAttrs(AriaNode a, AriaNode t) {
        for (Map.Entry<String, Object> e : t.attrs().entrySet()) {
            Object have = a.attrs() == null ? null : a.attrs().get(e.getKey());
            if (have == null || !String.valueOf(have).equals(String.valueOf(e.getValue()))) {
                return false;
            }
        }
        return true;
    }

    /** Describes the first template entry that has no match, descending into a same-role candidate. */
    private static String firstUnmatched(List<AriaNode> actual, List<AriaNode> template, String where) {
        int i = 0;
        for (AriaNode t : template) {
            boolean found = false;
            AriaNode sameRole = null;
            while (i < actual.size()) {
                AriaNode a = actual.get(i++);
                if (matchNode(a, t)) {
                    found = true;
                    break;
                }
                if (sameRole == null && a.role().equals(t.role()) && matchName(a, t) && matchAttrs(a, t)) {
                    sameRole = a;
                }
            }
            if (!found) {
                if (sameRole != null) {
                    return firstUnmatched(sameRole.children(), t.children(),
                            sameRole.role() + (sameRole.name() == null ? "" : " \"" + sameRole.name() + "\""));
                }
                return "No match in " + where + " for: " + describe(t) + "\n  found there: " + summary(actual);
            }
        }
        return "No single position in the captured tree satisfies the template, although each entry exists somewhere.";
    }

    private static String describe(AriaNode t) {
        StringBuilder sb = new StringBuilder("- ").append(t.role());
        if (t.namePattern() != null) {
            sb.append(" /").append(t.namePattern().pattern()).append('/');
        } else if (t.name() != null) {
            sb.append(" \"").append(t.name()).append('"');
        }
        t.attrs().forEach((k, v) -> sb.append(" [").append(k).append(Boolean.TRUE.equals(v) ? "" : "=" + v).append(']'));
        return sb.toString();
    }

    private static String summary(List<AriaNode> nodes) {
        if (nodes.isEmpty()) {
            return "(nothing)";
        }
        return nodes.stream().limit(10)
                .map(n -> n.role() + (n.name() == null ? "" : " \"" + n.name() + "\""))
                .collect(Collectors.joining(", ")) + (nodes.size() > 10 ? ", …" : "");
    }

    private static String limitLines(String yaml) {
        String[] lines = yaml.split("\n");
        if (lines.length <= MAX_DIFF_LINES) {
            return yaml;
        }
        return String.join("\n", java.util.Arrays.copyOf(lines, MAX_DIFF_LINES)) + "\n… (" + (lines.length - MAX_DIFF_LINES)
                + " more lines)\n";
    }
}
