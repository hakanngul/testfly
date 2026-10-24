package io.testfly.mobile.internal;

import io.testfly.mobile.MobileLocatorException;
import io.testfly.mobile.MobileRole;
import io.testfly.mobile.internal.LocatorEngine.Query;
import io.testfly.mobile.internal.LocatorEngine.TextMatch;

import java.util.List;
import java.util.Locale;

/**
 * Parses the selector strings of {@code MobileScreen.locate} and {@code @MobileFind}: a bare string
 * is a test id; otherwise {@code testid=}, {@code text=}, {@code label=}, {@code placeholder=},
 * {@code type=} or {@code role=button;name=Sign in}. Same meaning as the TS YAML platform blocks.
 */
public final class SelectorParser {

    static final String FORMATS = "LoginScreen.loginButton (test id), testid=… / id=…, accessibility=…, text=…, label=…, "
            + "placeholder=…, type=…, role=button[;name=…], xpath=…, chain=… (iOS class chain), predicate=… (iOS NSPredicate)";

    private SelectorParser() {
    }

    public static Query parse(String selector) {
        if (selector == null || selector.isBlank()) {
            throw invalid(selector, "it is blank");
        }
        int eq = selector.indexOf('=');
        if (eq < 0) {
            return LocatorEngine.byTestId(selector.trim());
        }
        String key = selector.substring(0, eq).trim().toLowerCase(Locale.ROOT);
        String value = selector.substring(eq + 1).trim();
        if (value.isEmpty()) {
            throw invalid(selector, "'" + key + "' has no value");
        }
        return switch (key) {
            case "testid", "id" -> LocatorEngine.byTestId(value);
            case "accessibility" -> LocatorEngine.byAccessibilityId(value);
            case "xpath" -> new XPathQuery(value);
            case "chain" -> new ClassChainQuery(value);
            case "predicate" -> LocatorEngine.where(NsPredicate.parse(value), "iosPredicate(\"" + value + "\")");
            case "text" -> LocatorEngine.byText(TextMatch.exact(value));
            case "label" -> LocatorEngine.byLabel(TextMatch.exact(value));
            case "placeholder" -> LocatorEngine.byPlaceholder(TextMatch.exact(value));
            case "type" -> LocatorEngine.byType(value);
            case "role" -> role(selector, value);
            default -> throw invalid(selector, "unknown key '" + key + "'");
        };
    }

    private static Query role(String selector, String value) {
        String roleName = value;
        TextMatch name = null;
        int semi = value.indexOf(';');
        if (semi >= 0) {
            roleName = value.substring(0, semi).trim();
            String rest = value.substring(semi + 1).trim();
            if (!rest.toLowerCase(Locale.ROOT).startsWith("name=") || rest.substring(5).isBlank()) {
                throw invalid(selector, "only ';name=…' may follow a role");
            }
            name = TextMatch.exact(rest.substring(5).trim());
        }
        MobileRole role;
        try {
            role = MobileRole.valueOf(roleName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalid(selector, "unknown role '" + roleName + "'; roles: " + List.of(MobileRole.values()));
        }
        return LocatorEngine.byRole(role, name);
    }

    private static MobileLocatorException invalid(String selector, String reason) {
        return new MobileLocatorException("Invalid mobile selector \"" + selector + "\": " + reason + ". Formats: " + FORMATS);
    }
}
