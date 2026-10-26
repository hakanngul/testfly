package io.testfly.loadtest.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.loadtest.CheckCondition;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadStep;
import io.testfly.loadtest.LoadTestConfig;
import io.testfly.loadtest.ClosedStep;
import io.testfly.loadtest.OpenStep;
import io.testfly.loadtest.SlaRule;
import io.testfly.loadtest.Workload;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe config holder and serializer bridging {@link GatlingEngine} →
 * {@link TestFlyGatlingSimulation}.
 *
 * <p>Supports in-memory passing for in-process runs, and JSON serialization
 * for forked JVM runs (which need {@code --add-opens} on Java 21+).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
final class GatlingRunConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicReference<GatlingRunConfig> CURRENT = new AtomicReference<>();

    /** Simulation name: the scenario's name for a single-scenario run. */
    public String simulationName;
    public String resultsDir;
    /**
     * Absolute path of the TestFly metrics sidecar ({@value GatlingResultsParser#METRICS_SIDECAR_FILE})
     * written by the simulation's {@code after()} hook, inside the run's dedicated results directory.
     */
    public String metricsSidecarPath;
    /** The scenarios in order; one element for {@code LoadScenario.run()}. */
    public List<ScenarioConfig> scenarios = new ArrayList<>();
    /** SLA rules registered as native Gatling assertions (Gatling's report only; TestFly decides). */
    public List<GatlingAssertion> assertions = new ArrayList<>();

    /**
     * Extra time Gatling may run past one full iteration after the injection (rampUp + hold, or the workload's
     * total duration) before {@code maxDuration} stops it.
     */
    static final long MAX_DURATION_GRACE_SECONDS = 30;

    public GatlingRunConfig() {
    }

    /** A single-scenario run: unprefixed request names. */
    GatlingRunConfig(LoadScenario scenario, LoadTestConfig config, String baseUrl) {
        this.simulationName = scenario.name();
        this.resultsDir = config.getResultsDir();
        this.scenarios.add(new ScenarioConfig(scenario, config, baseUrl));
    }

    /**
     * A run of several scenarios; with more than one, request names are prefixed
     * {@code "<scenario>" + }{@value LoadScenarioValidator#REQUEST_NAME_SEPARATOR}.
     */
    static GatlingRunConfig forSimulation(String simulationName, String resultsDir, List<ScenarioConfig> scenarios) {
        GatlingRunConfig cfg = new GatlingRunConfig();
        cfg.simulationName = simulationName;
        cfg.resultsDir = resultsDir;
        cfg.scenarios.addAll(scenarios);
        boolean prefixed = scenarios.size() > 1;
        for (ScenarioConfig sc : cfg.scenarios) {
            sc.requestPrefix = prefixed ? sc.scenarioName + LoadScenarioValidator.REQUEST_NAME_SEPARATOR : "";
        }
        return cfg;
    }

    /**
     * The SLA rules that have a native Gatling counterpart, in order: the simulation rules (global rules on
     * {@code global()}, step rules resolved with {@link SlaEvaluator#simulationStepKey} on
     * {@code details(requestName)}), then each scenario's rules (its step rules matched exactly; its global rules
     * only when the run has a single scenario, because Gatling has no per-population global). Rules on unknown or
     * ambiguous steps are skipped. TestFly evaluates every rule either way.
     */
    static List<GatlingAssertion> nativeAssertions(GatlingRunConfig cfg, List<GatlingEngine.ScenarioRun> runs,
            List<SlaRule> simulationRules) {
        List<GatlingAssertion> out = new ArrayList<>();
        List<String> allRequests = new ArrayList<>();
        List<String> scenarioNames = new ArrayList<>();
        for (ScenarioConfig sc : cfg.scenarios) {
            scenarioNames.add(sc.scenarioName);
            for (GatlingStep step : sc.steps) {
                allRequests.add(sc.requestName(step.name));
            }
        }
        for (SlaRule rule : simulationRules == null ? List.<SlaRule>of() : simulationRules) {
            if (rule.isGlobal()) {
                out.add(GatlingAssertion.of(null, rule, null));
            } else {
                String request = SlaEvaluator.simulationStepKey(allRequests, rule.scope(), scenarioNames);
                if (request != null) {
                    out.add(GatlingAssertion.of(request, rule, null));
                }
            }
        }
        boolean single = cfg.scenarios.size() == 1;
        for (int i = 0; i < runs.size() && i < cfg.scenarios.size(); i++) {
            ScenarioConfig sc = cfg.scenarios.get(i);
            for (SlaRule rule : runs.get(i).slaRules()) {
                if (rule.isGlobal()) {
                    if (single) {
                        out.add(GatlingAssertion.of(null, rule, sc.scenarioName));
                    }
                } else if (sc.steps.stream().anyMatch(s -> rule.scope().equals(s.name))) {
                    out.add(GatlingAssertion.of(sc.requestName(rule.scope()), rule, sc.scenarioName));
                }
            }
        }
        return out;
    }

    /** Gatling {@code maxDuration} in seconds: the largest {@link ScenarioConfig#maxDurationSeconds()}. */
    long maxDurationSeconds() {
        long max = 0;
        for (ScenarioConfig sc : scenarios) {
            max = Math.max(max, sc.maxDurationSeconds());
        }
        return max > 0 ? max : MAX_DURATION_GRACE_SECONDS;
    }

    /** Users reported for all scenarios together: the sum of {@link ScenarioConfig#reportedUsers()}. */
    int reportedUsers() {
        int sum = 0;
        for (ScenarioConfig sc : scenarios) {
            sum += sc.reportedUsers();
        }
        return sum;
    }

    /**
     * One scenario of the run: name, base URL, steps, feeder, think time, request timeout and either a workload
     * or the legacy {@code users/rampUp/hold} shape.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScenarioConfig {

        public String scenarioName;
        public String baseUrl;
        public int users;
        public long rampUpSeconds;
        public long holdSeconds;
        public long cooldownSeconds;
        public int requestTimeoutSeconds;
        public long thinkTimeFixedMs = -1;
        public long thinkTimeMinMs = -1;
        public long thinkTimeMaxMs = -1;
        public GatlingFeederConfig feeder;
        public List<GatlingStep> steps = new ArrayList<>();
        /** Workload model; {@code null} for the legacy {@code users/rampUp/hold} loop. */
        public GatlingWorkload workload;

        /**
         * Prefix of this scenario's request names: {@code "<scenario> :: "} when the run has more than one
         * scenario, empty otherwise (single-scenario names stay unprefixed).
         */
        public String requestPrefix = "";

        public ScenarioConfig() {
        }

        ScenarioConfig(LoadScenario scenario, LoadTestConfig config, String baseUrl) {
            this.scenarioName = scenario.name();
            this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl : config.getBaseUrl();
            this.users = config.getUsers();
            this.rampUpSeconds = config.getRampUpSeconds();
            this.holdSeconds = config.getHoldSeconds();
            this.cooldownSeconds = config.getCooldownSeconds();
            this.requestTimeoutSeconds = config.getRequestTimeoutSeconds();

            if (scenario.thinkTimeFixed() != null) {
                this.thinkTimeFixedMs = scenario.thinkTimeFixed().toMillis();
            }
            this.thinkTimeMinMs = scenario.thinkTimeMinMs();
            this.thinkTimeMaxMs = scenario.thinkTimeMaxMs();

            if (scenario.feeder() != null) {
                io.testfly.loadtest.LoadTestFeeder.FeederDescriptor desc = scenario.feeder().describe();
                if (desc != null) {
                    GatlingFeederConfig fc = new GatlingFeederConfig();
                    fc.type = desc.type();
                    fc.path = desc.path();
                    fc.variable = desc.variable();
                    fc.start = desc.start();
                    fc.step = desc.step();
                    fc.min = desc.min();
                    fc.max = desc.max();
                    fc.value = desc.value();
                    fc.records = desc.records();
                    this.feeder = fc;
                }
            }

            if (scenario.workload() != null) {
                this.workload = GatlingWorkload.of(scenario.workload());
            }

            for (LoadStep step : scenario.steps()) {
                GatlingStep gs = new GatlingStep();
                gs.name = step.name();
                gs.method = step.method();
                gs.path = step.path();
                gs.headers.putAll(step.headers());
                gs.queryParams.putAll(step.queryParams());
                if (step.body() != null) {
                    if (step.body() instanceof String s) {
                        gs.body = s;
                    } else {
                        try {
                            gs.body = MAPPER.writeValueAsString(step.body());
                        } catch (Exception e) {
                            gs.body = step.body().toString();
                        }
                    }
                }
                // same normalised JSONPaths as the JDK engine (bare "token" → "$.token")
                step.extractions().forEach((var, path) ->
                        gs.extractions.put(var, LoadScenarioValidator.normalizeJsonPath(path)));

                gs.formParams.putAll(step.formParams());
                for (CheckCondition check : step.checks()) {
                    gs.checks.add(GatlingCheck.of(check));
                }
                this.steps.add(gs);
            }
        }

        /**
         * Gatling {@code maxDuration} in seconds:
         * {@code injection + steps × (requestTimeout + max think time per step) + 30s}, where the injection is
         * {@code rampUp + hold} or, with a workload, its total duration (rounded up).
         *
         * <p>The hold loop runs with {@code exitASAP = false}, so the last user started at {@code rampUp} may
         * begin a full iteration just before {@code hold} ends and run all its steps to completion; each step can
         * take up to its request timeout plus the longest think time. Also the base of {@link GatlingEngine}'s
         * wait budget for the child JVM.
         */
        long maxDurationSeconds() {
            long stepCount = Math.max(1, steps != null ? steps.size() : 0);
            long perStepMs = effectiveRequestTimeoutSeconds() * 1000L + maxThinkTimeMs();
            long iterationSeconds = (stepCount * perStepMs + 999) / 1000; // ceil
            long injectionSeconds = workload != null
                    ? (long) Math.ceil(workload.totalSeconds())
                    : Math.max(0, rampUpSeconds) + Math.max(0, holdSeconds);
            return injectionSeconds + iterationSeconds + MAX_DURATION_GRACE_SECONDS;
        }

        /**
         * Users reported in {@code LoadTestMetrics.users()}: the configured {@link #users} for the legacy loop,
         * the workload's planned users otherwise ({@link GatlingWorkload#plannedUsers()}).
         */
        int reportedUsers() {
            return workload != null ? workload.plannedUsers() : users;
        }

        /** Longest think time after one step in ms: the fixed pause, else the random range's maximum, else 0. */
        long maxThinkTimeMs() {
            if (thinkTimeFixedMs > 0) {
                return thinkTimeFixedMs;
            }
            if (thinkTimeMinMs >= 0 && thinkTimeMaxMs >= 0) {
                return Math.max(thinkTimeMinMs, thinkTimeMaxMs);
            }
            return 0;
        }

        /** Per-request timeout in seconds (at least 1). */
        int effectiveRequestTimeoutSeconds() {
            return Math.max(1, requestTimeoutSeconds);
        }


        /** The Gatling request name (and marker key) of one of this scenario's steps. */
        String requestName(String stepName) {
            return (requestPrefix != null ? requestPrefix : "") + (stepName != null ? stepName : "");
        }
    }

    static void set(GatlingRunConfig config) {
        CURRENT.set(config);
    }

    static GatlingRunConfig get() {
        return CURRENT.get();
    }

    static void clear() {
        CURRENT.set(null);
    }

    static void save(GatlingRunConfig config, File file) throws IOException {
        MAPPER.writeValue(file, config);
    }

    static GatlingRunConfig load(File file) throws IOException {
        return MAPPER.readValue(file, GatlingRunConfig.class);
    }

    /**
     * Serialisable SLA rule for a native Gatling assertion: {@code requestName == null} means {@code global()},
     * otherwise {@code details(requestName)}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GatlingAssertion {
        public String requestName;
        /** The rule's scope as declared ({@value SlaRule#GLOBAL} or a step name). */
        public String scope;
        /** The scenario whose own SLA declared the rule; {@code null} for a simulation-level rule. */
        public String source;
        /** {@link SlaRule.Metric} name. */
        public String metric;
        /** {@link SlaRule.Comparison} name. */
        public String comparison;
        public double threshold;

        public GatlingAssertion() {
        }

        static GatlingAssertion of(String requestName, SlaRule rule, String source) {
            GatlingAssertion a = new GatlingAssertion();
            a.requestName = requestName;
            a.scope = rule.scope();
            a.source = source;
            a.metric = rule.metric().name();
            a.comparison = rule.comparison().name();
            a.threshold = rule.threshold();
            return a;
        }

        boolean below() {
            return "BELOW".equals(comparison);
        }

        /** The TestFly rule this assertion was built from. */
        SlaRule toRule() {
            return new SlaRule(scope, SlaRule.Metric.valueOf(metric), SlaRule.Comparison.valueOf(comparison),
                    threshold);
        }
    }

    /** Serialisable form of a {@link Workload}: {@code {model, steps:[{type, users, ..., seconds}]}}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GatlingWorkload {
        /** {@code "OPEN"} or {@code "CLOSED"}. */
        public String model;
        public List<GatlingWorkloadStep> steps = new ArrayList<>();

        public GatlingWorkload() {
        }

        static GatlingWorkload of(Workload w) {
            GatlingWorkload gw = new GatlingWorkload();
            gw.model = w.model().name();
            for (OpenStep step : w.openSteps()) {
                GatlingWorkloadStep s = new GatlingWorkloadStep();
                s.seconds = seconds(step.duration());
                switch (step) {
                    case OpenStep.AtOnce a -> {
                        s.type = "atOnce";
                        s.users = a.users();
                    }
                    case OpenStep.Ramp r -> {
                        s.type = "ramp";
                        s.users = r.users();
                    }
                    case OpenStep.ConstantRate r -> {
                        s.type = "constantRate";
                        s.rate = r.usersPerSec();
                    }
                    case OpenStep.RampRate r -> {
                        s.type = "rampRate";
                        s.fromRate = r.fromPerSec();
                        s.toRate = r.toPerSec();
                    }
                    case OpenStep.StressPeak p -> {
                        s.type = "stressPeak";
                        s.users = p.users();
                    }
                    case OpenStep.NothingFor n -> s.type = "nothingFor";
                }
                gw.steps.add(s);
            }
            for (ClosedStep step : w.closedSteps()) {
                GatlingWorkloadStep s = new GatlingWorkloadStep();
                s.seconds = seconds(step.duration());
                switch (step) {
                    case ClosedStep.ConstantConcurrent c -> {
                        s.type = "constantConcurrent";
                        s.users = c.users();
                    }
                    case ClosedStep.RampConcurrent r -> {
                        s.type = "rampConcurrent";
                        s.fromUsers = r.fromUsers();
                        s.toUsers = r.toUsers();
                    }
                }
                gw.steps.add(s);
            }
            return gw;
        }

        private static double seconds(Duration d) {
            return d.toNanos() / 1_000_000_000.0;
        }

        boolean closed() {
            return "CLOSED".equals(model);
        }

        /** Sum of the step durations in seconds. */
        double totalSeconds() {
            double total = 0;
            for (GatlingWorkloadStep s : steps) {
                total += Math.max(0, s.seconds);
            }
            return total;
        }

        /**
         * Planned users: open = the users of {@code atOnce}/{@code ramp}/{@code stressPeak} plus
         * {@code ceil(Σ rate × duration)} of the rate steps; closed = the peak concurrent users.
         */
        int plannedUsers() {
            if (closed()) {
                int peak = 0;
                for (GatlingWorkloadStep s : steps) {
                    peak = Math.max(peak, "rampConcurrent".equals(s.type) ? Math.max(s.fromUsers, s.toUsers) : s.users);
                }
                return peak;
            }
            long users = 0;
            double arrivals = 0;
            for (GatlingWorkloadStep s : steps) {
                switch (s.type) {
                    case "atOnce", "ramp", "stressPeak" -> users += s.users;
                    case "constantRate" -> arrivals += s.rate * s.seconds;
                    case "rampRate" -> arrivals += (s.fromRate + s.toRate) / 2.0 * s.seconds;
                    default -> {
                        // nothingFor: no user
                    }
                }
            }
            // tolerance against floating-point noise (10/s x 4s must be 40, not 41)
            return (int) Math.min(Integer.MAX_VALUE, users + (long) Math.ceil(arrivals - 1e-9));
        }
    }

    /** One serialised workload step; only the fields of its {@code type} are used. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GatlingWorkloadStep {
        public String type;
        public int users;
        public int fromUsers;
        public int toUsers;
        public double rate;
        public double fromRate;
        public double toRate;
        public double seconds;

        public GatlingWorkloadStep() {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GatlingFeederConfig {
        public String type;
        public String path;
        public String variable;
        public Long start;
        public Long step;
        public Integer min;
        public Integer max;
        public String value;
        public List<Map<String, Object>> records;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GatlingStep {
        public String name;
        public String method = "GET";
        public String path;
        public Map<String, String> headers = new LinkedHashMap<>();
        public Map<String, Object> queryParams = new LinkedHashMap<>();
        public String body;
        public Map<String, String> formParams = new LinkedHashMap<>();
        public List<GatlingCheck> checks = new ArrayList<>();
        public Map<String, String> extractions = new LinkedHashMap<>();
    }

    /**
     * Engine-neutral, serializable form of a {@link CheckCondition}: the type name, the target
     * (JSONPath or header name) and the expected values as strings.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GatlingCheck {
        public String type;
        public String target;
        public List<String> expected = new ArrayList<>();

        public GatlingCheck() {
        }

        /** @throws IllegalArgumentException if the check type is unknown or unsupported */
        static GatlingCheck of(CheckCondition check) {
            if (check == null || check.type() == null) {
                throw new IllegalArgumentException("[LoadTest] Unsupported check: " + check);
            }
            GatlingCheck gc = new GatlingCheck();
            gc.type = check.type().name();
            gc.target = switch (check.type()) {
                case JSON_EXISTS, JSON_EQUALS, JSON_IN -> LoadScenarioValidator.normalizeJsonPath(check.target());
                default -> check.target();
            };
            Object exp = check.expected();
            switch (check.type()) {
                case STATUS_IS, JSON_EQUALS -> gc.expected.add(String.valueOf(exp));
                case STATUS_IN, JSON_IN -> {
                    for (int v : (int[]) exp) {
                        gc.expected.add(String.valueOf(v));
                    }
                }
                case BODY_MATCHES -> gc.expected.add((String) exp);
                case JSON_EXISTS, HEADER_PRESENT -> {
                    // no expected value
                }
                default -> throw new IllegalArgumentException(
                        "[LoadTest] Unsupported check type for Gatling: " + check.type());
            }
            return gc;
        }
    }
}
