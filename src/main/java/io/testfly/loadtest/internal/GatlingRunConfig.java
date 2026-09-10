package io.testfly.loadtest.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.loadtest.CheckCondition;
import io.testfly.loadtest.LoadScenario;
import io.testfly.loadtest.LoadStep;
import io.testfly.loadtest.LoadTestConfig;

import java.io.File;
import java.io.IOException;
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
 * for forked JVM runs (which need {@code --add-opens} on Java 17+).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
final class GatlingRunConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicReference<GatlingRunConfig> CURRENT = new AtomicReference<>();

    public String scenarioName;
    public String baseUrl;
    public int users;
    public long rampUpSeconds;
    public long holdSeconds;
    public long cooldownSeconds;
    public int requestTimeoutSeconds;
    public String resultsDir;
    public long thinkTimeFixedMs = -1;
    public long thinkTimeMinMs = -1;
    public long thinkTimeMaxMs = -1;
    public GatlingFeederConfig feeder;
    public List<GatlingStep> steps = new ArrayList<>();

    public GatlingRunConfig() {
    }

    GatlingRunConfig(LoadScenario scenario, LoadTestConfig config, String baseUrl) {
        this.scenarioName = scenario.name();
        this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl : config.getBaseUrl();
        this.users = config.getUsers();
        this.rampUpSeconds = config.getRampUpSeconds();
        this.holdSeconds = config.getHoldSeconds();
        this.cooldownSeconds = config.getCooldownSeconds();
        this.requestTimeoutSeconds = config.getRequestTimeoutSeconds();
        this.resultsDir = config.getResultsDir();

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
            gs.extractions.putAll(step.extractions());

            for (CheckCondition check : step.checks()) {
                if (check.type() == CheckCondition.Type.STATUS_IS && check.expected() instanceof Integer code) {
                    gs.statusChecks.add(code);
                } else if (check.type() == CheckCondition.Type.JSON_EXISTS && check.target() != null) {
                    gs.jsonPathChecks.add(check.target());
                }
            }
            this.steps.add(gs);
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
        public List<Integer> statusChecks = new ArrayList<>();
        public List<String> jsonPathChecks = new ArrayList<>();
        public Map<String, String> extractions = new LinkedHashMap<>();
    }
}
