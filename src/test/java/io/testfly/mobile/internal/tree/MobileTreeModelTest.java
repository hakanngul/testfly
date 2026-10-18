package io.testfly.mobile.internal.tree;

import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;

public class MobileTreeModelTest {

    @Test
    public void rejectsNonFiniteNegativeAndOverflowingBounds() {
        assertThrows(IllegalArgumentException.class, () -> new MobileBounds(Double.NaN, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MobileBounds(0, Double.POSITIVE_INFINITY, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MobileBounds(0, 0, Double.NEGATIVE_INFINITY, 1));
        assertThrows(IllegalArgumentException.class, () -> new MobileBounds(0, 0, 1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new MobileBounds(0, 0, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new MobileBounds(0, 0, 1, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new MobileBounds(Double.MAX_VALUE, 0, Double.MAX_VALUE, 1));
        assertEquals(new MobileBounds(-5, -10, 0, 0).x(), -5.0);
    }

    @Test
    public void defensivelyCopiesAttributesChildrenAndRoots() {
        MobileNode child = node("child");
        List<MobileNode> children = new ArrayList<>(List.of(child));
        Map<String, String> attributes = new HashMap<>(Map.of("label", "original"));
        MobileNode parent = new MobileNode("group", null, null, null, null, null, attributes, children);
        List<MobileNode> roots = new ArrayList<>(List.of(parent));
        MobileTree tree = new MobileTree(roots);

        children.clear();
        attributes.put("label", "changed");
        roots.clear();

        assertEquals(parent.attributes(), Map.of("label", "original"));
        assertEquals(parent.children(), List.of(child));
        assertEquals(tree.roots(), List.of(parent));
        assertThrows(UnsupportedOperationException.class, () -> parent.children().clear());
        assertThrows(UnsupportedOperationException.class, () -> parent.attributes().clear());
        assertThrows(UnsupportedOperationException.class, () -> tree.roots().clear());
        assertThrows(UnsupportedOperationException.class, () -> tree.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> tree.entries().getFirst().path().clear());
    }

    @Test
    public void preservesHierarchyAndPreorderAcrossRoots() {
        MobileNode leaf = node("leaf");
        MobileNode branch = new MobileNode("group", "branch", null, null, null, null, Map.of(), List.of(leaf));
        MobileTree tree = new MobileTree(List.of(branch, node("second-root")));

        assertEquals(tree.entries().stream().map(entry -> entry.node().text()).toList(),
                List.of("branch", "leaf", "second-root"));
        assertEquals(tree.entries().stream().map(MobileTree.Entry::path).toList(),
                List.of(List.of(0), List.of(0, 0), List.of(1)));
        assertSame(tree.roots().getFirst().children().getFirst(), leaf);
        assertNull(leaf.bounds());
        assertNull(leaf.visible());
        assertNull(leaf.enabled());
    }

    @Test
    public void repeatedNodeObjectsRemainSeparateOccurrences() {
        MobileNode repeated = node("same");
        MobileTree tree = new MobileTree(List.of(repeated, repeated));

        assertSame(tree.entries().get(0).node(), tree.entries().get(1).node());
        assertNotSame(tree.entries().get(0), tree.entries().get(1));
        assertEquals(tree.entries().get(0).path(), List.of(0));
        assertEquals(tree.entries().get(1).path(), List.of(1));
    }

    private static MobileNode node(String text) {
        return new MobileNode("text", text, null, null, null, null, Map.of(), List.of());
    }
}
