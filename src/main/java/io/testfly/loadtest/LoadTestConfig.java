package io.testfly.loadtest;

import io.testfly.api.TestFlyApi;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;

import java.lang.reflect.Method;

/**
 * Resolved load-test configuration — merges {@code @LoadTest} annotation values
 * with {@code testfly.yml} defaults.
 *
 * <h3>Resolution priority</h3>
 * <ol>
 *   <li>Method-level {@code @LoadTest}</li>
 *   <li>Class-level {@code @LoadTest}</li>
 *   <li>{@code testfly.yml → loadtest:}</li>
 *   <li>Hardcoded defaults</li>
 * </ol>
 *
 * <p>Instances are created by the framework (via {@link #resolve(Class, Method)})
 * and consumed by {@link LoadTestRunner}.
 */
@TestFlyApi(since = "1.1.0")
public final class LoadTestConfig {

    private final String baseUrl;
    private final int users;
    private final String rampUp;
    private final String hold;
    private final String cooldown;
    private final String engine;
    private final int maxUsers;
    private final String resultsDir;
    private final boolean reportEnabled;
    private final int requestTimeoutSeconds;

    private LoadTestConfig(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.users = builder.users;
        this.rampUp = builder.rampUp;
        this.hold = builder.hold;
        this.cooldown = builder.cooldown;
        this.engine = builder.engine;
        this.maxUsers = builder.maxUsers;
        this.resultsDir = builder.resultsDir;
        this.reportEnabled = builder.reportEnabled;
        this.requestTimeoutSeconds = builder.requestTimeoutSeconds;
    }

    /**
     * Resolves the effective load-test configuration for a test method.
     *
     * @param testClass the test class (may carry class-level {@code @LoadTest})
     * @param testMethod the test method (may carry method-level {@code @LoadTest}), may be {@code null}
     * @return merged configuration
     */
    public static LoadTestConfig resolve(Class<?> testClass, Method testMethod) {
        TestFlyConfig.LoadTest yaml = yamlConfig();

        LoadTest classAnnotation = testClass != null ? testClass.getAnnotation(LoadTest.class) : null;
        LoadTest methodAnnotation = testMethod != null ? testMethod.getAnnotation(LoadTest.class) : null;

        Builder b = new Builder();

        // Layer 1: YAML defaults (lowest priority)
        if (yaml != null) {
            b.baseUrl = yaml.getBaseUrl();
            b.users = yaml.getUsers();
            b.rampUp = yaml.getRampUp();
            b.hold = yaml.getHold();
            b.cooldown = yaml.getCooldown();
            b.engine = yaml.getEngine();
            b.maxUsers = yaml.getMaxUsers();
            b.resultsDir = yaml.getResultsDir();
            b.reportEnabled = yaml.isReportEnabled();
            b.requestTimeoutSeconds = yaml.getRequestTimeoutSeconds();
        }

        // Layer 2: Class-level annotation overrides YAML
        if (classAnnotation != null) {
            applyAnnotation(b, classAnnotation);
        }

        // Layer 3: Method-level annotation overrides class (highest priority)
        if (methodAnnotation != null) {
            applyAnnotation(b, methodAnnotation);
        }

        // Clamp users to maxUsers
        if (b.users > b.maxUsers) {
            b.users = b.maxUsers;
        }

        return new LoadTestConfig(b);
    }

    /**
     * Resolves configuration for a scenario, applying any scenario-level overrides.
     */
    public static LoadTestConfig resolveFor(LoadScenario scenario, Class<?> testClass, Method testMethod) {
        LoadTestConfig base = resolve(testClass, testMethod);
        Builder b = new Builder();

        b.baseUrl = base.baseUrl;
        b.users = base.users;
        b.rampUp = base.rampUp;
        b.hold = base.hold;
        b.cooldown = base.cooldown;
        b.engine = base.engine;
        b.maxUsers = base.maxUsers;
        b.resultsDir = base.resultsDir;
        b.reportEnabled = base.reportEnabled;
        b.requestTimeoutSeconds = base.requestTimeoutSeconds;

        // Scenario-level fluent overrides (highest priority)
        if (scenario.users() > 0) b.users = scenario.users();
        if (scenario.rampUp() != null) b.rampUp = scenario.rampUp().toSeconds() + "s";
        if (scenario.hold() != null) b.hold = scenario.hold().toSeconds() + "s";
        if (scenario.cooldown() != null) b.cooldown = scenario.cooldown().toSeconds() + "s";
        if (scenario.engine() != null) b.engine = scenario.engine();
        if (scenario.baseUrl() != null) b.baseUrl = scenario.baseUrl();

        if (b.users > b.maxUsers) b.users = b.maxUsers;

        return new LoadTestConfig(b);
    }

    private static void applyAnnotation(Builder b, LoadTest ann) {
        if (ann.users() > 0) b.users = ann.users();
        if (!ann.rampUp().isEmpty()) b.rampUp = ann.rampUp();
        if (!ann.hold().isEmpty()) b.hold = ann.hold();
        if (!ann.cooldown().isEmpty()) b.cooldown = ann.cooldown();
        if (!ann.engine().isEmpty()) b.engine = ann.engine();
        if (!ann.baseUrl().isEmpty()) b.baseUrl = ann.baseUrl();
    }

    private static TestFlyConfig.LoadTest yamlConfig() {
        try {
            TestFlyConfig config = TestFlyContext.getConfig();
            return config != null ? config.getLoadTest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public String getBaseUrl() { return baseUrl; }
    public int getUsers() { return users; }
    public String getRampUp() { return rampUp; }
    public String getHold() { return hold; }
    public String getCooldown() { return cooldown; }
    public String getEngine() { return engine; }
    public int getMaxUsers() { return maxUsers; }
    public String getResultsDir() { return resultsDir; }
    public boolean isReportEnabled() { return reportEnabled; }
    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }

    public long getRampUpSeconds() {
        return TestFlyConfig.LoadTest.parseDurationSeconds(rampUp, 10);
    }

    public long getHoldSeconds() {
        return TestFlyConfig.LoadTest.parseDurationSeconds(hold, 30);
    }

    public long getCooldownSeconds() {
        return TestFlyConfig.LoadTest.parseDurationSeconds(cooldown, 5);
    }

    @Override
    public String toString() {
        return String.format("LoadTestConfig{users=%d, rampUp=%s, hold=%s, cooldown=%s, engine=%s, baseUrl=%s}",
                users, rampUp, hold, cooldown, engine, baseUrl);
    }

    // ── Builder ──────────────────────────────────────────────────────────

    private static final class Builder {
        String baseUrl;
        int users = 10;
        String rampUp = "10s";
        String hold = "30s";
        String cooldown = "5s";
        String engine = "auto";
        int maxUsers = 1000;
        String resultsDir = "target/loadtest";
        boolean reportEnabled = true;
        int requestTimeoutSeconds = 30;
    }
}
