package io.testfly.mobile.internal;

import io.testfly.mobile.MobileLocatorException;
import io.testfly.mobile.MobileNode;
import io.testfly.mobile.internal.LocatorEngine.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The common subset of Appium's iOS class chain ({@code iOSClassChain}) on mobilecli's view tree:
 * segments joined by {@code /}, {@code **}{@code /} for any depth, a type or {@code *}, and filters
 * {@code [`predicate`]} ({@link NsPredicate}) or {@code [n]} (1-based, negative from the end), applied
 * in order per parent. Types match with or without the {@code XCUIElementType} prefix.
 */
public final class ClassChainQuery extends Query {

    private record Segment(boolean anyDepth, String type, List<Object> filters) {
    }

    private final String chain;
    private final List<Segment> segments;

    public ClassChainQuery(String chain) {
        this.chain = chain;
        this.segments = parse(chain);
    }

    @Override
    public List<MobileNode> evaluate(List<MobileNode> roots) {
        List<MobileNode> context = null; // null = above the roots
        for (Segment segment : segments) {
            Set<MobileNode> next = Collections.newSetFromMap(new IdentityHashMap<>());
            if (context == null) {
                next.addAll(apply(segment, segment.anyDepth() ? LocatorEngine.flatten(roots) : roots));
            } else {
                for (MobileNode parent : context) {
                    next.addAll(apply(segment, segment.anyDepth() ? LocatorEngine.flatten(parent.children()) : parent.children()));
                }
            }
            context = new ArrayList<>(next);
        }
        Set<MobileNode> matched = Collections.newSetFromMap(new IdentityHashMap<>());
        matched.addAll(context);
        return LocatorEngine.flatten(roots).stream().filter(matched::contains).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<MobileNode> apply(Segment segment, List<MobileNode> candidates) {
        String wanted = LocatorEngine.bareTypeName(segment.type());
        List<MobileNode> out = candidates.stream()
                .filter(n -> segment.type().equals("*") || LocatorEngine.bareTypeName(n.type()).equals(wanted))
                .toList();
        for (Object filter : segment.filters()) {
            if (filter instanceof Integer index) {
                int i = index > 0 ? index - 1 : out.size() + index;
                out = i >= 0 && i < out.size() ? List.of(out.get(i)) : List.of();
            } else {
                out = out.stream().filter((Predicate<MobileNode>) filter).toList();
            }
        }
        return out;
    }

    @Override
    public String describe() {
        return "iosClassChain(\"" + chain + "\")";
    }

    @Override
    public boolean positional() {
        // An index on the last segment picks one element, which lifts strict mode like nth().
        return segments.get(segments.size() - 1).filters().stream().anyMatch(f -> f instanceof Integer);
    }

    private static List<Segment> parse(String chain) {
        if (chain == null || chain.isBlank()) {
            throw invalid(chain, "it is blank");
        }
        List<Segment> out = new ArrayList<>();
        boolean anyDepth = false;
        int i = 0;
        String s = chain.trim();
        while (i < s.length()) {
            if (s.startsWith("**/", i)) {
                anyDepth = true;
                i += 3;
                continue;
            }
            int start = i;
            while (i < s.length() && s.charAt(i) != '/' && s.charAt(i) != '[') {
                i++;
            }
            String type = s.substring(start, i).trim();
            if (type.isEmpty()) {
                throw invalid(chain, "a segment has no type (use * for any)");
            }
            List<Object> filters = new ArrayList<>();
            while (i < s.length() && s.charAt(i) == '[') {
                int close;
                if (i + 1 < s.length() && s.charAt(i + 1) == '`') {
                    int end = s.indexOf('`', i + 2);
                    if (end < 0 || end + 1 >= s.length() || s.charAt(end + 1) != ']') {
                        throw invalid(chain, "unterminated [`predicate`]");
                    }
                    filters.add(NsPredicate.parse(s.substring(i + 2, end)));
                    close = end + 1;
                } else {
                    close = s.indexOf(']', i);
                    if (close < 0) {
                        throw invalid(chain, "unterminated [index]");
                    }
                    String index = s.substring(i + 1, close).trim();
                    try {
                        int n = Integer.parseInt(index);
                        if (n == 0) {
                            throw invalid(chain, "indexes start at 1 (negative counts from the end)");
                        }
                        filters.add(n);
                    } catch (NumberFormatException e) {
                        throw invalid(chain, "'[" + index + "]' is neither an index nor a [`predicate`]");
                    }
                }
                i = close + 1;
            }
            out.add(new Segment(anyDepth, type, filters));
            anyDepth = false;
            if (i < s.length()) {
                if (s.charAt(i) != '/') {
                    throw invalid(chain, "unexpected '" + s.charAt(i) + "'");
                }
                i++;
                if (i == s.length()) {
                    throw invalid(chain, "it ends with /");
                }
            }
        }
        if (out.isEmpty()) {
            throw invalid(chain, "it has no segment");
        }
        return out;
    }

    private static MobileLocatorException invalid(String chain, String reason) {
        return new MobileLocatorException("Invalid iOS class chain \"" + chain + "\": " + reason);
    }
}
