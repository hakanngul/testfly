package io.testfly.mobile.internal;

import io.testfly.mobile.MobileLocatorException;
import io.testfly.mobile.MobileNode;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The common subset of iOS NSPredicate strings (Appium's {@code iOSNsPredicateString}), evaluated on
 * mobilecli's view tree: {@code attr OP value} joined with {@code AND}/{@code OR}/{@code NOT} and
 * parentheses. Operators: {@code ==}, {@code !=}, {@code BEGINSWITH}, {@code ENDSWITH},
 * {@code CONTAINS}, {@code LIKE} ({@code *}/{@code ?}), {@code MATCHES} (regex), with {@code [c]},
 * {@code [d]}, {@code [cd]}. Attributes: {@code name}, {@code label}, {@code value}, {@code type},
 * {@code identifier}, {@code text}, {@code placeholderValue}, {@code enabled}, {@code visible},
 * {@code selected}, {@code checked}, {@code focused}/{@code hasFocus}.
 */
public final class NsPredicate {

    private final String source;
    private final List<String> tokens;
    private int pos;

    private NsPredicate(String source) {
        this.source = source;
        this.tokens = tokenize(source);
    }

    public static Predicate<MobileNode> parse(String predicate) {
        if (predicate == null || predicate.isBlank()) {
            throw new MobileLocatorException("Invalid iOS predicate: it is blank");
        }
        NsPredicate p = new NsPredicate(predicate);
        Predicate<MobileNode> result = p.or();
        if (p.pos != p.tokens.size()) {
            throw p.error("unexpected '" + p.tokens.get(p.pos) + "'");
        }
        return result;
    }

    // ── grammar: or := and (OR and)* ; and := not (AND not)* ; not := NOT not | primary ──

    private Predicate<MobileNode> or() {
        Predicate<MobileNode> left = and();
        while (accept("OR") || accept("||")) {
            left = left.or(and());
        }
        return left;
    }

    private Predicate<MobileNode> and() {
        Predicate<MobileNode> left = not();
        while (accept("AND") || accept("&&")) {
            left = left.and(not());
        }
        return left;
    }

    private Predicate<MobileNode> not() {
        if (accept("NOT") || accept("!")) {
            return not().negate();
        }
        if (accept("(")) {
            Predicate<MobileNode> inner = or();
            expect(")");
            return inner;
        }
        return comparison();
    }

    private Predicate<MobileNode> comparison() {
        String attr = next("an attribute");
        if (!Character.isLetter(attr.charAt(0))) {
            throw error("expected an attribute, got '" + attr + "'");
        }
        String op = next("an operator").toUpperCase(Locale.ROOT);
        boolean caseInsensitive = false;
        boolean diacriticInsensitive = false;
        if (pos < tokens.size() && tokens.get(pos).startsWith("[")) {
            String mod = tokens.get(pos++).toLowerCase(Locale.ROOT);
            caseInsensitive = mod.contains("c");
            diacriticInsensitive = mod.contains("d");
        }
        String expected = literal(next("a value"));
        boolean ci = caseInsensitive;
        boolean di = diacriticInsensitive;
        Predicate<String> test = switch (op) {
            case "==", "=" -> a -> fold(a, ci, di).equals(fold(expected, ci, di));
            case "!=", "<>" -> a -> !fold(a, ci, di).equals(fold(expected, ci, di));
            case "BEGINSWITH" -> a -> fold(a, ci, di).startsWith(fold(expected, ci, di));
            case "ENDSWITH" -> a -> fold(a, ci, di).endsWith(fold(expected, ci, di));
            case "CONTAINS" -> a -> fold(a, ci, di).contains(fold(expected, ci, di));
            case "LIKE" -> likePattern(expected, ci)::matches;
            case "MATCHES" -> regex(expected, ci)::matches;
            default -> throw error("unsupported operator '" + op + "'");
        };
        String key = attr;
        return node -> test.test(attribute(node, key));
    }

    private static Matcher likePattern(String glob, boolean ci) {
        StringBuilder sb = new StringBuilder();
        for (char c : glob.toCharArray()) {
            sb.append(c == '*' ? ".*" : c == '?' ? "." : Pattern.quote(String.valueOf(c)));
        }
        return regex(sb.toString(), ci);
    }

    private static Matcher regex(String regex, boolean ci) {
        Pattern p = Pattern.compile(regex, ci ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0);
        return s -> p.matcher(s).matches();
    }

    private interface Matcher {
        boolean matches(String s);
    }

    private static String fold(String s, boolean ci, boolean di) {
        String out = s;
        if (di) {
            out = Normalizer.normalize(out, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        }
        return ci ? out.toLowerCase(Locale.ROOT) : out;
    }

    /** The attribute as a string; booleans as {@code true}/{@code false}, a missing one as empty. */
    public static String attribute(MobileNode n, String name) {
        String v = switch (name) {
            case "name" -> n.name() != null ? n.name() : n.identifier();
            case "label" -> n.label();
            case "value" -> n.value();
            case "type", "elementType" -> appiumType(n);
            case "identifier", "rawIdentifier", "accessibilityIdentifier" -> n.identifier();
            case "text", "title" -> n.text();
            case "placeholderValue", "placeholder" -> n.placeholder();
            case "enabled", "isEnabled" -> String.valueOf(n.enabled());
            case "visible", "isVisible" -> String.valueOf(n.visible());
            case "selected", "isSelected" -> String.valueOf(n.selected());
            case "checked", "isChecked" -> String.valueOf(n.checked());
            case "focused", "hasFocus" -> String.valueOf(n.focused());
            default -> {
                Object raw = n.raw().get(name);
                yield raw == null ? null : String.valueOf(raw);
            }
        };
        return v == null ? "" : v;
    }

    /** iOS types as Appium names them ({@code XCUIElementTypeButton}); Android classes as reported. */
    static String appiumType(MobileNode n) {
        String t = n.type();
        return t.contains(".") || t.startsWith("XCUIElementType") ? t : "XCUIElementType" + t;
    }

    private String literal(String token) {
        if ((token.startsWith("'") || token.startsWith("\"")) && token.length() >= 2) {
            return token.substring(1, token.length() - 1);
        }
        return switch (token.toUpperCase(Locale.ROOT)) {
            case "TRUE", "YES", "1" -> "true";
            case "FALSE", "NO", "0" -> "false";
            default -> token;
        };
    }

    private boolean accept(String token) {
        if (pos < tokens.size() && tokens.get(pos).equalsIgnoreCase(token)) {
            pos++;
            return true;
        }
        return false;
    }

    private void expect(String token) {
        if (!accept(token)) {
            throw error("expected '" + token + "'");
        }
    }

    private String next(String what) {
        if (pos >= tokens.size()) {
            throw error("expected " + what + " at the end");
        }
        return tokens.get(pos++);
    }

    private MobileLocatorException error(String reason) {
        return new MobileLocatorException("Invalid iOS predicate \"" + source + "\": " + reason);
    }

    private static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '\'' || c == '"') {
                StringBuilder sb = new StringBuilder().append(c);
                int j = i + 1;
                while (j < s.length() && s.charAt(j) != c) {
                    if (s.charAt(j) == '\\' && j + 1 < s.length()) {
                        j++;
                    }
                    sb.append(s.charAt(j++));
                }
                if (j >= s.length()) {
                    throw new MobileLocatorException("Invalid iOS predicate \"" + s + "\": unterminated string");
                }
                out.add(sb.append(c).toString());
                i = j + 1;
            } else if (c == '[') {
                int j = s.indexOf(']', i);
                if (j < 0) {
                    throw new MobileLocatorException("Invalid iOS predicate \"" + s + "\": unterminated modifier");
                }
                out.add(s.substring(i, j + 1));
                i = j + 1;
            } else if ("()".indexOf(c) >= 0) {
                out.add(String.valueOf(c));
                i++;
            } else if ("=!<>&|".indexOf(c) >= 0) {
                int j = i + 1;
                while (j < s.length() && "=!<>&|".indexOf(s.charAt(j)) >= 0) {
                    j++;
                }
                out.add(s.substring(i, j));
                i = j;
            } else {
                int j = i;
                while (j < s.length() && !Character.isWhitespace(s.charAt(j)) && "()[]=!<>&|'\"".indexOf(s.charAt(j)) < 0) {
                    j++;
                }
                out.add(s.substring(i, j));
                i = j;
            }
        }
        return out;
    }
}
