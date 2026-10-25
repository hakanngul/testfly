package io.testfly.loadtest.internal;

import io.testfly.loadtest.CheckCondition;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadSimulation;
import io.testfly.loadtest.LoadStep;
import io.testfly.loadtest.LoadTestConfig;
import io.testfly.loadtest.OpenStep;
import io.testfly.loadtest.Workload;
import io.testfly.config.TestFlyConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Fail-fast validation of a {@link LoadScenario} before any request is sent.
 *
 * <p>Both engines apply the same rules, so a scenario that one engine would silently
 * misinterpret is rejected with an {@link IllegalArgumentException} instead:
 * <ul>
 * <li>at least one step;</li>
 * <li>step names are literal: no {@code #{} or {@code ${} (they would be Gatling expressions);</li>
 * <li>a step may not combine a body with form parameters;</li>
 * <li>every check has a known type and the data that type needs (JSON/header checks need a
 * non-blank target, {@code BODY_MATCHES} a valid regex, status checks their codes);</li>
 * <li>{@code extract(var, path)} needs a non-blank variable and path;</li>
 * <li>a {@link Workload} needs the Gatling engine, cannot be combined with {@code users/rampUp/hold/cooldown},
 * and its peak (closed: concurrent users; open: arrival rate and every user-count step) may not exceed
 * {@code maxUsers};</li>
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

    /** Used when no TestFly configuration is loaded (same default as {@code loadtest.maxUsers}). */
    static final int DEFAULT_MAX_USERS = 1000;

    /** Message of a workload combined with the legacy shape. */
    static final String WORKLOAD_COMBINED =
            "workload(...) cannot be combined with users/rampUp/hold/cooldown";
    /** Message of a workload that would start no virtual user. */
    static final String WORKLOAD_INJECTS_NOBODY = "workload(...) injects no virtual user";

    /** Message of a workload on an engine other than Gatling. */
    static final String WORKLOAD_NEEDS_GATLING = "workload(...) requires the Gatling engine; add "
            + "io.gatling.highcharts:gatling-charts-highcharts or use users/rampUp/hold";

    /**
     * Validates the scenario for the given (resolved) engine, with the {@code loadtest.maxUsers} cap of the
     * loaded TestFly configuration ({@value #DEFAULT_MAX_USERS} when none is loaded).
     *
     * @param scenario   the scenario to validate
     * @param engineName the resolved engine name ({@code "jdk"} or {@code "gatling"})
     * @throws IllegalArgumentException if the scenario cannot be executed faithfully
     */
    public static void validate(LoadScenario scenario, String engineName) {
        validate(scenario, engineName, configuredMaxUsers());
    }

    /**
     * Validates the scenario for the given (resolved) engine with an explicit {@code maxUsers} cap
     * (the runner passes {@link io.testfly.loadtest.LoadTestConfig#getMaxUsers()}).
     *
     * @throws IllegalArgumentException if the scenario cannot be executed faithfully
     */
    public static void validate(LoadScenario scenario, String engineName, int maxUsers) {
        // Workload shape and engine first: a workload on the wrong engine is reported before step details
        if (scenario.workload() != null) {
            validateWorkloadShapeAndEngine(scenario, engineName);
        }
        if (scenario.steps().isEmpty()) {
            throw new IllegalArgumentException(
                    "[LoadTest] Scenario '" + scenario.name() + "' has no steps. " +
                            "Add at least one step via .get(), .post(), or .step().");
        }
        boolean jdk = "jdk".equals(engineName);
        for (LoadStep step : scenario.steps()) {
            String where = "[LoadTest] Scenario '" + scenario.name() + "', step '" + step.name() + "': ";
            if (step.name() != null && (step.name().contains("#{") || step.name().contains("${"))) {
                throw new IllegalArgumentException(where + "a step name cannot contain '#{' or '${': Gatling "
                        + "would evaluate it as an expression and attribute the metrics to another name; "
                        + "use a literal step name and put variables in the path, headers or body");
            }
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
        if (scenario.workload() != null) {
            validateWorkloadLoad(scenario, maxUsers);
        }
    }

    private static void validateWorkloadShapeAndEngine(LoadScenario scenario, String engineName) {
        String where = "[LoadTest] Scenario '" + scenario.name() + "': ";
        if (scenario.users() != -1 || scenario.rampUp() != null || scenario.hold() != null
                || scenario.cooldown() != null) {
            throw new IllegalArgumentException(where + WORKLOAD_COMBINED
                    + "; express the ramp, hold and ramp-down as workload steps");
        }
        if (!"gatling".equals(engineName)) {
            throw new IllegalArgumentException(where + WORKLOAD_NEEDS_GATLING + " (engine: " + engineName + ")");
        }
    }

    /** Separator of {@code "<scenario> :: <step>"} request names in a multi-scenario simulation. */
    public static final String REQUEST_NAME_SEPARATOR = " :: ";

    /** Message of a simulation on an engine other than Gatling. */
    static final String SIMULATION_NEEDS_GATLING = "loadSimulation(...) requires the Gatling engine; add "
            + "io.gatling.highcharts:gatling-charts-highcharts";

    /**
     * {@link #validateSimulation(LoadSimulation, String, int, ToIntFunction)} with the legacy users of each scenario
     * resolved without a test class or method ({@code users(..)}, else {@code testfly.yml}, else the default).
     */
    public static void validateSimulation(LoadSimulation simulation, String engineName, int maxUsers) {
        validateSimulation(simulation, engineName, maxUsers,
                s -> s.users() > 0 ? s.users() : LoadTestConfig.resolveFor(s, null, null).getUsers());
    }

    /**
     * Validates a multi-scenario simulation before anything is forked. Order: engine (Gatling only), simulation
     * name, at least one scenario, scenario names (non-blank, unique, no {@code #{}/{@code ${}, no
     * {@value #REQUEST_NAME_SEPARATOR} and not ending with {@code " ::"}), scenario engines, each scenario on its
     * own, then the combined {@code maxUsers} caps: the sum of the closed peaks (a legacy scenario counts its
     * resolved users) and the sum of the open peak rates.
     *
     * <p>A legacy scenario whose own {@code users(..)} exceeds {@code maxUsers} is rejected here, not clamped as
     * {@code LoadScenario.run()} does: in a simulation a silent clamp would also hide part of the combined load.
     * Users that come from {@code @LoadTest} or {@code testfly.yml} are defaults and are already clamped to
     * {@code maxUsers} when the configuration is resolved.
     *
     * @param engineName  the simulation's resolved engine name
     * @param legacyUsers the resolved users of a scenario without a workload (the runner passes the users of the
     *                    configuration it will run with, including {@code @LoadTest})
     * @throws IllegalArgumentException if the simulation cannot be run faithfully
     */
    public static void validateSimulation(LoadSimulation simulation, String engineName, int maxUsers,
            ToIntFunction<LoadScenario> legacyUsers) {
        String where = "[LoadTest] Simulation '" + simulation.name() + "': ";
        if (!"gatling".equals(engineName)) {
            throw new IllegalArgumentException(where + SIMULATION_NEEDS_GATLING + " (engine: " + engineName + ")");
        }
        if (simulation.name() == null || simulation.name().isBlank()) {
            throw new IllegalArgumentException("[LoadTest] A simulation needs a non-blank name");
        }
        List<LoadScenario> scenarios = simulation.scenarios();
        if (scenarios.isEmpty()) {
            throw new IllegalArgumentException(where + "a simulation needs at least one scenario; "
                    + "add them with scenario(..)");
        }
        Set<String> names = new HashSet<>();
        for (LoadScenario s : scenarios) {
            String n = s.name();
            if (n == null || n.isBlank()) {
                throw new IllegalArgumentException(where + "every scenario needs a non-blank name");
            }
            if (n.contains("#{") || n.contains("${")) {
                throw new IllegalArgumentException(where + "scenario name '" + n + "' cannot contain '#{' or '${'");
            }
            if (n.contains(REQUEST_NAME_SEPARATOR) || n.endsWith(REQUEST_NAME_SEPARATOR.stripTrailing())) {
                throw new IllegalArgumentException(where + "scenario name '" + n + "' cannot contain '"
                        + REQUEST_NAME_SEPARATOR + "' or end with '" + REQUEST_NAME_SEPARATOR.stripTrailing()
                        + "' (' :: ' separates scenario and step in request names)");
            }
            if (!names.add(n)) {
                throw new IllegalArgumentException(where + "duplicate scenario name '" + n + "'");
            }
            if (s.engine() != null && !s.engine().equals(engineName) && !"auto".equals(s.engine())) {
                throw new IllegalArgumentException(where + "scenario '" + n + "' sets engine '" + s.engine()
                        + "', which conflicts with the simulation's engine '" + engineName + "'");
            }
        }
        for (LoadScenario s : scenarios) {
            validate(s, engineName, maxUsers);
        }
        double closedPeaks = 0;
        double openRates = 0;
        for (LoadScenario s : scenarios) {
            Workload w = s.workload();
            if (w == null) {
                if (s.users() > maxUsers) {
                    throw capExceeded(where, "scenario '" + s.name() + "' users(" + s.users() + ")", maxUsers);
                }
                closedPeaks += legacyUsers.applyAsInt(s);
            } else if (w.model() == Workload.Model.CLOSED) {
                closedPeaks += w.peak();
            } else {
                openRates += w.peak();
            }
        }
        if (closedPeaks > maxUsers) {
            throw capExceeded(where, "sum of the closed peaks (" + fmt(closedPeaks) + " concurrent users)", maxUsers);
        }
        if (openRates > maxUsers) {
            throw capExceeded(where, "sum of the open peak rates (" + fmt(openRates) + " users/s)", maxUsers);
        }
    }

    /** Rejects a workload that injects nobody, and one whose peak exceeds {@code maxUsers}. */
    private static void validateWorkloadLoad(LoadScenario scenario, int maxUsers) {
        String where = "[LoadTest] Scenario '" + scenario.name() + "': ";
        Workload w = scenario.workload();
        if (w.model() == Workload.Model.CLOSED) {
            if (w.peak() <= 0) {
                throw new IllegalArgumentException(where + WORKLOAD_INJECTS_NOBODY
                        + ": the peak concurrency of " + w + " is 0");
            }
            if (w.peak() > maxUsers) {
                throw capExceeded(where, "workload peak of " + fmt(w.peak()) + " concurrent users", maxUsers);
            }
            return;
        }
        if (w.peak() > maxUsers) {
            throw capExceeded(where, "workload peak arrival rate of " + fmt(w.peak()) + " users/s", maxUsers);
        }
        if (!injectsAnyone(w)) {
            throw new IllegalArgumentException(where + WORKLOAD_INJECTS_NOBODY
                    + ": " + w + " has only pauses or zero rates");
        }
        for (OpenStep step : w.openSteps()) {
            int users = switch (step) {
                case OpenStep.AtOnce a -> a.users();
                case OpenStep.Ramp r -> r.users();
                case OpenStep.StressPeak p -> p.users();
                default -> 0;
            };
            if (users > maxUsers) {
                throw capExceeded(where, "workload " + step + " with " + users + " users", maxUsers);
            }
        }
    }

    /** {@code true} if an open workload starts at least one user: a user-count step or a positive rate. */
    private static boolean injectsAnyone(Workload w) {
        for (OpenStep step : w.openSteps()) {
            boolean starts = switch (step) {
                case OpenStep.AtOnce a -> a.users() > 0;
                case OpenStep.Ramp r -> r.users() > 0;
                case OpenStep.StressPeak p -> p.users() > 0;
                case OpenStep.ConstantRate r -> r.usersPerSec() > 0;
                case OpenStep.RampRate r -> r.fromPerSec() > 0 || r.toPerSec() > 0;
                default -> false;
            };
            if (starts) {
                return true;
            }
        }
        return false;
    }

    private static IllegalArgumentException capExceeded(String where, String what, int maxUsers) {
        return new IllegalArgumentException(where + what + " exceeds maxUsers=" + maxUsers
                + " (loadtest.maxUsers); lower the workload or raise loadtest.maxUsers");
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** {@code loadtest.maxUsers} of the loaded configuration, or {@value #DEFAULT_MAX_USERS}. */
    private static int configuredMaxUsers() {
        try {
            if (io.testfly.internal.TestFlyContext.isInitialized()) {
                TestFlyConfig.LoadTest lt = io.testfly.internal.TestFlyContext.getConfig().getLoadTest();
                if (lt != null && lt.getMaxUsers() > 0) {
                    return lt.getMaxUsers();
                }
            }
        } catch (RuntimeException ignored) {
            // no usable configuration: default cap
        }
        return DEFAULT_MAX_USERS;
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
