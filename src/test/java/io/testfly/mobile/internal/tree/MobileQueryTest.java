package io.testfly.mobile.internal.tree;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

public class MobileQueryTest {

    @Test
    public void exactQueriesAndContainsAreCaseSensitiveAndExcludeAbsentValues() {
        MobileTree tree = new MobileTree(List.of(node("Button", "Save", "save"),
                node("Button", "Save draft", "save-draft"), node("button", "save", null),
                node(null, null, null)));

        assertEquals(paths(MobileQuery.text("Save"), tree), List.of(List.of(0)));
        assertEquals(paths(MobileQuery.textContaining("Save"), tree), List.of(List.of(0), List.of(1)));
        assertEquals(paths(MobileQuery.testId("save"), tree), List.of(List.of(0)));
        assertEquals(paths(MobileQuery.type("Button").withTextContaining("draft"), tree), List.of(List.of(1)));
        assertEquals(MobileQuery.text("").resolve(tree).size(), 0);
        assertThrows(NullPointerException.class, () -> MobileQuery.text(null));
        assertThrows(NullPointerException.class, () -> MobileQuery.textContaining(null));
        assertThrows(NullPointerException.class, () -> MobileQuery.testId(null));
        assertThrows(NullPointerException.class, () -> MobileQuery.type(null));
    }

    @Test
    public void overlappingSiblingBoundsDoNotImplyContainmentAndContainerIsExcluded() {
        MobileBounds bounds = new MobileBounds(0, 0, 100, 100);
        MobileNode child = node("Button", "child", null);
        MobileNode container = new MobileNode("Button", "container", "panel", bounds,
                true, true, Map.of(), List.of(child));
        MobileNode overlappingSibling = new MobileNode("Button", "outside", null, bounds,
                true, true, Map.of(), List.of());
        MobileTree tree = new MobileTree(List.of(container, overlappingSibling));

        assertEquals(paths(MobileQuery.type("Button").within(MobileQuery.testId("panel")), tree),
                List.of(List.of(0, 0)));
        assertTrue(MobileQuery.testId("panel").within(MobileQuery.testId("panel")).resolve(tree).isEmpty());
    }

    @Test
    public void overlappingContainerScopesUnionOccurrencesOnceInTreePreorder() {
        MobileNode inner = node("Panel", "inner", null, node("Button", "first", null));
        MobileNode outer = node("Panel", "outer", null, inner, node("Button", "second", null));
        MobileNode other = node("Panel", "other", null, node("Button", "third", null));
        MobileTree tree = new MobileTree(List.of(outer, other));
        MobileQuery query = MobileQuery.type("Button").within(MobileQuery.type("Panel"));

        assertEquals(paths(query, tree), List.of(List.of(0, 0, 0), List.of(0, 1), List.of(1, 0)));
        assertEquals(query.first().resolveSingle(tree).node().text(), "first");
        assertEquals(query.nth(1).resolveSingle(tree).node().text(), "second");
        assertEquals(query.last().resolveSingle(tree).node().text(), "third");
        assertEquals(query.resolve(tree).size(), 3, "Positional refinements must not mutate their source query");
        assertEquals(paths(MobileQuery.type("Panel").within(MobileQuery.type("Panel")), tree),
                List.of(List.of(0, 0)), "An inner container remains a proper descendant of the outer container");
    }

    @Test
    public void positionalRefinementsApplyAtTheirCallPositionToTheCombinedList() {
        MobileTree tree = new MobileTree(List.of(node("Button", "outside", null),
                node("Panel", "panel", "scope", node("Button", "inside", null))));
        MobileQuery buttons = MobileQuery.type("Button");
        MobileQuery scope = MobileQuery.testId("scope");

        assertTrue(buttons.first().within(scope).resolve(tree).isEmpty());
        assertEquals(buttons.within(scope).first().resolveSingle(tree).node().text(), "inside");
        assertEquals(buttons.nth(1).within(scope).resolveSingle(tree).node().text(), "inside");
        assertTrue(buttons.within(scope).nth(1).resolve(tree).isEmpty());
        assertEquals(buttons.first().last().resolveSingle(tree).node().text(), "outside");
    }

    @Test
    public void nestedContainerQueriesUseTheSameSnapshotAndTheirOwnScope() {
        MobileNode outsideRow = node("Row", "outside", null, node("Button", "outside", null));
        MobileNode list = node("Panel", "list", "list",
                node("Row", "first-row", null, node("Button", "first", null)),
                node("Row", "second-row", null, node("Button", "second", null)));
        MobileTree tree = new MobileTree(List.of(outsideRow, list));
        MobileQuery row = MobileQuery.type("Row").within(MobileQuery.testId("list")).nth(1);

        assertEquals(MobileQuery.type("Button").within(row).resolveSingle(tree).node().text(), "second");
        assertTrue(MobileQuery.type("Button").within(MobileQuery.testId("missing")).resolve(tree).isEmpty());
    }

    @Test
    public void equalLookingNodesKeepTheirDistinctPathsAndStrictness() {
        MobileNode first = node("Button", "same", "same-id");
        MobileNode second = node("Button", "same", "same-id");
        MobileTree tree = new MobileTree(List.of(node("Panel", null, null, first, second, first)));
        MobileQuery query = MobileQuery.testId("same-id").within(MobileQuery.type("Panel"));

        assertEquals(first, second);
        assertEquals(query.resolve(tree).size(), 3);
        assertNotEquals(query.resolve(tree).get(0).path(), query.resolve(tree).get(1).path());
        assertSame(query.nth(2).resolveSingle(tree).node(), first);
        assertThrows(MobileQueryException.class, () -> query.resolveSingle(tree));
    }

    @Test
    public void unknownFlagsAreDifferentFromKnownFalse() {
        MobileNode unknown = new MobileNode("Button", "unknown", null, null, null, null, Map.of(), List.of());
        MobileNode no = new MobileNode("Button", "no", null, null, false, false, Map.of(), List.of());
        MobileNode yes = new MobileNode("Button", "yes", null, null, true, true, Map.of(), List.of());
        MobileTree tree = new MobileTree(List.of(unknown, no, yes));

        assertEquals(paths(MobileQuery.type("Button").withVisible(false), tree), List.of(List.of(1)));
        assertEquals(paths(MobileQuery.type("Button").withVisible(true), tree), List.of(List.of(2)));
        assertEquals(paths(MobileQuery.type("Button").withEnabled(false), tree), List.of(List.of(1)));
        assertEquals(paths(MobileQuery.type("Button").withEnabled(true), tree), List.of(List.of(2)));
        assertEquals(MobileQuery.type("Button").resolve(tree).size(), 3);
    }

    @Test
    public void invalidPositionsAndEmptyResultsHavePredictableBehavior() {
        MobileTree tree = new MobileTree(List.of(node("Button", "one", null)));
        MobileQuery query = MobileQuery.type("Button");

        assertThrows(IllegalArgumentException.class, () -> query.nth(-1));
        assertTrue(query.nth(1).resolve(tree).isEmpty());
        assertTrue(query.nth(Integer.MAX_VALUE).resolve(tree).isEmpty());
        assertTrue(MobileQuery.text("missing").first().last().resolve(tree).isEmpty());
        assertTrue(query.resolve(new MobileTree(List.of())).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> query.resolve(tree).clear());
        assertThrows(NullPointerException.class, () -> query.within(null));
        assertThrows(NullPointerException.class, () -> query.resolve(null));
    }

    @Test
    public void strictErrorsAndStringRepresentationsRedactSelectorsAndAllNodeContent() {
        String textSecret = "password-value-34";
        MobileNode secret = new MobileNode("sensitive-type", textSecret, "sensitive-id", null,
                true, null, Map.of("password", "attribute-secret"), List.of());
        MobileTree tree = new MobileTree(List.of(node("Panel", "sensitive-label", "container-secret", secret, secret)));
        MobileQuery query = MobileQuery.text(textSecret).withTextContaining("password")
                .within(MobileQuery.testId("container-secret"));
        String message = expectThrows(MobileQueryException.class, () -> query.resolveSingle(tree)).getMessage();

        assertTrue(message.contains("text(<redacted>, exact)"));
        assertTrue(message.contains("found 2"));
        assertTrue(message.contains("path=[0, 0]"));
        assertTrue(message.contains("path=[0, 1]"));
        for (String forbidden : List.of(textSecret, "sensitive-type", "sensitive-id", "sensitive-label",
                "container-secret", "attribute-secret", "password")) {
            assertFalse(message.contains(forbidden), message);
            assertFalse(secret.toString().contains(forbidden));
            assertFalse(tree.entries().getFirst().toString().contains(forbidden));
        }
        String empty = expectThrows(MobileQueryException.class,
                () -> MobileQuery.text("missing-secret").resolveSingle(tree)).getMessage();
        assertTrue(empty.contains("found 0"));
        assertTrue(empty.contains("candidates=[]"));
        assertFalse(empty.contains("missing-secret"));
    }

    @Test
    public void strictFailureCapsCandidateSummaries() {
        MobileNode button = node("Button", "text", null);
        MobileTree tree = new MobileTree(List.of(button, button, button, button, button, button));
        String message = expectThrows(MobileQueryException.class,
                () -> MobileQuery.type("Button").resolveSingle(tree)).getMessage();

        assertTrue(message.contains("found 6"));
        assertTrue(message.contains("(first 5)"));
        assertFalse(message.contains("path=[5]"));
    }

    private static List<List<Integer>> paths(MobileQuery query, MobileTree tree) {
        return query.resolve(tree).stream().map(MobileTree.Entry::path).toList();
    }

    private static MobileNode node(String type, String text, String testId, MobileNode... children) {
        return new MobileNode(type, text, testId, null, null, null, Map.of(), List.of(children));
    }
}
