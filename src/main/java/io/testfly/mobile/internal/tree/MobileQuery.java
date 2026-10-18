package io.testfly.mobile.internal.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Immutable query over an already-normalized snapshot, with no device I/O or waiting.
 *
 * <p>Factories match exact, case-sensitive strings, except {@link #textContaining(String)}.
 * Every refinement applies in call order to the current result list. {@code within()} forms
 * a union of proper descendants of all matching containers, preserving tree preorder and
 * including each occurrence once. Positional refinements select from that combined list,
 * not separately per container. Apply {@code within()} before {@code nth()} to select from
 * the scoped union; reversing those calls first selects globally, then checks containment.
 * Container queries are evaluated against the same complete snapshot and may themselves
 * have scopes or positional refinements. Query diagnostics redact all selector values.
 */
public final class MobileQuery {

    private final Predicate<MobileNode> selector;
    private final String description;
    private final List<Refinement> refinements;

    private MobileQuery(Predicate<MobileNode> selector, String description,
                        List<Refinement> refinements) {
        this.selector = selector;
        this.description = description;
        this.refinements = List.copyOf(refinements);
    }

    public static MobileQuery text(String text) {
        Objects.requireNonNull(text, "text");
        return new MobileQuery(node -> text.equals(node.text()), "text(<redacted>, exact)", List.of());
    }

    public static MobileQuery textContaining(String fragment) {
        Objects.requireNonNull(fragment, "fragment");
        return new MobileQuery(node -> contains(node.text(), fragment),
                "text(<redacted>, contains)", List.of());
    }

    public static MobileQuery testId(String testId) {
        Objects.requireNonNull(testId, "testId");
        return new MobileQuery(node -> testId.equals(node.testId()), "testId(<redacted>, exact)", List.of());
    }

    public static MobileQuery type(String type) {
        Objects.requireNonNull(type, "type");
        return new MobileQuery(node -> type.equals(node.type()), "type(<redacted>, exact)", List.of());
    }

    public MobileQuery withTextContaining(String fragment) {
        Objects.requireNonNull(fragment, "fragment");
        return filter("withTextContaining(<redacted>)", node -> contains(node.text(), fragment));
    }

    /** Matches a known flag only; unknown is excluded for both true and false. */
    public MobileQuery withVisible(boolean visible) {
        return filter("withVisible(" + visible + ")", node -> Boolean.valueOf(visible).equals(node.visible()));
    }

    /** Matches a known flag only; unknown is excluded for both true and false. */
    public MobileQuery withEnabled(boolean enabled) {
        return filter("withEnabled(" + enabled + ")", node -> Boolean.valueOf(enabled).equals(node.enabled()));
    }

    public MobileQuery first() {
        return refine("first()", (tree, matches) -> matches.isEmpty() ? List.of() : List.of(matches.getFirst()));
    }

    public MobileQuery last() {
        return refine("last()", (tree, matches) -> matches.isEmpty() ? List.of() : List.of(matches.getLast()));
    }

    /** Zero-based; negative indices fail immediately and out-of-range indices match nothing. */
    public MobileQuery nth(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Query index must be non-negative");
        }
        return refine("nth(" + index + ")",
                (tree, matches) -> index < matches.size() ? List.of(matches.get(index)) : List.of());
    }

    /** Restricts by hierarchy; a container never qualifies as its own descendant. */
    public MobileQuery within(MobileQuery containers) {
        Objects.requireNonNull(containers, "containers");
        return refine("within(" + containers + ")", (tree, matches) -> {
            List<MobileTree.Entry> ancestors = containers.resolve(tree);
            return matches.stream().filter(entry -> ancestors.stream().anyMatch(entry::isDescendantOf)).toList();
        });
    }

    public List<MobileTree.Entry> resolve(MobileTree tree) {
        Objects.requireNonNull(tree, "tree");
        List<MobileTree.Entry> matches = tree.entries().stream().filter(entry -> selector.test(entry.node())).toList();
        for (Refinement refinement : refinements) {
            matches = refinement.apply().apply(tree, matches);
        }
        return matches;
    }

    public MobileTree.Entry resolveSingle(MobileTree tree) {
        List<MobileTree.Entry> matches = resolve(tree);
        if (matches.size() != 1) {
            List<MobileTree.Entry> sample = matches.subList(0, Math.min(matches.size(), 5));
            throw new MobileQueryException("Query " + this + " expected exactly one match but found "
                    + matches.size() + "; candidates=" + sample + (matches.size() > 5 ? " (first 5)" : ""));
        }
        return matches.getFirst();
    }

    @Override
    public String toString() {
        return description;
    }

    private MobileQuery filter(String description, Predicate<MobileNode> predicate) {
        return refine(description,
                (tree, matches) -> matches.stream().filter(entry -> predicate.test(entry.node())).toList());
    }

    private MobileQuery refine(String description,
                               BiFunction<MobileTree, List<MobileTree.Entry>, List<MobileTree.Entry>> action) {
        List<Refinement> updated = new ArrayList<>(refinements);
        updated.add(new Refinement(action));
        return new MobileQuery(selector, this.description + "." + description, updated);
    }

    private static boolean contains(String text, String fragment) {
        return text != null && text.contains(fragment);
    }

    private record Refinement(BiFunction<MobileTree, List<MobileTree.Entry>, List<MobileTree.Entry>> apply) { }
}
