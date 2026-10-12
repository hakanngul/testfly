package io.testfly.unit.accessibility;

import io.testfly.accessibility.AriaSnapshot;
import io.testfly.accessibility.AriaSnapshot.AriaNode;
import io.testfly.accessibility.AriaSnapshot.MatchResult;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

/** Unit tests for {@link AriaSnapshot}: template parsing, YAML output and matching. No browser. */
public class AriaSnapshotTest {

    private static AriaNode node(String role, String name, Map<String, Object> attrs, AriaNode... children) {
        return new AriaNode(role, name, null, attrs, List.of(children));
    }

    private static AriaNode node(String role, String name, AriaNode... children) {
        return node(role, name, Map.of(), children);
    }

    private static Map<String, Object> attrs(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static List<AriaNode> page() {
        return List.of(
                node("navigation", "Main",
                        node("link", "Home"),
                        node("link", "Documentation"),
                        node("link", "Pricing")),
                node("heading", "Welcome back", attrs("level", 1)),
                node("list", null,
                        node("listitem", null, node("checkbox", "Milk", attrs("checked", true))),
                        node("listitem", null, node("checkbox", "Eggs"))),
                node("button", "Submit", attrs("disabled", true)));
    }

    // ---------------------------------------------------------------- parsing

    @Test
    public void parse_leafNameAndAttributes() {
        List<AriaNode> t = AriaSnapshot.parseTemplate("- heading \"Welcome\" [level=1]\n- button [disabled]");
        assertEquals(t.size(), 2);
        assertEquals(t.get(0).role(), "heading");
        assertEquals(t.get(0).name(), "Welcome");
        assertEquals(t.get(0).attrs().get("level"), 1);
        assertEquals(t.get(1).attrs().get("disabled"), Boolean.TRUE);
        assertNull(t.get(1).name());
    }

    @Test
    public void parse_nestedChildren() {
        List<AriaNode> t = AriaSnapshot.parseTemplate("- navigation \"Main\":\n  - link \"Home\"\n  - link \"Docs\"");
        assertEquals(t.size(), 1);
        assertEquals(t.get(0).children().size(), 2);
        assertEquals(t.get(0).children().get(1).name(), "Docs");
    }

    @Test
    public void parse_regexName_andCaseInsensitiveFlag() {
        AriaNode n = AriaSnapshot.parseTemplate("- heading /Issues \\d+/i").get(0);
        assertNotNull(n.namePattern());
        assertTrue(n.namePattern().matcher("issues 12").find());
    }

    @Test
    public void parse_stringValueBecomesTextChild() {
        AriaNode n = AriaSnapshot.parseTemplate("- paragraph: Hello there").get(0);
        assertEquals(n.children().get(0).role(), "text");
        assertEquals(n.children().get(0).name(), "Hello there");
    }

    @Test
    public void parse_unknownRole_listsValidRoles() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> AriaSnapshot.parseTemplate("- bogus \"x\""));
        assertTrue(e.getMessage().contains("Unknown role 'bogus'"), e.getMessage());
        assertTrue(e.getMessage().contains("button"), e.getMessage());
    }

    @Test
    public void parse_rejectsEmptyNonListAndBadEntries() {
        expectThrows(IllegalArgumentException.class, () -> AriaSnapshot.parseTemplate(""));
        expectThrows(IllegalArgumentException.class, () -> AriaSnapshot.parseTemplate("just: a map"));
        expectThrows(IllegalArgumentException.class, () -> AriaSnapshot.parseTemplate("- button 'single quotes'"));
        expectThrows(IllegalArgumentException.class, () -> AriaSnapshot.parseTemplate("- heading /[unclosed/"));
        expectThrows(IllegalArgumentException.class, () -> AriaSnapshot.parseTemplate("- [not valid yaml"));
    }

    @Test
    public void parse_rejectsDuplicateKeys() {
        expectThrows(IllegalArgumentException.class, () -> AriaSnapshot.parseTemplate(
                "- list:\n  - listitem\n  list: x"));
    }

    // ---------------------------------------------------------------- YAML output

    @Test
    public void toYaml_usesPlaywrightStyleLines() {
        String yaml = AriaSnapshot.toYaml(page());
        assertTrue(yaml.contains("- navigation \"Main\":\n  - link \"Home\"\n"), yaml);
        assertTrue(yaml.contains("- heading \"Welcome back\" [level=1]\n"), yaml);
        assertTrue(yaml.contains("    - checkbox \"Milk\" [checked]\n"), yaml);
        assertTrue(yaml.contains("- button \"Submit\" [disabled]\n"), yaml);
        assertTrue(yaml.contains("- list:\n"), yaml);
    }

    @Test
    public void toYaml_roundTripsThroughParseTemplate() {
        List<AriaNode> tree = List.of(
                node("navigation", "A: B # C", node("link", "He said \"hi\"")),
                node("heading", "Back\\slash", attrs("level", 2)));
        List<AriaNode> parsed = AriaSnapshot.parseTemplate(AriaSnapshot.toYaml(tree));

        assertTrue(AriaSnapshot.match(tree, parsed).matches(), AriaSnapshot.match(tree, parsed).diff());
        assertEquals(parsed.get(0).name(), "A: B # C");
        assertEquals(parsed.get(0).children().get(0).name(), "He said \"hi\"");
        assertEquals(parsed.get(1).name(), "Back\\slash");
    }

    // ---------------------------------------------------------------- matching

    private static boolean matches(String template) {
        return AriaSnapshot.match(page(), AriaSnapshot.parseTemplate(template)).matches();
    }

    @Test
    public void match_partialTemplate_ignoresExtraChildren() {
        assertTrue(matches("- navigation:\n  - link \"Home\"\n  - link \"Pricing\""));
    }

    @Test
    public void match_nameIsCaseInsensitiveSubstring() {
        assertTrue(matches("- link \"docum\""));
        assertTrue(matches("- heading \"welcome\""));
    }

    @Test
    public void match_regexName() {
        assertTrue(matches("- link /^Doc.*ion$/"));
        assertFalse(matches("- link /^Pric$/"));
    }

    @Test
    public void match_childrenMustKeepTheirOrder() {
        assertFalse(matches("- navigation:\n  - link \"Pricing\"\n  - link \"Home\""));
    }

    @Test
    public void match_attributesMustBePresent() {
        assertTrue(matches("- heading [level=1]"));
        assertFalse(matches("- heading [level=2]"));
        assertTrue(matches("- checkbox \"Milk\" [checked]"));
        assertFalse(matches("- checkbox \"Eggs\" [checked]"));
        assertTrue(matches("- button [disabled]"));
    }

    @Test
    public void match_topLevelEntriesMayDescribeAnyDepth() {
        // The two checkboxes sit in different list items, so they are not siblings.
        assertFalse(matches("- checkbox \"Milk\"\n- checkbox \"Eggs\""));
        assertTrue(matches("- listitem\n- listitem"));
        assertTrue(matches("- listitem:\n  - checkbox \"Milk\""));
    }

    @Test
    public void match_topLevelEntriesKeepTheirOrder() {
        assertTrue(matches("- navigation\n- button"));
        assertFalse(matches("- button\n- navigation"));
    }

    @Test
    public void match_wrongRole_fails() {
        assertFalse(matches("- button \"Home\""));
    }

    @Test
    public void match_missingNode_reportsWhereAndWhatWasFound() {
        MatchResult r = AriaSnapshot.match(page(),
                AriaSnapshot.parseTemplate("- navigation \"Main\":\n  - link \"Contact\""));

        assertFalse(r.matches());
        assertTrue(r.diff().contains("navigation \"Main\""), r.diff());
        assertTrue(r.diff().contains("- link \"Contact\""), r.diff());
        assertTrue(r.diff().contains("link \"Home\""), r.diff());
        assertTrue(r.diff().contains("--- expected (template) ---"), r.diff());
        assertTrue(r.diff().contains("--- actual (captured) ---"), r.diff());
    }

    @Test
    public void match_emptyTemplateList_matchesAnything() {
        assertTrue(AriaSnapshot.match(page(), List.of()).matches());
    }

    @Test
    public void diff_isLimitedToTwoHundredActualLines() {
        AriaNode[] many = new AriaNode[400];
        for (int i = 0; i < many.length; i++) {
            many[i] = node("link", "L" + i);
        }
        MatchResult r = AriaSnapshot.match(List.of(node("list", null, many)),
                AriaSnapshot.parseTemplate("- button \"Nope\""));
        assertFalse(r.matches());
        assertTrue(r.diff().contains("more lines)"), "long snapshots must be truncated");
    }
}
