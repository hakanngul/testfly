package io.testfly.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dependency-free JSONPath subset used by {@link ApiResponse}.
 *
 * <p>Supported syntax: {@code $} (root, optional), {@code .name}, {@code ['name']} / {@code ["name"]}
 * (keys containing dots, slashes, tildes or spaces), {@code [0]}, {@code [*]} and {@code .*}.
 * Filter expressions ({@code [?(...)]}), slices and recursive descent are not supported.
 *
 * <p>A path without wildcards resolves to a single node or {@link MissingNode}. A path with a
 * wildcard resolves to an {@link ArrayNode} holding every match (possibly empty).
 */
final class JsonPathResolver {

    private JsonPathResolver() {
    }

    private sealed interface Segment permits Name, Index, Wildcard {
    }

    private record Name(String value) implements Segment {
    }

    private record Index(int value) implements Segment {
    }

    private record Wildcard() implements Segment {
    }

    static JsonNode resolve(JsonNode root, String path) {
        Objects.requireNonNull(path, "JSON path");
        List<Segment> segments = parse(path);
        boolean multi = false;
        List<JsonNode> cursor = new ArrayList<>(List.of(root));
        for (Segment segment : segments) {
            List<JsonNode> next = new ArrayList<>();
            for (JsonNode node : cursor) {
                if (segment instanceof Wildcard) {
                    if (node.isContainerNode()) node.elements().forEachRemaining(next::add);
                } else {
                    JsonNode child = step(node, segment);
                    if (child != null && !child.isMissingNode()) next.add(child);
                }
            }
            if (segment instanceof Wildcard) multi = true;
            if (!multi && next.isEmpty()) return MissingNode.getInstance();
            cursor = next;
        }
        if (!multi) return cursor.get(0);
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        result.addAll(cursor);
        return result;
    }

    private static JsonNode step(JsonNode node, Segment segment) {
        if (segment instanceof Index index) {
            if (node.isArray()) return node.get(index.value());
            if (node.isObject()) return node.get(String.valueOf(index.value()));
            return null;
        }
        String name = ((Name) segment).value();
        if (node.isObject()) return node.get(name);
        if (node.isArray() && isDigits(name)) return node.get(Integer.parseInt(name));
        return null;
    }

    private static List<Segment> parse(String path) {
        List<Segment> segments = new ArrayList<>();
        int i = 0;
        int n = path.length();
        if (i < n && path.charAt(i) == '$') i++;
        boolean first = true;
        while (i < n) {
            char c = path.charAt(i);
            if (c == '.') {
                i++;
                if (i < n && path.charAt(i) == '.') throw unsupported(path, "recursive descent '..'");
                if (i < n && path.charAt(i) == '*') {
                    segments.add(new Wildcard());
                    i++;
                } else {
                    int end = nameEnd(path, i);
                    if (end == i) throw invalid(path, i, "empty name");
                    segments.add(new Name(path.substring(i, end)));
                    i = end;
                }
            } else if (c == '[') {
                i = bracket(path, i + 1, segments);
            } else if (first) {
                // Legacy form without leading "$." e.g. "user.name"
                int end = nameEnd(path, i);
                segments.add(new Name(path.substring(i, end)));
                i = end;
            } else {
                throw invalid(path, i, "expected '.' or '['");
            }
            first = false;
        }
        return segments;
    }

    private static int bracket(String path, int i, List<Segment> segments) {
        int n = path.length();
        if (i >= n) throw invalid(path, i, "unterminated '['");
        char c = path.charAt(i);
        if (c == '?' || c == '(') throw unsupported(path, "filter/script expressions");
        if (c == '*') {
            expect(path, i + 1, ']');
            segments.add(new Wildcard());
            return i + 2;
        }
        if (c == '\'' || c == '"') {
            StringBuilder name = new StringBuilder();
            int j = i + 1;
            while (j < n && path.charAt(j) != c) {
                char ch = path.charAt(j);
                if (ch == '\\' && j + 1 < n) {
                    name.append(path.charAt(j + 1));
                    j += 2;
                } else {
                    name.append(ch);
                    j++;
                }
            }
            if (j >= n) throw invalid(path, i, "unterminated quoted name");
            expect(path, j + 1, ']');
            segments.add(new Name(name.toString()));
            return j + 2;
        }
        int j = i;
        while (j < n && Character.isDigit(path.charAt(j))) j++;
        if (j == i) {
            if (path.indexOf(':', i) > 0 || c == '-') throw unsupported(path, "slices and negative indexes");
            throw invalid(path, i, "expected index, '*' or quoted name");
        }
        expect(path, j, ']');
        segments.add(new Index(Integer.parseInt(path.substring(i, j))));
        return j + 1;
    }

    private static int nameEnd(String path, int i) {
        int j = i;
        while (j < path.length() && path.charAt(j) != '.' && path.charAt(j) != '[') j++;
        return j;
    }

    private static void expect(String path, int i, char ch) {
        if (i >= path.length() || path.charAt(i) != ch) throw invalid(path, i, "expected '" + ch + "'");
    }

    private static boolean isDigits(String s) {
        if (s.isEmpty()) return false;
        for (int k = 0; k < s.length(); k++) if (!Character.isDigit(s.charAt(k))) return false;
        return true;
    }

    private static IllegalArgumentException invalid(String path, int pos, String reason) {
        return new IllegalArgumentException("[ApiResponse] Invalid JSON path '" + path + "' at " + pos + ": " + reason);
    }

    private static IllegalArgumentException unsupported(String path, String feature) {
        return new IllegalArgumentException("[ApiResponse] JSON path '" + path + "' uses " + feature
                + ", which is not supported. Supported: $, .name, ['name'], [0], [*], .*");
    }
}
