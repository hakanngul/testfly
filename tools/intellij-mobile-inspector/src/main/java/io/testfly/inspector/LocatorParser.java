package io.testfly.inspector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds TestFly mobile locators in Java or Kotlin source: {@code @MobileFind} fields and
 * {@code screen().getByTestId/getByText/getByLabel/getByPlaceholder/getByType/getByRole(MobileRole…)}
 * calls, as the selector string TestFly uses per platform. Only literal arguments are read; a locator
 * built from a constant is skipped. The same rules as the VS Code extension's src/javaLocators.ts.
 */
public final class LocatorParser {

    /** A locator in the source: its range and the selector on each platform (null when there is none). */
    public record Found(int start, int end, String source, String android, String ios) {
        public String selectorFor(String platform) {
            return "ios".equalsIgnoreCase(platform) ? ios : android;
        }
    }

    private static final Pattern MOBILE_FIND = Pattern.compile("@MobileFind\\b\\s*\\(");
    private static final Pattern CALL = Pattern.compile(
            "(\\.\\s*)?\\b(getByTestId|getByText|getByLabel|getByPlaceholder|getByType|getByRole)\\s*\\(");
    private static final Pattern NAMED = Pattern.compile("^([A-Za-z]\\w*)\\s*=\\s*([\\s\\S]*)$");
    private static final Pattern LITERAL = Pattern.compile("^\"((?:[^\"\\\\\\n]|\\\\.)*)\"\\s*(\\+\\s*)?");
    private static final Pattern ROLE = Pattern.compile("^(?:\\w+\\.)*MobileRole\\.([A-Z_]+)$");
    private static final Map<String, String> CALL_PREFIX = Map.of(
            "getByTestId", "testid=", "getByText", "text=", "getByLabel", "label=", "getByPlaceholder", "placeholder=", "getByType", "type=");
    // MobilePages.selector: a typed attribute wins, then ios / android, then value
    private static final String[][] ANDROID_TYPED = {{"androidAccessibility", "accessibility="}, {"androidId", "id="},
            {"androidXpath", "xpath="}, {"androidClassName", "type="}};
    private static final String[][] IOS_TYPED = {{"iosAccessibility", "accessibility="}, {"iosId", "id="}, {"iosXpath", "xpath="},
            {"iosClassChain", "chain="}, {"iosPredicate", "predicate="}, {"iosClassName", "type="}};

    private LocatorParser() {
    }

    public static List<Found> find(String text) {
        String code = blankComments(text);
        List<Found> out = new ArrayList<>();
        boolean mobileFile = code.contains("io.testfly.mobile");

        Matcher m = MOBILE_FIND.matcher(code);
        while (m.find()) {
            Args args = parseArgs(code, m.end());
            if (args == null) {
                continue;
            }
            Map<String, String> attrs = new LinkedHashMap<>();
            boolean literal = true;
            for (String a : args.list) {
                Matcher named = NAMED.matcher(a);
                boolean isNamed = named.matches();
                String value = stringValue(isNamed ? named.group(2) : a);
                if (value == null) {
                    literal = false;
                    break;
                }
                attrs.put(isNamed ? named.group(1) : "value", value);
            }
            if (literal) {
                out.add(new Found(m.start(), args.end + 1, oneLine(code.substring(m.start(), args.end + 1)),
                        pick(attrs, "android", ANDROID_TYPED), pick(attrs, "ios", IOS_TYPED)));
            }
        }

        Matcher c = CALL.matcher(code);
        while (c.find()) {
            int start = c.start(2);
            String receiver = code.substring(Math.max(0, c.start() - 40), c.start() + (c.group(1) != null ? 1 : 0));
            Args args = parseArgs(code, c.end());
            if (args == null || args.list.isEmpty()) {
                continue;
            }
            boolean mobile = args.list.get(0).contains("MobileRole.") || receiver.matches("(?s).*\\bscreen\\(\\)\\s*\\.$")
                    || (mobileFile && !args.list.get(0).matches("(?:^|.*[^\\w.])Role\\..*"));
            String selector = mobile ? callSelector(c.group(2), args.list) : null;
            if (selector != null) {
                out.add(new Found(start, args.end + 1, oneLine(code.substring(start, args.end + 1)), selector, selector));
            }
        }
        out.sort(Comparator.comparingInt(Found::start));
        return out;
    }

    private static String pick(Map<String, String> attrs, String platform, String[][] typed) {
        String chosen = null;
        for (String[] t : typed) {
            if (attrs.get(t[0]) != null && !attrs.get(t[0]).isEmpty()) {
                chosen = t[1] + attrs.get(t[0]);
            }
        }
        if (chosen != null) {
            return chosen;
        }
        String own = attrs.get(platform);
        if (own != null && !own.isEmpty()) {
            return own;
        }
        String value = attrs.get("value");
        return value != null && !value.isEmpty() ? value : null;
    }

    private static String callSelector(String name, List<String> args) {
        if (name.equals("getByRole")) {
            Matcher role = ROLE.matcher(args.get(0).trim());
            if (!role.matches() || args.size() > 2) {
                return null;
            }
            String roleName = args.size() == 2 ? stringValue(args.get(1)) : "";
            return roleName == null ? null : "role=" + role.group(1).toLowerCase() + (roleName.isEmpty() ? "" : ";name=" + roleName);
        }
        String value = stringValue(args.get(0));
        // the (text, false) overloads match substrings, which has no selector-string form
        if (value == null || args.size() > 2 || (args.size() == 2 && !args.get(1).trim().equals("true"))) {
            return null;
        }
        return CALL_PREFIX.get(name) + value;
    }

    private record Args(List<String> list, int end) {
    }

    /** Arguments of the call whose '(' is just before {@code from}, split at top-level commas. */
    private static Args parseArgs(String code, int from) {
        List<String> list = new ArrayList<>();
        int depth = 0;
        int start = from;
        for (int i = from; i < code.length() && i < from + 4000; i++) {
            char ch = code.charAt(i);
            if (ch == '"' || ch == '\'') {
                i = skipLiteral(code, i);
                if (i < 0) {
                    return null;
                }
            } else if (ch == '(' || ch == '{' || ch == '[') {
                depth++;
            } else if (ch == ')' || ch == '}' || ch == ']') {
                if (depth == 0) {
                    String last = code.substring(start, i).trim();
                    if (!last.isEmpty() || !list.isEmpty()) {
                        list.add(last);
                    }
                    return new Args(list, i);
                }
                depth--;
            } else if (ch == ',' && depth == 0) {
                list.add(code.substring(start, i).trim());
                start = i + 1;
            }
        }
        return null;
    }

    private static int skipLiteral(String code, int i) {
        char q = code.charAt(i);
        if (q == '"' && code.startsWith("\"\"\"", i)) {
            int end = code.indexOf("\"\"\"", i + 3);
            return end < 0 ? -1 : end + 2;
        }
        for (int j = i + 1; j < code.length(); j++) {
            char ch = code.charAt(j);
            if (ch == '\\') {
                j++;
            } else if (ch == q) {
                return j;
            } else if (ch == '\n') {
                return -1;
            }
        }
        return -1;
    }

    /** The value of a string literal or a {@code +} concatenation of literals; null for anything else. */
    static String stringValue(String expr) {
        StringBuilder value = new StringBuilder();
        String rest = expr.trim();
        if (rest.isEmpty()) {
            return null;
        }
        while (!rest.isEmpty()) {
            Matcher m = LITERAL.matcher(rest);
            if (!m.find()) {
                return null;
            }
            value.append(unescape(m.group(1)));
            rest = rest.substring(m.end());
            if (m.group(2) == null && !rest.isEmpty()) {
                return null;
            }
        }
        return value.toString();
    }

    private static String unescape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch != '\\' || i + 1 >= s.length()) {
                out.append(ch);
                continue;
            }
            char e = s.charAt(++i);
            switch (e) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 's' -> out.append(' ');
                case 'u' -> {
                    int j = i;
                    while (j + 1 < s.length() && s.charAt(j + 1) == 'u') {
                        j++;
                    }
                    if (j + 4 < s.length()) {
                        out.append((char) Integer.parseInt(s.substring(j + 1, j + 5), 16));
                        i = j + 4;
                    }
                }
                default -> out.append(e);
            }
        }
        return out.toString();
    }

    private static String oneLine(String s) {
        return s.replaceAll("\\s+", " ");
    }

    /** Comments replaced by spaces, so offsets stay and commented-out locators are ignored. */
    static String blankComments(String code) {
        StringBuilder out = new StringBuilder(code.length());
        for (int i = 0; i < code.length(); i++) {
            char ch = code.charAt(i);
            if (ch == '"' || ch == '\'') {
                int end = skipLiteral(code, i);
                int stop = end < 0 ? i : end;
                out.append(code, i, stop + 1);
                i = stop;
            } else if (ch == '/' && i + 1 < code.length() && code.charAt(i + 1) == '/') {
                int end = code.indexOf('\n', i);
                int stop = end < 0 ? code.length() : end;
                out.append(" ".repeat(stop - i));
                i = stop - 1;
            } else if (ch == '/' && i + 1 < code.length() && code.charAt(i + 1) == '*') {
                int end = code.indexOf("*/", i + 2);
                int stop = end < 0 ? code.length() : end + 2;
                out.append(code.substring(i, stop).replaceAll("[^\\n]", " "));
                i = stop - 1;
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }
}
