package io.testfly.config;

import io.testfly.api.TestFlyApi;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@TestFlyApi(since = "0.1.0")
public final class TestFlyConfig {

    private Browser browser;
    private Execution execution;
    private Retry retry;
    private Timeouts timeouts;
    private Ci ci;
    private Api api;

    // --- getters ---
    public Browser getBrowser() {
        return browser;
    }

    public Execution getExecution() {
        return execution;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    // --- setters (required for SnakeYAML) ---
    public void setBrowser(Browser browser) {
        this.browser = browser;
    }

    public void setExecution(Execution execution) {
        this.execution = execution;
    }

    public Timeouts getTimeouts() {
        return timeouts;
    }

    public void setTimeouts(Timeouts timeouts) {
        this.timeouts = timeouts;
    }

    public Ci getCi() {
        return ci;
    }

    public void setCi(Ci ci) {
        this.ci = ci;
    }

    private Network network;

    public Network getNetwork() {
        return network;
    }

    public void setNetwork(Network network) {
        this.network = network;
    }

    private TestManagement testManagement;

    public TestManagement getTestManagement() {
        return testManagement;
    }

    public void setTestManagement(TestManagement testManagement) {
        this.testManagement = testManagement;
    }

    public static final class Network {
        private boolean interceptEnabled = false;

        /** Overridable from the umbrella via {@code features.network}. */
        public boolean isInterceptEnabled() {
            return FeatureGate.enabled(FeatureGate.NETWORK, interceptEnabled);
        }

        public void setInterceptEnabled(boolean interceptEnabled) {
            this.interceptEnabled = interceptEnabled;
        }

        /**
         * Glob URL patterns to abort globally (trackers, ads) without a per-test
         * {@code mockRoute} call. Lowest-priority rule — an explicit route/stub
         * matching the same URL always wins. Defaults to an empty list.
         */
        private java.util.List<String> blockUrls = new java.util.ArrayList<>();

        public java.util.List<String> getBlockUrls() {
            return blockUrls;
        }

        public void setBlockUrls(java.util.List<String> blockUrls) {
            this.blockUrls = (blockUrls != null) ? blockUrls : new java.util.ArrayList<>();
        }
    }

    // =========================

    public static final class Browser {
        private String name;
        private boolean headless;
        private List<String> arguments;
        private Map<String, Object> capabilities;

        /**
         * Controls when the WebDriver session is closed.
         * <ul>
         * <li>{@code per-test} (default) — browser closes after every test method</li>
         * <li>{@code per-suite} — browser stays open for the entire suite; one instance
         * per thread, closed only when the suite finishes. Saves startup time on
         * large sequential suites.</li>
         * </ul>
         */
        private String lifecycle = "per-test";
        private String downloadDir = "./target/downloads";
        private boolean captureConsoleErrors = false;
        private boolean failOnConsoleErrors = false;
        private boolean bidi = false;
        private List<String> matrix = Collections.emptyList();
        private String device; // optional device profile name, e.g. "iPhone 14"
        private Window window = new Window();

        /**
         * Window sizing applied right after the browser session starts.
         * Never {@code null}; defaults to {@code mode: default}.
         */
        public Window getWindow() {
            return window;
        }

        public void setWindow(Window window) {
            this.window = window != null ? window : new Window();
        }

        public String getDevice() {
            return device;
        }

        public void setDevice(String device) {
            this.device = device;
        }

        public String getDownloadDir() {
            return downloadDir;
        }

        public void setDownloadDir(String downloadDir) {
            this.downloadDir = downloadDir;
        }

        public boolean isCaptureConsoleErrors() {
            return FeatureGate.enabled(FeatureGate.CONSOLE_ERRORS, captureConsoleErrors);
        }

        public void setCaptureConsoleErrors(boolean captureConsoleErrors) {
            this.captureConsoleErrors = captureConsoleErrors;
        }

        public boolean isFailOnConsoleErrors() {
            return failOnConsoleErrors;
        }

        /**
         * Starts the browser with a WebDriver BiDi connection ({@code webSocketUrl}). Console error
         * capture uses it, when {@code captureConsoleErrors} is on, to see every error from the
         * moment a page starts loading, in browsers without a WebDriver browser log (Firefox).
         * Safari does not support BiDi.
         */
        @TestFlyApi(since = "1.1.0")
        public boolean isBidi() {
            return bidi;
        }

        @TestFlyApi(since = "1.1.0")
        public void setBidi(boolean bidi) {
            this.bidi = bidi;
        }

        public void setFailOnConsoleErrors(boolean failOnConsoleErrors) {
            this.failOnConsoleErrors = failOnConsoleErrors;
        }

        public List<String> getMatrix() {
            return matrix;
        }

        public void setMatrix(List<String> matrix) {
            this.matrix = matrix != null ? matrix : Collections.emptyList();
        }

        public List<String> getArguments() {
            return arguments;
        }

        public void setArguments(List<String> arguments) {
            this.arguments = arguments;
        }

        public Map<String, Object> getCapabilities() {
            return capabilities;
        }

        public void setCapabilities(Map<String, Object> capabilities) {
            this.capabilities = capabilities;
        }

        public String getName() {
            return name;
        }

        public boolean isHeadless() {
            return headless;
        }

        // setters REQUIRED
        public void setName(String name) {
            this.name = name;
        }

        public void setHeadless(boolean headless) {
            this.headless = headless;
        }

        public String getLifecycle() {
            return lifecycle;
        }

        public void setLifecycle(String lifecycle) {
            this.lifecycle = lifecycle;
        }
    }

    /**
     * Browser window sizing ({@code browser.window} in testfly.yml).
     * <ul>
     * <li>{@code default} — leave the window as the browser opens it</li>
     * <li>{@code maximized} — maximize to the current screen; no width/height</li>
     * <li>{@code fullscreen} — switch to full screen; no width/height</li>
     * <li>{@code size} — exact {@code width} x {@code height}; both required</li>
     * </ul>
     * Headless sessions have no screen, so {@code maximized} and
     * {@code fullscreen} fall back to a built-in 1920x1080 window there.
     */
    public static final class Window {
        private String mode = "default";
        private Integer width;
        private Integer height;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode != null ? mode : "default";
        }

        public Integer getWidth() {
            return width;
        }

        public void setWidth(Integer width) {
            this.width = width;
        }

        public Integer getHeight() {
            return height;
        }

        public void setHeight(Integer height) {
            this.height = height;
        }
    }

    public static final class Execution {
        private String mode;
        private String baseUrl;
        private String gridUrl;
        private String parallel = "none";
        private int threadCount = 1;
        private int maxActiveSessions = 5;
        private int sessionWaitSeconds = 300;

        public String getMode() {
            return mode;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        // setters REQUIRED
        public void setMode(String mode) {
            this.mode = mode;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public void setGridUrl(String gridUrl) {
            this.gridUrl = gridUrl;
        }

        public String getGridUrl() {
            return gridUrl;
        }

        public int getThreadCount() {
            return threadCount;
        }

        public void setThreadCount(int threadCount) {
            this.threadCount = threadCount;
        }

        public String getParallel() {
            return parallel;
        }

        public void setParallel(String parallel) {
            this.parallel = parallel;
        }

        public int getMaxActiveSessions() {
            return maxActiveSessions;
        }

        public void setMaxActiveSessions(int maxActiveSessions) {
            this.maxActiveSessions = maxActiveSessions;
        }

        /**
         * Seconds a test waits for a free browser slot when {@code maxActiveSessions}
         * are all in use. {@code 0} means do not wait. Default {@code 300}.
         */
        public int getSessionWaitSeconds() {
            return sessionWaitSeconds;
        }

        public void setSessionWaitSeconds(int sessionWaitSeconds) {
            this.sessionWaitSeconds = sessionWaitSeconds;
        }

        private BrowserStack browserstack = new BrowserStack();
        private SauceLabs saucelabs = new SauceLabs();

        public BrowserStack getBrowserstack() {
            return browserstack;
        }

        public void setBrowserstack(BrowserStack v) {
            this.browserstack = v;
        }

        public SauceLabs getSaucelabs() {
            return saucelabs;
        }

        public void setSaucelabs(SauceLabs v) {
            this.saucelabs = v;
        }

        private Sharding sharding = new Sharding();

        public Sharding getSharding() {
            return sharding;
        }

        public void setSharding(Sharding sharding) {
            this.sharding = sharding;
        }

        public static final class Sharding {
            private boolean enabled = false;
            private int total = 1;
            private int index = 0;
            private String strategy = "lpt";
            private String metricsFile = "target/testfly-metrics.json";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getTotal() {
                return total;
            }

            public void setTotal(int total) {
                this.total = total;
            }

            public int getIndex() {
                return index;
            }

            public void setIndex(int index) {
                this.index = index;
            }

            public String getStrategy() {
                return strategy;
            }

            public void setStrategy(String strategy) {
                this.strategy = strategy;
            }

            public String getMetricsFile() {
                return metricsFile;
            }

            public void setMetricsFile(String metricsFile) {
                this.metricsFile = metricsFile;
            }
        }

        public static final class BrowserStack {
            private String username;
            private String accessKey;
            private String os;
            private String osVersion;
            private String browser = "chrome";
            private String browserVersion = "latest";
            private String device;
            private boolean realMobile = true;
            private java.util.Map<String, Object> capabilities = new java.util.LinkedHashMap<>();

            public String getUsername() {
                return username;
            }

            public void setUsername(String v) {
                this.username = v;
            }

            public String getAccessKey() {
                return accessKey;
            }

            public void setAccessKey(String v) {
                this.accessKey = v;
            }

            public String getOs() {
                return os;
            }

            public void setOs(String v) {
                this.os = v;
            }

            public String getOsVersion() {
                return osVersion;
            }

            public void setOsVersion(String v) {
                this.osVersion = v;
            }

            public String getBrowser() {
                return browser;
            }

            public void setBrowser(String v) {
                this.browser = v;
            }

            public String getBrowserVersion() {
                return browserVersion;
            }

            public void setBrowserVersion(String v) {
                this.browserVersion = v;
            }

            public String getDevice() {
                return device;
            }

            public void setDevice(String v) {
                this.device = v;
            }

            public boolean isRealMobile() {
                return realMobile;
            }

            public void setRealMobile(boolean v) {
                this.realMobile = v;
            }

            public java.util.Map<String, Object> getCapabilities() {
                return capabilities;
            }

            public void setCapabilities(java.util.Map<String, Object> v) {
                this.capabilities = v != null ? v : new java.util.LinkedHashMap<>();
            }
        }

        public static final class SauceLabs {
            private String username;
            private String accessKey;
            private String region = "us-west-1";
            private String platformName = "Windows 11";
            private String browser = "chrome";
            private String browserVersion = "latest";
            private java.util.Map<String, Object> capabilities = new java.util.LinkedHashMap<>();

            public String getUsername() {
                return username;
            }

            public void setUsername(String v) {
                this.username = v;
            }

            public String getAccessKey() {
                return accessKey;
            }

            public void setAccessKey(String v) {
                this.accessKey = v;
            }

            public String getRegion() {
                return region;
            }

            public void setRegion(String v) {
                this.region = v;
            }

            public String getPlatformName() {
                return platformName;
            }

            public void setPlatformName(String v) {
                this.platformName = v;
            }

            public String getBrowser() {
                return browser;
            }

            public void setBrowser(String v) {
                this.browser = v;
            }

            public String getBrowserVersion() {
                return browserVersion;
            }

            public void setBrowserVersion(String v) {
                this.browserVersion = v;
            }

            public java.util.Map<String, Object> getCapabilities() {
                return capabilities;
            }

            public void setCapabilities(java.util.Map<String, Object> v) {
                this.capabilities = v != null ? v : new java.util.LinkedHashMap<>();
            }
        }
    }

    public static final class Retry {
        private boolean enabled = true;
        private int maxAttempts = 1;
        private boolean maxAttemptsSet = false; // true only when YAML explicitly sets the value

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        /**
         * Returns null when maxAttempts was not explicitly set by YAML (used by
         * defaults logic).
         */
        public Integer getRawMaxAttempts() {
            return maxAttemptsSet ? maxAttempts : null;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            this.maxAttemptsSet = true;
        }
    }

    public static final class Timeouts {
        private int explicit;
        private int pageLoad;

        public int getExplicit() {
            return explicit;
        }

        public void setExplicit(int explicit) {
            this.explicit = explicit;
        }

        public int getPageLoad() {
            return pageLoad;
        }

        public void setPageLoad(int pageLoad) {
            this.pageLoad = pageLoad;
        }
    }

    /**
     * CI/CD build quality gates and metadata capture.
     * All thresholds are disabled by default (0 / -1).
     */
    public static final class Ci {
        /**
         * Minimum pass rate (0–100) required to pass the build.
         * 0 means disabled. Example: 80 → build fails if fewer than 80% of tests pass.
         */
        private double failOnPassRateBelow = 0;

        /**
         * Maximum number of flaky tests (retried but eventually passed) allowed.
         * -1 means disabled.
         */
        private int maxFlakyTests = -1;

        /**
         * Capture structured CI metadata (provider, branch, commit, build URL, etc.)
         * and include it in HTML/JUnit reports and metrics JSON.
         * {@code null} means "auto" — capture when running inside a recognized CI
         * environment. Set to {@code false} to disable, or {@code true} to force.
         */
        private Boolean captureMetadata;

        public double getFailOnPassRateBelow() {
            return failOnPassRateBelow;
        }

        public void setFailOnPassRateBelow(double failOnPassRateBelow) {
            this.failOnPassRateBelow = failOnPassRateBelow;
        }

        public int getMaxFlakyTests() {
            return maxFlakyTests;
        }

        public void setMaxFlakyTests(int maxFlakyTests) {
            this.maxFlakyTests = maxFlakyTests;
        }

        /**
         * Returns true if metadata capture is enabled. When the user has not
         * explicitly configured the flag, it defaults to {@code true} only
         * when running inside a recognized CI environment.
         */
        public boolean isCaptureMetadata() {
            return captureMetadata != null ? captureMetadata : io.testfly.ci.CiEnvironmentDetector.isCI();
        }

        public void setCaptureMetadata(boolean captureMetadata) {
            this.captureMetadata = captureMetadata;
        }
    }

    private Recording recording;

    public Recording getRecording() {
        return recording;
    }

    public void setRecording(Recording recording) {
        this.recording = recording;
    }

    private Reporting reporting = new Reporting();

    public Reporting getReporting() {
        return reporting != null ? reporting : (reporting = new Reporting());
    }

    public void setReporting(Reporting reporting) {
        this.reporting = reporting != null ? reporting : new Reporting();
    }

    private Notifications notifications;

    public Notifications getNotifications() {
        return notifications;
    }

    public void setNotifications(Notifications notifications) {
        this.notifications = notifications;
    }

    public static final class Notifications {
        private Slack slack;
        private Teams teams;

        public Slack getSlack() {
            return slack;
        }

        public void setSlack(Slack slack) {
            this.slack = slack;
        }

        public Teams getTeams() {
            return teams;
        }

        public void setTeams(Teams teams) {
            this.teams = teams;
        }

        public static final class Slack {
            private String webhookUrl;
            private boolean notifyOnFailureOnly = false;

            public String getWebhookUrl() {
                return webhookUrl;
            }

            public void setWebhookUrl(String webhookUrl) {
                this.webhookUrl = webhookUrl;
            }

            public boolean isNotifyOnFailureOnly() {
                return notifyOnFailureOnly;
            }

            public void setNotifyOnFailureOnly(boolean v) {
                this.notifyOnFailureOnly = v;
            }
        }

        public static final class Teams {
            private String webhookUrl;
            private boolean notifyOnFailureOnly = false;

            public String getWebhookUrl() {
                return webhookUrl;
            }

            public void setWebhookUrl(String webhookUrl) {
                this.webhookUrl = webhookUrl;
            }

            public boolean isNotifyOnFailureOnly() {
                return notifyOnFailureOnly;
            }

            public void setNotifyOnFailureOnly(boolean v) {
                this.notifyOnFailureOnly = v;
            }
        }
    }

    public static final class Reporting {
        private boolean allureEnabled = false;
        private boolean mergeRuns = false;
        private int historyRuns = 10;
        private boolean screenshotOnFailure = true;
        private boolean htmlReport = true;
        private ReportPortal reportPortal = new ReportPortal();
        private Allure allure;

        public boolean isScreenshotOnFailure() {
            return screenshotOnFailure;
        }

        public void setScreenshotOnFailure(boolean val) {
            this.screenshotOnFailure = val;
        }

        public boolean isHtmlReport() {
            return htmlReport;
        }

        public void setHtmlReport(boolean val) {
            this.htmlReport = val;
        }

        /**
         * Returns {@code true} when Allure reporting is enabled via either the
         * flat key ({@code reporting.allureEnabled: true}) or the nested key
         * ({@code reporting.allure.enabled: true}). The nested form matches the
         * documentation; the flat form is kept for backward compatibility.
         */
        public boolean isAllureEnabled() {
            if (allureEnabled)
                return true;
            return allure != null && allure.isEnabled();
        }

        public void setAllureEnabled(boolean allureEnabled) {
            this.allureEnabled = allureEnabled;
        }

        public Allure getAllure() {
            return allure;
        }

        public void setAllure(Allure allure) {
            this.allure = allure;
        }

        public boolean isMergeRuns() {
            String sysProp = System.getProperty("testfly.merge");
            if (sysProp != null && !sysProp.isBlank()) {
                return Boolean.parseBoolean(sysProp) || "true".equalsIgnoreCase(sysProp);
            }
            return mergeRuns;
        }

        public void setMergeRuns(boolean mergeRuns) {
            this.mergeRuns = mergeRuns;
        }

        public int getHistoryRuns() {
            return historyRuns;
        }

        public void setHistoryRuns(int historyRuns) {
            this.historyRuns = historyRuns > 0 ? historyRuns : 10;
        }

        public ReportPortal getReportPortal() {
            return reportPortal;
        }

        public void setReportPortal(ReportPortal v) {
            this.reportPortal = v != null ? v : new ReportPortal();
        }

        /**
         * Alias for {@link #setReportPortal(ReportPortal)} that accepts the
         * all-lowercase YAML key {@code reportportal} used in the documentation.
         */
        public void setReportportal(ReportPortal v) {
            setReportPortal(v);
        }

        public static final class ReportPortal {
            private boolean enabled = false;
            private String endpoint;
            private String apiKey;
            private String project = "superadmin_personal";
            private String launch = "TestFly Suite";
            private String description = "Automated test execution powered by TestFly";
            private String attributes = "";
            /**
             * Run type for launch name enrichment:
             * <ul>
             * <li>{@code auto} — auto-detect from test classes (default)</li>
             * <li>{@code api} — force API label</li>
             * <li>{@code web} — force Web label</li>
             * </ul>
             */
            private String type = "auto";
            /**
             * ReportPortal listener mode:
             * <ul>
             * <li>{@code default} — standard TestNG listener</li>
             * <li>{@code step} — each test method as a step</li>
             * </ul>
             */
            private String mode = "default";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean v) {
                this.enabled = v;
            }

            public String getEndpoint() {
                return endpoint;
            }

            public void setEndpoint(String v) {
                this.endpoint = v;
            }

            public String getApiKey() {
                return apiKey;
            }

            public void setApiKey(String v) {
                this.apiKey = v;
            }

            public String getProject() {
                return project;
            }

            public void setProject(String v) {
                this.project = v;
            }

            public String getLaunch() {
                return launch;
            }

            public void setLaunch(String v) {
                this.launch = v;
            }

            public String getDescription() {
                return description;
            }

            public void setDescription(String v) {
                this.description = v;
            }

            public String getAttributes() {
                return attributes;
            }

            public void setAttributes(String v) {
                this.attributes = v;
            }

            public String getType() {
                return type;
            }

            public void setType(String v) {
                this.type = v;
            }

            public String getMode() {
                return mode;
            }

            public void setMode(String v) {
                this.mode = v;
            }
        }

        /**
         * Nested Allure configuration matching the documented YAML structure:
         * 
         * <pre>
         * reporting:
         *   allure:
         *     enabled: true
         * </pre>
         * 
         * The flat key {@code reporting.allureEnabled} is also accepted for
         * backward compatibility; see {@link #isAllureEnabled()}.
         */
        public static final class Allure {
            private boolean enabled = false;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }
    }

    public static final class Recording {
        private boolean enabled = false;
        private String mode = "retain-on-failure";
        private String format = "mp4";
        private boolean cdp = true;
        private int fps = 2;
        private int maxDurationSeconds = 60;

        /** Overridable from the umbrella via {@code features.recording}. */
        public boolean isEnabled() {
            return FeatureGate.enabled(FeatureGate.RECORDING, enabled);
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode != null ? mode : "retain-on-failure";
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format != null ? format : "mp4";
        }

        public boolean isMp4() {
            return "mp4".equalsIgnoreCase(format);
        }

        public boolean isCdp() {
            return cdp;
        }

        public void setCdp(boolean cdp) {
            this.cdp = cdp;
        }

        public int getFps() {
            return fps;
        }

        public void setFps(int fps) {
            this.fps = fps;
        }

        public int getMaxDurationSeconds() {
            return maxDurationSeconds;
        }

        public void setMaxDurationSeconds(int maxDurationSeconds) {
            this.maxDurationSeconds = maxDurationSeconds;
        }

        public boolean isRetainOnFailure() {
            return "retain-on-failure".equalsIgnoreCase(mode);
        }

        public boolean isRecordAll() {
            return isEnabled() && ("on".equalsIgnoreCase(mode) || "always".equalsIgnoreCase(mode));
        }

        public boolean shouldRecord() {
            return isEnabled() && !"off".equalsIgnoreCase(mode);
        }
    }

    private Visual visual;

    public Visual getVisual() {
        return visual;
    }

    public void setVisual(Visual visual) {
        this.visual = visual;
    }

    private Tracing tracing;

    public Tracing getTracing() {
        return tracing;
    }

    public void setTracing(Tracing tracing) {
        this.tracing = tracing;
    }

    private Actionability actionability = new Actionability();

    @TestFlyApi(since = "1.1.0")
    public Actionability getActionability() {
        return actionability;
    }

    @TestFlyApi(since = "1.1.0")
    public void setActionability(Actionability actionability) {
        this.actionability = actionability;
    }

    private Locators locators;

    public Locators getLocators() {
        return locators;
    }

    public void setLocators(Locators locators) {
        this.locators = locators;
    }

    /**
     * Playwright-style actionability checks run before click/hover/type: the element
     * must be visible, enabled (where relevant), stable (not animating) and not
     * covered by another element.
     */
    @TestFlyApi(since = "1.1.0")
    public static final class Actionability {
        private boolean enabled = true;
        private int stabilityIntervalMs = 100;
        private boolean hitTargetCheck = true;
        private boolean autoScroll = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getStabilityIntervalMs() {
            return stabilityIntervalMs;
        }

        public void setStabilityIntervalMs(int stabilityIntervalMs) {
            this.stabilityIntervalMs = stabilityIntervalMs;
        }

        public boolean isHitTargetCheck() {
            return hitTargetCheck;
        }

        public void setHitTargetCheck(boolean hitTargetCheck) {
            this.hitTargetCheck = hitTargetCheck;
        }

        public boolean isAutoScroll() {
            return autoScroll;
        }

        public void setAutoScroll(boolean autoScroll) {
            this.autoScroll = autoScroll;
        }
    }

    public static final class Locators {
        private boolean selfHealing = false;
        private boolean aiHealing = false;
        private int maxDomTokens = 8000;
        private String testIdAttribute = "data-testid";
        // TODO(2.0): consider defaulting to true, as Playwright does.
        private boolean strict = false;

        /**
         * When true, an action on a locator that matches more than one visible element
         * (and is not narrowed with first()/nth()/last()) fails instead of silently
         * using the first match.
         */
        @TestFlyApi(since = "1.1.0")
        public boolean isStrict() {
            return strict;
        }

        @TestFlyApi(since = "1.1.0")
        public void setStrict(boolean strict) {
            this.strict = strict;
        }

        /** Overridable from the umbrella via {@code features.healing}. */
        public boolean isSelfHealing() {
            return FeatureGate.enabled(FeatureGate.HEALING, selfHealing);
        }

        public void setSelfHealing(boolean v) {
            this.selfHealing = v;
        }

        /**
         * Overridable from the umbrella via {@code features.healing}. The AI
         * kill-switch ({@code features.ai} / {@code ai.enabled}) is enforced
         * separately by {@code AiHealingEngine}.
         */
        public boolean isAiHealing() {
            return FeatureGate.enabled(FeatureGate.HEALING, aiHealing);
        }

        public void setAiHealing(boolean v) {
            this.aiHealing = v;
        }

        public int getMaxDomTokens() {
            return maxDomTokens;
        }

        public void setMaxDomTokens(int maxDomTokens) {
            this.maxDomTokens = maxDomTokens;
        }

        public String getTestIdAttribute() {
            return testIdAttribute;
        }

        public void setTestIdAttribute(String value) {
            this.testIdAttribute = value;
        }
    }

    private java.util.Map<String, Boolean> features = new java.util.LinkedHashMap<>();

    /**
     * Master switchboard for optional framework modules, keyed by module name.
     *
     * <p>
     * Values are tri-state: an absent key (or a {@code null} value) means
     * "not decided here — the module's own settings apply", while an explicit
     * {@code true} / {@code false} overrides them.
     *
     * <pre>
     * features:
     *   ai: false          # disable every AI/agentic surface
     *   recording: true    # force video recording on
     * </pre>
     *
     * <p>
     * Read these through {@link FeatureGate} rather than directly — it
     * applies the tri-state resolution and tolerates an uninitialized context.
     * Valid names are listed by {@link FeatureGate#knownFeatures()}.
     */
    public java.util.Map<String, Boolean> getFeatures() {
        return features;
    }

    public void setFeatures(java.util.Map<String, Boolean> features) {
        this.features = (features != null) ? features : new java.util.LinkedHashMap<>();
    }

    private Ai ai;

    public Ai getAi() {
        return ai;
    }

    public void setAi(Ai ai) {
        this.ai = ai;
    }

    public static final class Ai {
        /**
         * Master switch for every AI-driven surface: failure analysis, patch
         * generation, AI locator healing, {@code aiAssert(...)} and the agentic
         * {@code act(...)} engine. Defaults to {@code true}; the granular flags
         * below still decide <em>which</em> surfaces run, but none of them can
         * run while this is {@code false}.
         *
         * <p>
         * Overridable from the umbrella via {@code features.ai}.
         */
        private boolean enabled = true;
        private boolean failureAnalysis = false;
        private boolean generatePatch = false;
        private String provider = "claude";
        private String baseUrl = null;
        private String apiKey = null;
        private String model = null;
        private String language = "en";
        private int timeoutSeconds = 20;

        /** Overridable from the umbrella via {@code features.ai}. */
        public boolean isEnabled() {
            return FeatureGate.enabled(FeatureGate.AI, enabled);
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isFailureAnalysis() {
            return failureAnalysis;
        }

        public void setFailureAnalysis(boolean v) {
            this.failureAnalysis = v;
        }

        public boolean isGeneratePatch() {
            return generatePatch;
        }

        public void setGeneratePatch(boolean v) {
            this.generatePatch = v;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String v) {
            this.provider = v;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String v) {
            this.baseUrl = v;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String v) {
            this.apiKey = v;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String v) {
            this.model = v;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String v) {
            this.language = v;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int v) {
            this.timeoutSeconds = v;
        }

        private boolean actionCache = true;

        public boolean isActionCache() {
            return actionCache;
        }

        public void setActionCache(boolean v) {
            this.actionCache = v;
        }
    }

    private Flakiness flakiness;

    public Flakiness getFlakiness() {
        return flakiness;
    }

    public void setFlakiness(Flakiness flakiness) {
        this.flakiness = flakiness;
    }

    public static final class Flakiness {
        private int historyRuns = 20;
        private double highRiskThreshold = 33.0;
        private boolean failOnHighFlakiness = false;

        public int getHistoryRuns() {
            return historyRuns;
        }

        public void setHistoryRuns(int v) {
            this.historyRuns = v;
        }

        public double getHighRiskThreshold() {
            return highRiskThreshold;
        }

        public void setHighRiskThreshold(double v) {
            this.highRiskThreshold = v;
        }

        public boolean isFailOnHighFlakiness() {
            return failOnHighFlakiness;
        }

        public void setFailOnHighFlakiness(boolean v) {
            this.failOnHighFlakiness = v;
        }
    }

    public static final class Tracing {
        private boolean enabled = false;
        private boolean captureOnPass = false;
        private String snapshots = "aria";
        private int snapshotMaxKb = 256;
        private boolean snapshotOnFailureAfter = true;

        /** Overridable from the umbrella via {@code features.tracing}. */
        public boolean isEnabled() {
            return FeatureGate.enabled(FeatureGate.TRACING, enabled);
        }

        public void setEnabled(boolean v) {
            this.enabled = v;
        }

        public boolean isCaptureOnPass() {
            return captureOnPass;
        }

        public void setCaptureOnPass(boolean v) {
            this.captureOnPass = v;
        }

        /**
         * What each step records for the trace: {@code none}, {@code aria} (the accessibility
         * tree, default), {@code dom} (the pruned page HTML) or {@code both}.
         */
        @TestFlyApi(since = "1.1.0")
        public String getSnapshots() {
            return snapshots;
        }

        @TestFlyApi(since = "1.1.0")
        public void setSnapshots(String v) {
            String mode = v == null ? "" : v.trim().toLowerCase(java.util.Locale.ROOT);
            if (!mode.equals("none") && !mode.equals("aria") && !mode.equals("dom") && !mode.equals("both")) {
                throw new IllegalArgumentException(
                        "tracing.snapshots must be one of none, aria, dom, both; got: '" + v + "'");
            }
            this.snapshots = mode;
        }

        /** Largest snapshot kept per step, in kilobytes; longer ones are cut with a marker. */
        @TestFlyApi(since = "1.1.0")
        public int getSnapshotMaxKb() {
            return snapshotMaxKb;
        }

        @TestFlyApi(since = "1.1.0")
        public void setSnapshotMaxKb(int v) {
            if (v < 1) {
                throw new IllegalArgumentException("tracing.snapshotMaxKb must be at least 1; got: " + v);
            }
            this.snapshotMaxKb = v;
        }

        /** Whether a failed test also records an accessibility snapshot taken after its last step. */
        @TestFlyApi(since = "1.1.0")
        public boolean isSnapshotOnFailureAfter() {
            return snapshotOnFailureAfter;
        }

        @TestFlyApi(since = "1.1.0")
        public void setSnapshotOnFailureAfter(boolean v) {
            this.snapshotOnFailureAfter = v;
        }
    }

    public static final class Visual {
        private String baselineDir = "src/test/resources/baselines";
        private String diffDir = "target/visual-diffs";
        private double defaultTolerance = 0;
        private boolean updateBaselines = false;
        private boolean failOnNewBaseline = false;

        public String getBaselineDir() {
            return baselineDir;
        }

        public void setBaselineDir(String v) {
            this.baselineDir = v;
        }

        public String getDiffDir() {
            return diffDir;
        }

        public void setDiffDir(String v) {
            this.diffDir = v;
        }

        public double getDefaultTolerance() {
            return defaultTolerance;
        }

        public void setDefaultTolerance(double v) {
            this.defaultTolerance = v;
        }

        public boolean isUpdateBaselines() {
            return updateBaselines;
        }

        public void setUpdateBaselines(boolean v) {
            this.updateBaselines = v;
        }

        public boolean isFailOnNewBaseline() {
            return failOnNewBaseline;
        }

        public void setFailOnNewBaseline(boolean v) {
            this.failOnNewBaseline = v;
        }
    }

    private Email email;

    public Email getEmail() {
        return email;
    }

    public void setEmail(Email email) {
        this.email = email;
    }

    public static final class Email {
        private String provider = "mailhog";
        private int timeoutSeconds = 30;
        private int pollIntervalMs = 1000;
        private boolean autoClear = false;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String v) {
            this.provider = v;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int v) {
            this.timeoutSeconds = v;
        }

        public int getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(int v) {
            this.pollIntervalMs = v;
        }

        public boolean isAutoClear() {
            return autoClear;
        }

        public void setAutoClear(boolean v) {
            this.autoClear = v;
        }

        private Mailhog mailhog = new Mailhog();
        private Mailtrap mailtrap = new Mailtrap();
        private Outlook outlook = new Outlook();
        private Imap imap = new Imap();

        public Mailhog getMailhog() {
            return mailhog;
        }

        public void setMailhog(Mailhog v) {
            this.mailhog = v;
        }

        public Mailtrap getMailtrap() {
            return mailtrap;
        }

        public void setMailtrap(Mailtrap v) {
            this.mailtrap = v;
        }

        public Outlook getOutlook() {
            return outlook;
        }

        public void setOutlook(Outlook v) {
            this.outlook = v;
        }

        public Imap getImap() {
            return imap;
        }

        public void setImap(Imap v) {
            this.imap = v;
        }

        public static final class Mailhog {
            private String host = "localhost";
            private int port = 8025;

            public String getHost() {
                return host;
            }

            public void setHost(String v) {
                this.host = v;
            }

            public int getPort() {
                return port;
            }

            public void setPort(int v) {
                this.port = v;
            }
        }

        public static final class Mailtrap {
            private String apiToken;
            private String accountId;
            private String inboxId;

            public String getApiToken() {
                return apiToken;
            }

            public void setApiToken(String v) {
                this.apiToken = v;
            }

            public String getAccountId() {
                return accountId;
            }

            public void setAccountId(String v) {
                this.accountId = v;
            }

            public String getInboxId() {
                return inboxId;
            }

            public void setInboxId(String v) {
                this.inboxId = v;
            }
        }

        public static final class Outlook {
            private String tenantId;
            private String clientId;
            private String clientSecret;
            private String mailbox;

            public String getTenantId() {
                return tenantId;
            }

            public void setTenantId(String v) {
                this.tenantId = v;
            }

            public String getClientId() {
                return clientId;
            }

            public void setClientId(String v) {
                this.clientId = v;
            }

            public String getClientSecret() {
                return clientSecret;
            }

            public void setClientSecret(String v) {
                this.clientSecret = v;
            }

            public String getMailbox() {
                return mailbox;
            }

            public void setMailbox(String v) {
                this.mailbox = v;
            }
        }

        public static final class Imap {
            private String host;
            private int port = 993;
            private boolean ssl = true;
            private String username;
            private String password;
            private String folder = "INBOX";

            public String getHost() {
                return host;
            }

            public void setHost(String v) {
                this.host = v;
            }

            public int getPort() {
                return port;
            }

            public void setPort(int v) {
                this.port = v;
            }

            public boolean isSsl() {
                return ssl;
            }

            public void setSsl(boolean v) {
                this.ssl = v;
            }

            public String getUsername() {
                return username;
            }

            public void setUsername(String v) {
                this.username = v;
            }

            public String getPassword() {
                return password;
            }

            public void setPassword(String v) {
                this.password = v;
            }

            public String getFolder() {
                return folder;
            }

            public void setFolder(String v) {
                this.folder = v;
            }
        }
    }

    private Sessions sessions;

    public Sessions getSessions() {
        return sessions;
    }

    public void setSessions(Sessions sessions) {
        this.sessions = sessions;
    }

    public static final class Sessions {
        private int maxPerTest = 2;

        public int getMaxPerTest() {
            return maxPerTest;
        }

        public void setMaxPerTest(int v) {
            this.maxPerTest = v;
        }
    }

    private Performance performance;

    public Performance getPerformance() {
        return performance;
    }

    public void setPerformance(Performance performance) {
        this.performance = performance;
    }

    public static final class Performance {
        private boolean captureOnEveryTest = false;
        private double lcpWarnMs = 0; // 0 = disabled
        private double fcpWarnMs = 0;
        private double ttfbWarnMs = 0;
        private double clsWarn = 0;

        /** Overridable from the umbrella via {@code features.performance}. */
        public boolean isCaptureOnEveryTest() {
            return FeatureGate.enabled(FeatureGate.PERFORMANCE, captureOnEveryTest);
        }

        public void setCaptureOnEveryTest(boolean v) {
            this.captureOnEveryTest = v;
        }

        public double getLcpWarnMs() {
            return lcpWarnMs;
        }

        public void setLcpWarnMs(double v) {
            this.lcpWarnMs = v;
        }

        public double getFcpWarnMs() {
            return fcpWarnMs;
        }

        public void setFcpWarnMs(double v) {
            this.fcpWarnMs = v;
        }

        public double getTtfbWarnMs() {
            return ttfbWarnMs;
        }

        public void setTtfbWarnMs(double v) {
            this.ttfbWarnMs = v;
        }

        public double getClsWarn() {
            return clsWarn;
        }

        public void setClsWarn(double v) {
            this.clsWarn = v;
        }
    }

    private Quarantine quarantine;

    public Quarantine getQuarantine() {
        return quarantine;
    }

    public void setQuarantine(Quarantine quarantine) {
        this.quarantine = quarantine;
    }

    public static final class Quarantine {
        private boolean enabled = true;
        private String cucumberTag = "quarantine";

        /** Overridable from the umbrella via {@code features.quarantine}. */
        public boolean isEnabled() {
            return FeatureGate.enabled(FeatureGate.QUARANTINE, enabled);
        }

        public void setEnabled(boolean v) {
            this.enabled = v;
        }

        /**
         * Tag name to look for in Cucumber {@code .feature} files (without the
         * {@code @} prefix).
         */
        public String getCucumberTag() {
            return cucumberTag;
        }

        public void setCucumberTag(String v) {
            this.cucumberTag = v != null ? v : "quarantine";
        }
    }

    private Clock clock;

    public Clock getClock() {
        return clock;
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public static final class Clock {
        private boolean injectHeader = false;
        private String headerName = "X-Mock-Date";

        public boolean isInjectHeader() {
            return injectHeader;
        }

        public void setInjectHeader(boolean v) {
            this.injectHeader = v;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String v) {
            this.headerName = v;
        }
    }

    private Database database;

    public Database getDatabase() {
        return database;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }

    public static final class Database {
        private String url;
        private String username;
        private String password;
        private String driver;
        private java.util.Map<String, DataSource> datasources = new java.util.LinkedHashMap<>();

        public String getUrl() {
            return url;
        }

        public void setUrl(String v) {
            this.url = v;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String v) {
            this.username = v;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String v) {
            this.password = v;
        }

        public String getDriver() {
            return driver;
        }

        public void setDriver(String v) {
            this.driver = v;
        }

        public java.util.Map<String, DataSource> getDatasources() {
            return datasources;
        }

        public void setDatasources(java.util.Map<String, DataSource> v) {
            this.datasources = v != null ? v : new java.util.LinkedHashMap<>();
        }

        public static final class DataSource {
            private String url;
            private String username;
            private String password;
            private String driver;

            public String getUrl() {
                return url;
            }

            public void setUrl(String v) {
                this.url = v;
            }

            public String getUsername() {
                return username;
            }

            public void setUsername(String v) {
                this.username = v;
            }

            public String getPassword() {
                return password;
            }

            public void setPassword(String v) {
                this.password = v;
            }

            public String getDriver() {
                return driver;
            }

            public void setDriver(String v) {
                this.driver = v;
            }
        }
    }

    public Api getApi() {
        if (api == null) {
            api = new Api();
        }
        return api;
    }

    public void setApi(Api api) {
        this.api = api;
    }

    public static final class Api {
        private String baseUrl;
        private java.util.Map<String, String> baseUrls = new java.util.LinkedHashMap<>();
        private int connectTimeoutSeconds = 30;
        private int maxConcurrentRequests = 0;
        private Ssl ssl = new Ssl();
        public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
        public void setConnectTimeoutSeconds(int value) { connectTimeoutSeconds = value; }
        public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
        public void setMaxConcurrentRequests(int value) { maxConcurrentRequests = value; }
        public Ssl getSsl() { return ssl; }
        public void setSsl(Ssl value) { ssl = value; }
        private Proxy proxy = new Proxy();
        /** HTTP proxy for API calls ({@code api.proxy.host/port/nonProxyHosts}). */
        public Proxy getProxy() { return proxy; }
        public void setProxy(Proxy value) { proxy = value; }
        /**
         * HTTP proxy for API calls. Unset host means direct connections. Proxy authentication is
         * not supported. {@code nonProxyHosts} entries match host names case-insensitively and may
         * use {@code *} wildcards, e.g. {@code localhost}, {@code *.internal}, {@code 10.*}.
         */
        public static final class Proxy {
            private String host;
            private int port;
            private java.util.List<String> nonProxyHosts = new java.util.ArrayList<>();
            public String getHost() { return host; }
            public void setHost(String value) { host = value; }
            public int getPort() { return port; }
            public void setPort(int value) { port = value; }
            public java.util.List<String> getNonProxyHosts() { return nonProxyHosts; }
            public void setNonProxyHosts(java.util.List<String> value) {
                nonProxyHosts = value != null ? value : new java.util.ArrayList<>();
            }
        }
        public static final class Ssl {
            private boolean trustAll;
            private TrustStore trustStore;
            public boolean isTrustAll() { return trustAll; }
            public void setTrustAll(boolean value) { trustAll = value; }
            public TrustStore getTrustStore() { return trustStore; }
            public void setTrustStore(TrustStore value) { trustStore = value; }
        }
        public static final class TrustStore {
            private String path;
            private String type = "PKCS12";
            private String password;
            public String getPath() { return path; }
            public void setPath(String value) { path = value; }
            public String getType() { return type; }
            public void setType(String value) { type = value; }
            public String getPassword() { return password; }
            public void setPassword(String value) { password = value; }
        }
        private int timeoutSeconds = 30;
        private boolean logBody = false;
        private boolean logContext = true;
        private boolean prettyLog = false;
        private boolean logCurl = false;
        private int truncationLimit = 300;
        private java.util.List<String> maskedHeaders = new java.util.ArrayList<>(
                java.util.List.of("Authorization", "Cookie", "X-Api-Key"));
        private java.util.Map<String, AuthStrategy> auth = new java.util.LinkedHashMap<>();
        private RetryConfig retry = new RetryConfig();

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String v) {
            this.baseUrl = v;
        }

        public java.util.Map<String, String> getBaseUrls() {
            return baseUrls;
        }

        public void setBaseUrls(java.util.Map<String, String> v) {
            this.baseUrls = v != null ? v : new java.util.LinkedHashMap<>();
        }

        public String baseUrlFor(String service) {
            if (baseUrls != null && baseUrls.containsKey(service))
                return baseUrls.get(service);
            return baseUrl;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int v) {
            this.timeoutSeconds = v;
        }

        public boolean isLogBody() {
            return logBody;
        }

        public void setLogBody(boolean v) {
            this.logBody = v;
        }

        public boolean isLogContext() {
            return logContext;
        }

        public void setLogContext(boolean v) {
            this.logContext = v;
        }

        public boolean isPrettyLog() {
            return prettyLog;
        }

        public void setPrettyLog(boolean v) {
            this.prettyLog = v;
        }

        public boolean isLogCurl() {
            return logCurl;
        }

        public void setLogCurl(boolean v) {
            this.logCurl = v;
        }

        public int getTruncationLimit() {
            return truncationLimit;
        }

        public void setTruncationLimit(int v) {
            this.truncationLimit = v;
        }

        public java.util.List<String> getMaskedHeaders() {
            return maskedHeaders;
        }

        public void setMaskedHeaders(java.util.List<String> v) {
            this.maskedHeaders = v != null ? v : new java.util.ArrayList<>();
        }

        public java.util.Map<String, AuthStrategy> getAuth() {
            return auth;
        }

        public void setAuth(java.util.Map<String, AuthStrategy> auth) {
            this.auth = auth;
        }

        public RetryConfig getRetry() {
            return retry;
        }

        public void setRetry(RetryConfig v) {
            this.retry = v;
        }

        public static final class RetryConfig {
            private boolean enabled = false;
            private int maxAttempts = 3;
            private long backoffMs = 500;
            private java.util.List<Integer> retryOnStatus = java.util.List.of(502, 503, 504);
            private boolean retryOnException = true;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean v) {
                this.enabled = v;
            }

            public int getMaxAttempts() {
                return maxAttempts;
            }

            public void setMaxAttempts(int v) {
                this.maxAttempts = v;
            }

            public long getBackoffMs() {
                return backoffMs;
            }

            public void setBackoffMs(long v) {
                this.backoffMs = v;
            }

            public java.util.List<Integer> getRetryOnStatus() {
                return retryOnStatus;
            }

            public void setRetryOnStatus(java.util.List<Integer> v) {
                this.retryOnStatus = v;
            }

            public boolean isRetryOnException() {
                return retryOnException;
            }

            public void setRetryOnException(boolean v) {
                this.retryOnException = v;
            }
        }

        public static final class AuthStrategy {
            private String type; // bearer | basic | oauth2 | oauth2_password | oauth2-refresh | apiKey | apikey-query | digest | hmac | hmac-signed
            private String token; // bearer
            private String username; // basic / digest
            private String password; // basic / digest
            private String tokenUrl; // oauth2
            private String clientId; // oauth2
            private String clientSecret; // oauth2
            private String headerName; // apiKey
            private String apiKey; // apiKey / hmac
            private String secret; // hmac
            private String algorithm; // hmac

            public String getType() {
                return type;
            }

            public void setType(String v) {
                this.type = v;
            }

            public String getToken() {
                return token;
            }

            public void setToken(String v) {
                this.token = v;
            }

            public String getUsername() {
                return username;
            }

            public void setUsername(String v) {
                this.username = v;
            }

            public String getPassword() {
                return password;
            }

            public void setPassword(String v) {
                this.password = v;
            }

            public String getTokenUrl() {
                return tokenUrl;
            }

            public void setTokenUrl(String v) {
                this.tokenUrl = v;
            }

            public String getClientId() {
                return clientId;
            }

            public void setClientId(String v) {
                this.clientId = v;
            }

            public String getClientSecret() {
                return clientSecret;
            }

            public void setClientSecret(String v) {
                this.clientSecret = v;
            }

            public String getHeaderName() {
                return headerName;
            }

            public void setHeaderName(String v) {
                this.headerName = v;
            }

            public String getApiKey() {
                return apiKey;
            }

            public void setApiKey(String v) {
                this.apiKey = v;
            }

            public String getSecret() {
                return secret;
            }

            public void setSecret(String v) {
                this.secret = v;
            }

            public String getAlgorithm() {
                return algorithm;
            }

            public void setAlgorithm(String v) {
                this.algorithm = v;
            }

            private String refreshToken; // oauth2-refresh
            private String keyHeader; // hmac-signed (default X-Api-Key)
            private String signatureHeader; // hmac-signed (default X-Signature)
            private String timestampHeader; // hmac-signed (default X-Timestamp; "none" disables)
            private String encoding; // hmac-signed: base64 | hex

            public String getRefreshToken() { return refreshToken; }
            public void setRefreshToken(String v) { this.refreshToken = v; }
            public String getKeyHeader() { return keyHeader; }
            public void setKeyHeader(String v) { this.keyHeader = v; }
            public String getSignatureHeader() { return signatureHeader; }
            public void setSignatureHeader(String v) { this.signatureHeader = v; }
            public String getTimestampHeader() { return timestampHeader; }
            public void setTimestampHeader(String v) { this.timestampHeader = v; }
            public String getEncoding() { return encoding; }
            public void setEncoding(String v) { this.encoding = v; }
        }
    }

    // ── Test Management ──────────────────────────────────────────────────────

    public static final class TestManagement {
        private TestRail testrail = new TestRail();
        private Xray xray = new Xray();

        public TestRail getTestrail() {
            return testrail;
        }

        public void setTestrail(TestRail v) {
            this.testrail = v;
        }

        public Xray getXray() {
            return xray;
        }

        public void setXray(Xray v) {
            this.xray = v;
        }

        public static final class TestRail {
            private boolean enabled = false;
            private String url;
            private String username;
            private String apiKey;
            private int projectId;
            private int suiteId;
            private String runName = "TestFly Run";
            private boolean autoCreateRun = true;
            private int runId; // populated at runtime; may be set explicitly to skip creation

            /** Overridable from the umbrella via {@code features.testManagement}. */
            public boolean isEnabled() {
                return FeatureGate.enabled(FeatureGate.TEST_MANAGEMENT, enabled);
            }

            public void setEnabled(boolean v) {
                this.enabled = v;
            }

            public String getUrl() {
                return url;
            }

            public void setUrl(String v) {
                this.url = v;
            }

            public String getUsername() {
                return username;
            }

            public void setUsername(String v) {
                this.username = v;
            }

            public String getApiKey() {
                return apiKey;
            }

            public void setApiKey(String v) {
                this.apiKey = v;
            }

            public int getProjectId() {
                return projectId;
            }

            public void setProjectId(int v) {
                this.projectId = v;
            }

            public int getSuiteId() {
                return suiteId;
            }

            public void setSuiteId(int v) {
                this.suiteId = v;
            }

            public String getRunName() {
                return runName;
            }

            public void setRunName(String v) {
                this.runName = v;
            }

            public boolean isAutoCreateRun() {
                return autoCreateRun;
            }

            public void setAutoCreateRun(boolean v) {
                this.autoCreateRun = v;
            }

            public int getRunId() {
                return runId;
            }

            public void setRunId(int v) {
                this.runId = v;
            }
        }

        public static final class Xray {
            private boolean enabled = false;
            /**
             * "cloud" (Xray Cloud / Jira Cloud) or "server" (Xray Server / Data Center).
             */
            private String mode = "cloud";
            // Cloud-only fields
            private String clientId;
            private String clientSecret;
            // Server/DC fields
            private String jiraUrl;
            private String username;
            private String password;
            // Shared
            private String projectKey;
            private String testPlanKey;

            /** Overridable from the umbrella via {@code features.testManagement}. */
            public boolean isEnabled() {
                return FeatureGate.enabled(FeatureGate.TEST_MANAGEMENT, enabled);
            }

            public void setEnabled(boolean v) {
                this.enabled = v;
            }

            public String getMode() {
                return mode;
            }

            public void setMode(String v) {
                this.mode = v;
            }

            public String getClientId() {
                return clientId;
            }

            public void setClientId(String v) {
                this.clientId = v;
            }

            public String getClientSecret() {
                return clientSecret;
            }

            public void setClientSecret(String v) {
                this.clientSecret = v;
            }

            public String getJiraUrl() {
                return jiraUrl;
            }

            public void setJiraUrl(String v) {
                this.jiraUrl = v;
            }

            public String getUsername() {
                return username;
            }

            public void setUsername(String v) {
                this.username = v;
            }

            public String getPassword() {
                return password;
            }

            public void setPassword(String v) {
                this.password = v;
            }

            public String getProjectKey() {
                return projectKey;
            }

            public void setProjectKey(String v) {
                this.projectKey = v;
            }

            public String getTestPlanKey() {
                return testPlanKey;
            }

            public void setTestPlanKey(String v) {
                this.testPlanKey = v;
            }
        }
    }

    // ── Load Testing ─────────────────────────────────────────────────────

    private LoadTest loadTest;

    public LoadTest getLoadTest() {
        return loadTest;
    }

    public void setLoadTest(LoadTest loadTest) {
        this.loadTest = loadTest;
    }

    /**
     * Alias for {@link #setLoadTest(LoadTest)} that accepts the all-lowercase
     * YAML key {@code loadtest} used in documentation.
     */
    public void setLoadtest(LoadTest loadTest) {
        setLoadTest(loadTest);
    }

    public LoadTest getLoadtest() {
        return getLoadTest();
    }

    /**
     * Load testing configuration block.
     *
     * <pre>
     * loadtest:
     *   baseUrl: https://api.example.com
     *   users: 50
     *   rampUp: 30s
     *   hold: 60s
     *   cooldown: 10s
     *   engine: auto
     *   resultsDir: target/loadtest
     *   reportEnabled: true
     * </pre>
     */
    public static final class LoadTest {
        private boolean enabled = false;
        private String baseUrl;
        private String engine = "auto";
        private int users = 10;
        private String rampUp = "10s";
        private String hold = "30s";
        private String cooldown = "5s";
        private int maxUsers = 1000;
        private String resultsDir = "target/loadtest";
        private boolean reportEnabled = true;
        private int requestTimeoutSeconds = 30;

        /** Overridable from the umbrella via {@code features.loadtest}. */
        public boolean isEnabled() {
            return FeatureGate.enabled(FeatureGate.LOADTEST, enabled);
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /**
         * Engine selection: {@code auto} (Gatling if available, else JDK),
         * {@code gatling} (require Gatling), or {@code jdk} (always JDK).
         */
        public String getEngine() {
            return engine;
        }

        public void setEngine(String engine) {
            this.engine = engine != null ? engine : "auto";
        }

        public int getUsers() {
            return users;
        }

        public void setUsers(int users) {
            this.users = users > 0 ? users : 10;
        }

        public String getRampUp() {
            return rampUp;
        }

        public void setRampUp(String rampUp) {
            this.rampUp = rampUp != null ? rampUp : "10s";
        }

        public String getHold() {
            return hold;
        }

        public void setHold(String hold) {
            this.hold = hold != null ? hold : "30s";
        }

        public String getCooldown() {
            return cooldown;
        }

        public void setCooldown(String cooldown) {
            this.cooldown = cooldown != null ? cooldown : "5s";
        }

        public int getMaxUsers() {
            return maxUsers;
        }

        public void setMaxUsers(int maxUsers) {
            this.maxUsers = maxUsers > 0 ? maxUsers : 1000;
        }

        public String getResultsDir() {
            return resultsDir;
        }

        public void setResultsDir(String resultsDir) {
            this.resultsDir = resultsDir != null ? resultsDir : "target/loadtest";
        }

        public boolean isReportEnabled() {
            return reportEnabled;
        }

        public void setReportEnabled(boolean reportEnabled) {
            this.reportEnabled = reportEnabled;
        }

        public int getRequestTimeoutSeconds() {
            return requestTimeoutSeconds;
        }

        public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds > 0 ? requestTimeoutSeconds : 30;
        }

        // ── Duration parsing helpers ──

        /** Parses {@code rampUp} string ("30s", "2m", "1h") into seconds. */
        public long getRampUpSeconds() {
            return parseDurationSeconds(rampUp, 10);
        }

        /** Parses {@code hold} string into seconds. */
        public long getHoldSeconds() {
            return parseDurationSeconds(hold, 30);
        }

        /** Parses {@code cooldown} string into seconds. */
        public long getCooldownSeconds() {
            return parseDurationSeconds(cooldown, 5);
        }

        /**
         * Parses a human-readable duration string into seconds.
         * Supported formats: {@code 30s}, {@code 2m}, {@code 1h}, {@code 500ms},
         * or a plain integer (interpreted as seconds).
         */
        public static long parseDurationSeconds(String value, long defaultSeconds) {
            if (value == null || value.isBlank())
                return defaultSeconds;
            String v = value.trim().toLowerCase();
            try {
                if (v.endsWith("ms")) {
                    return Math.max(0, Long.parseLong(v.substring(0, v.length() - 2).trim()) / 1000);
                } else if (v.endsWith("s")) {
                    return Long.parseLong(v.substring(0, v.length() - 1).trim());
                } else if (v.endsWith("m")) {
                    return Long.parseLong(v.substring(0, v.length() - 1).trim()) * 60;
                } else if (v.endsWith("h")) {
                    return Long.parseLong(v.substring(0, v.length() - 1).trim()) * 3600;
                } else {
                    return Long.parseLong(v);
                }
            } catch (NumberFormatException e) {
                return defaultSeconds;
            }
        }
    }

    // ── Native mobile (incubating, ADR 0002) ─────────────────────────────

    private Mobile mobile;

    /**
     * Native mobile settings ({@code mobile:}), or {@code null} when the section is absent.
     * Nothing mobile loads while it is absent. Incubating: see ADR 0002.
     */
    public Mobile getMobile() {
        return mobile;
    }

    public void setMobile(Mobile mobile) {
        this.mobile = mobile;
    }

    /**
     * Native mobile block (incubating). TestFly talks to a user-installed mobilecli server.
     *
     * <pre>
     * mobile:
     *   serverUrl: ${MOBILECLI_URL:-http://localhost:12000}
     *   platform: android            # android | ios
     *   deviceId: ""                 # exact id; wins over deviceName
     *   deviceName: ""               # regex
     *   app:
     *     bundleId: com.example.app
     *     install: ""
     *     reset: relaunch            # none | relaunch | clearData
     *   timeouts:
     *     actionMs: 5000
     *     pollMs: 100
     *     rpcMs: 30000
     *     reserveMs: 300000
     *   allowRemote: false
     *   headers: {}
     *   screenshotOnFailure: true
     * </pre>
     *
     * The defaults live in the field initialisers below; {@code io.testfly.mobile.Mobile}
     * validates the block when a device is opened.
     */
    public static final class Mobile {
        private String serverUrl = "http://localhost:12000";
        private String platform;
        private String deviceId = "";
        private String deviceName = "";
        private App app = new App();
        private Timeouts timeouts = new Timeouts();
        private boolean allowRemote = false;
        private java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        private boolean screenshotOnFailure = true;

        public String getServerUrl() {
            return serverUrl;
        }

        public void setServerUrl(String serverUrl) {
            this.serverUrl = serverUrl == null || serverUrl.isBlank() ? "http://localhost:12000" : serverUrl.trim();
        }

        /** {@code android} or {@code ios}; required when a mobile test runs. */
        public String getPlatform() {
            return platform;
        }

        public void setPlatform(String platform) {
            this.platform = platform;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId != null ? deviceId : "";
        }

        public String getDeviceName() {
            return deviceName;
        }

        public void setDeviceName(String deviceName) {
            this.deviceName = deviceName != null ? deviceName : "";
        }

        public App getApp() {
            return app;
        }

        public void setApp(App app) {
            this.app = app != null ? app : new App();
        }

        public Timeouts getTimeouts() {
            return timeouts;
        }

        public void setTimeouts(Timeouts timeouts) {
            this.timeouts = timeouts != null ? timeouts : new Timeouts();
        }

        /** A non-loopback {@code serverUrl} needs this flag and {@code https://}. */
        public boolean isAllowRemote() {
            return allowRemote;
        }

        public void setAllowRemote(boolean allowRemote) {
            this.allowRemote = allowRemote;
        }

        /** Static headers sent with every request, e.g. {@code Authorization} for a TLS/auth proxy. */
        public java.util.Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(java.util.Map<String, String> headers) {
            this.headers = headers != null ? new java.util.LinkedHashMap<>(headers) : new java.util.LinkedHashMap<>();
        }

        public boolean isScreenshotOnFailure() {
            return screenshotOnFailure;
        }

        public void setScreenshotOnFailure(boolean screenshotOnFailure) {
            this.screenshotOnFailure = screenshotOnFailure;
        }

        /** The app under test. */
        public static final class App {
            private String bundleId = "";
            private String install = "";
            private String reset = "relaunch";

            /** iOS bundle id or Android package. Empty: no app is launched. */
            public String getBundleId() {
                return bundleId;
            }

            public void setBundleId(String bundleId) {
                this.bundleId = bundleId != null ? bundleId : "";
            }

            /** Optional {@code .apk}/{@code .ipa}/{@code .app} path installed when the device opens. */
            public String getInstall() {
                return install;
            }

            public void setInstall(String install) {
                this.install = install != null ? install : "";
            }

            /** {@code none}, {@code relaunch} (default) or {@code clearData}. */
            public String getReset() {
                return reset;
            }

            public void setReset(String reset) {
                this.reset = reset == null || reset.isBlank() ? "relaunch" : reset.trim();
            }
        }

        /** Time budgets in milliseconds. */
        public static final class Timeouts {
            private long actionMs = 5000;
            private long pollMs = 100;
            private long rpcMs = 30000;
            private long reserveMs = 300000;

            /** Auto-wait budget of one action or assertion. */
            public long getActionMs() {
                return actionMs;
            }

            public void setActionMs(long actionMs) {
                this.actionMs = actionMs;
            }

            /** Pause between two polls of the view tree. */
            public long getPollMs() {
                return pollMs;
            }

            public void setPollMs(long pollMs) {
                this.pollMs = pollMs;
            }

            /** Upper bound of one JSON-RPC HTTP request. */
            public long getRpcMs() {
                return rpcMs;
            }

            public void setRpcMs(long rpcMs) {
                this.rpcMs = rpcMs;
            }

            /** How long a session waits for a device another session in this JVM holds. */
            public long getReserveMs() {
                return reserveMs;
            }

            public void setReserveMs(long reserveMs) {
                this.reserveMs = reserveMs;
            }
        }
    }

}
