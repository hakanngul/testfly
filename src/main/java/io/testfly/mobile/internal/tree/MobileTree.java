package io.testfly.mobile.internal.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Immutable forest with deterministic depth-first preorder and occurrence paths. */
public final class MobileTree {

    private final List<MobileNode> roots;
    private final List<Entry> entries;

    public MobileTree(List<MobileNode> roots) {
        this.roots = List.copyOf(roots);
        List<Entry> flattened = new ArrayList<>();
        Deque<Entry> pending = new ArrayDeque<>();
        for (int i = this.roots.size() - 1; i >= 0; i--) {
            pending.push(new Entry(List.of(i), this.roots.get(i)));
        }
        while (!pending.isEmpty()) {
            Entry current = pending.pop();
            flattened.add(current);
            List<MobileNode> children = current.node().children();
            for (int i = children.size() - 1; i >= 0; i--) {
                List<Integer> path = new ArrayList<>(current.path());
                path.add(i);
                pending.push(new Entry(path, children.get(i)));
            }
        }
        entries = List.copyOf(flattened);
    }

    public List<MobileNode> roots() {
        return roots;
    }

    public List<Entry> entries() {
        return entries;
    }

    /**
     * One occurrence in this snapshot. Paths include the root index and each child index.
     * Equal-looking nodes, even the same node object reused twice, have distinct paths.
     * Paths identify occurrences only within a single tree snapshot.
     */
    public record Entry(List<Integer> path, MobileNode node) {

        public Entry {
            path = List.copyOf(path);
            if (node == null || path.isEmpty() || path.stream().anyMatch(index -> index < 0)) {
                throw new IllegalArgumentException("An entry requires a node and a non-negative occurrence path");
            }
        }

        boolean isDescendantOf(Entry ancestor) {
            return path.size() > ancestor.path.size()
                    && path.subList(0, ancestor.path.size()).equals(ancestor.path);
        }

        /** Safe for diagnostics; never includes node content or attribute values. */
        @Override
        public String toString() {
            return "path=" + path + " " + node;
        }
    }
}
