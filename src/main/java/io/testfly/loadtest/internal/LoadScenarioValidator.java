package io.testfly.loadtest.internal;

import io.testfly.loadtest.CheckCondition;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadStep;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Fail-fast validation of a {@link LoadScenario} before any request is sent.
 *
 * <p>Both engines apply the same rules, so a scenario that one engine would silently
 * misinterpret is rejected with an {@link IllegalArgumentException} instead:
 * <ul>
 * <li>at least one step;</li>
 * <li>a step may not combine a body with form parameters;</li>
 * <li>every check has a known type and the data that type needs (JSON/header checks need a
 * non-blank target, {@code BODY_MATCHES} a valid regex, status checks their codes);</li>
 * <li>{@code extract(var, path)} needs a non-blank variable and path;</li>
 * <li>engine {@code jdk}: JSONPaths must be in the supported subset ({@code $.a.b}, {@code $.a[0].b},
 * {@code $[0]}).</li>
 * </ul>
 */
public final class LoadScenarioValidator {

    private LoadScenarioValidator() {
    }

    /**
     * The single place where JSONPaths are normalised for both engines: a path without a leading
     * {@code $} (the pre-1.1.0 JDK form {@code "token"}, {@code "data.token"}, {@code "items[0]"})
     * becomes {@code "$." + path} ({@code "$" + path} if it starts with {@code [} or {@code .}).
     * {@code null}/blank paths are returned unchanged (validation rejects them).
     */
    public static String normalizeJsonPath(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        String p = path.strip();
        if (p.startsWith("$")) {
            return p;
        }
        return p.startsWith("[") || p.startsWith(".") ? "$" + p : "$." + p;
    }

    /**
     * Validates the scenario for the given (resolved) engine.
     *
     * @param scenario   the scenario to validate
     * @param engineName the resolved engine name ({@code "jdk"} or {@code "gatling"})
     * @throws IllegalArgumentException if the scenario cannot be executed faithfully
     */
    public static void validate(LoadScenario scenario, String engineName) {
        if (scenario.steps().isEmpty()) {
            throw new IllegalArgumentException(
                    "[LoadTest] Scenario '" + scenario.name() + "' has no steps. " +
                            "Add at least one step via .get(), .post(), or .step().");
        }
        boolean jdk = "jdk".equals(engineName);
        for (LoadStep step : scenario.steps()) {
            String where = "[LoadTest] Scenario '" + scenario.name() + "', step '" + step.name() + "': ";
            if (step.path() == null || step.path().isBlank()) {
                throw new IllegalArgumentException(where + "no request path");
            }
            if (step.body() != null && !step.formParams().isEmpty()) {
                throw new IllegalArgumentException(where
                        + "a step cannot have both a body and form parameters; use one of them");
            }
            for (CheckCondition check : step.checks()) {
                validateCheck(where, check, jdk);
            }
            for (Map.Entry<String, String> ext : step.extractions().entrySet()) {
                if (ext.getKey() == null || ext.getKey().isBlank()) {
                    throw new IllegalArgumentException(where + "extract() needs a non-blank variable name");
                }
                requireJsonPath(where, "extract('" + ext.getKey() + "')", ext.getValue(), jdk);
            }
        }
    }

    private static void validateCheck(String where, CheckCondition check, boolean jdk) {
        if (check == null || check.type() == null) {
            throw new IllegalArgumentException(where + "null check or check type");
        }
        switch (check.type()) {
            case STATUS_IS -> {
                if (!(check.expected() instanceof Integer)) {
                    throw new IllegalArgumentException(where + "status().is(..) needs an integer status");
                }
            }
            case STATUS_IN -> {
                if (!(check.expected() instanceof int[] codes) || codes.length == 0) {
                    throw new IllegalArgumentException(where + "status().in(..) needs at least one status");
                }
            }
            case JSON_EXISTS -> requireJsonPath(where, "jsonPath(..).exists()", check.target(), jdk);
            case JSON_EQUALS -> {
                requireJsonPath(where, "jsonPath(..).is(..)", check.target(), jdk);
                if (check.expected() == null) {
                    throw new IllegalArgumentException(where + "jsonPath(..).is(..) needs an expected value");
                }
            }
            case JSON_IN -> {
                requireJsonPath(where, "jsonPath(..).in(..)", check.target(), jdk);
                if (!(check.expected() instanceof int[] values) || values.length == 0) {
                    throw new IllegalArgumentException(where + "jsonPath(..).in(..) needs at least one value");
                }
            }
            case HEADER_PRESENT -> {
                if (check.target() == null || check.target().isBlank()) {
                    throw new IllegalArgumentException(where + "header check needs a header name");
                }
            }
            case BODY_MATCHES -> {
                if (!(check.expected() instanceof String regex)) {
                    throw new IllegalArgumentException(where + "matches(..) needs a regex");
                }
                try {
                    Pattern.compile(regex);
                } catch (PatternSyntaxException e) {
                    throw new IllegalArgumentException(where + "invalid regex in matches(..): " + e.getDescription(), e);
                }
            }
            default -> throw new IllegalArgumentException(where + "unsupported check type " + check.type());
        }
    }

    private static void requireJsonPath(String where, String what, String path, boolean jdk) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(where + what + " needs a non-blank JSONPath");
        }
        if (jdk && !JdkLoadEngine.isSupportedJsonPath(path)) {
            throw new IllegalArgumentException(where + "JSONPath '" + path + "' in " + what
                    + " is not supported by the JDK engine (supported: $.a.b, $.a[0].b, $[0]). "
                    + "The Gatling engine supports full JSONPath: use engine(\"gatling\").");
        }
    }
}
