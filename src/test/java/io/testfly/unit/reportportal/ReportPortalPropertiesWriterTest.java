package io.testfly.unit.reportportal;

import io.testfly.config.TestFlyConfig;
import io.testfly.reporting.reportportal.ReportPortalPropertiesWriter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link ReportPortalPropertiesWriter}.
 * Thread-safe for parallel=methods via singleThreaded + global lock for System properties.
 */
@Test(singleThreaded = true)
public class ReportPortalPropertiesWriterTest {

    private static final Object LOCK = ReportPortalPropertiesWriter.class;

    private TestFlyConfig config;

    @BeforeMethod
    public void setup() {
        synchronized (LOCK) {
            clearRpSystemProperties();
            System.clearProperty("env");
            System.clearProperty("testfly.run.type");
            config = new TestFlyConfig();
            TestFlyConfig.Reporting reporting = new TestFlyConfig.Reporting();
            TestFlyConfig.Reporting.ReportPortal rp = new TestFlyConfig.Reporting.ReportPortal();
            rp.setEnabled(true);
            rp.setEndpoint("http://localhost:8080");
            rp.setApiKey("test-api-key");
            rp.setProject("superadmin_personal");
            rp.setLaunch("TestFly Launch");
            rp.setDescription("Automated run");
            rp.setAttributes("env:ci;branch:main");
            reporting.setReportPortal(rp);
            config.setReporting(reporting);
        }
    }

    @AfterMethod
    public void cleanup() {
        synchronized (LOCK) {
            clearRpSystemProperties();
            System.clearProperty("env");
            System.clearProperty("testfly.run.type");
        }
    }

    private static void clearRpSystemProperties() {
        System.clearProperty("rp.endpoint");
        System.clearProperty("rp.api.key");
        System.clearProperty("rp.project");
        System.clearProperty("rp.launch");
        System.clearProperty("rp.description");
        System.clearProperty("rp.attributes");
    }

    @Test
    public void toProperties_mapsAllFields() {
        synchronized (LOCK) {
            Properties props = ReportPortalPropertiesWriter.toProperties(config);

            assertEquals(props.getProperty("rp.endpoint"), "http://localhost:8080");
            assertEquals(props.getProperty("rp.api.key"), "test-api-key");
            assertEquals(props.getProperty("rp.project"), "superadmin_personal");
            assertTrue(props.getProperty("rp.launch").startsWith("TestFly Launch — "),
                    "Launch should start with configured name + enrichment separator, got: "
                            + props.getProperty("rp.launch"));
            assertTrue(props.getProperty("rp.description").startsWith("Automated run"),
                    "Description should start with configured base, got: "
                            + props.getProperty("rp.description"));
            assertTrue(props.getProperty("rp.description").contains("Run type:"),
                    "Description should contain run type info");
            assertEquals(props.getProperty("rp.attributes"), "env:ci;branch:main");
        }
    }

    @Test
    public void toProperties_omitsBlankAttributes() {
        synchronized (LOCK) {
            config.getReporting().getReportPortal().setAttributes("   ");

            Properties props = ReportPortalPropertiesWriter.toProperties(config);

            assertFalse(props.containsKey("rp.attributes"),
                    "Blank attributes should be omitted");
            assertTrue(props.containsKey("rp.description"),
                    "Description should always be present due to enrichment");
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".*endpoint is required.*")
    public void toProperties_requiresEndpoint() {
        synchronized (LOCK) {
            config.getReporting().getReportPortal().setEndpoint(null);
            ReportPortalPropertiesWriter.toProperties(config);
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".*apiKey is required.*")
    public void toProperties_requiresApiKey() {
        synchronized (LOCK) {
            config.getReporting().getReportPortal().setApiKey("");
            ReportPortalPropertiesWriter.toProperties(config);
        }
    }

    @Test
    public void applyAsSystemProperties_setsProperties() {
        synchronized (LOCK) {
            ReportPortalPropertiesWriter.applyAsSystemProperties(config);

            assertEquals(System.getProperty("rp.endpoint"), "http://localhost:8080");
            assertEquals(System.getProperty("rp.api.key"), "test-api-key");
            assertEquals(System.getProperty("rp.project"), "superadmin_personal");
        }
    }

    @Test
    public void writeToFile_createsPropertiesFile() throws IOException {
        synchronized (LOCK) {
            Path tempDir = Files.createTempDirectory("reportportal-");
            Path target = tempDir.resolve("reportportal.properties");

            Path written = ReportPortalPropertiesWriter.writeToFile(config, target);

            assertTrue(written.toFile().exists(), "properties file should exist: " + written);
            String content = Files.readString(written);
            assertTrue(content.contains("rp.endpoint"), "content should mention rp.endpoint");
            assertTrue(content.contains("rp.api.key=test-api-key"), "content should contain api key");
            assertTrue(content.contains("rp.project=superadmin_personal"), "content should contain project");

            Files.deleteIfExists(written);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void toMaskedString_masksApiKey() {
        synchronized (LOCK) {
            config.getReporting().getReportPortal().setApiKey("my-secret-key");
            String masked = ReportPortalPropertiesWriter.toMaskedString(config);

            assertTrue(masked.contains("rp.api.key"));
            assertFalse(masked.contains("my-secret-key"));
        }
    }

    // ── Launch name enrichment ─────────────────────────────────────────────────

    @Test
    public void enrichLaunchName_containsBaseNameAndRunType() {
        synchronized (LOCK) {
            System.clearProperty("env");
            String launch = ReportPortalPropertiesWriter.enrichLaunchName("My Launch", "API");
            assertTrue(launch.startsWith("My Launch — API"),
                    "Launch should contain base name + run type, got: " + launch);
        }
    }

    @Test
    public void enrichLaunchName_containsTimestamp() {
        synchronized (LOCK) {
            System.clearProperty("env");
            String launch = ReportPortalPropertiesWriter.enrichLaunchName("Test", "Web");
            assertTrue(launch.contains(" | "),
                    "Launch should contain timestamp separator, got: " + launch);
        }
    }

    @Test
    public void enrichLaunchName_withEnv_includesEnvLabel() {
        synchronized (LOCK) {
            System.setProperty("env", "staging");
            try {
                String launch = ReportPortalPropertiesWriter.enrichLaunchName("Demo", "API");
                assertTrue(launch.contains("staging"),
                        "Launch should contain env label, got: " + launch);
            } finally {
                System.clearProperty("env");
            }
        }
    }

    @Test
    public void enrichLaunchName_nullBaseName_usesDefault() {
        synchronized (LOCK) {
            System.clearProperty("env");
            String launch = ReportPortalPropertiesWriter.enrichLaunchName(null, "API");
            assertTrue(launch.startsWith("TestFly Suite — API"),
                    "Null base name should use default, got: " + launch);
        }
    }

    // ── Description enrichment ─────────────────────────────────────────────────

    @Test
    public void enrichDescription_containsRunType() {
        synchronized (LOCK) {
            System.clearProperty("env");
            String desc = ReportPortalPropertiesWriter.enrichDescription("Base", "API", config);
            assertTrue(desc.contains("Run type: API"),
                    "Description should contain run type, got: " + desc);
        }
    }

    @Test
    public void enrichDescription_webRunType_showsExecutionBaseUrl() {
        synchronized (LOCK) {
            System.clearProperty("env");
            TestFlyConfig.Execution exec = new TestFlyConfig.Execution();
            exec.setBaseUrl("https://www.saucedemo.com/");
            config.setExecution(exec);
            TestFlyConfig.Api api = new TestFlyConfig.Api();
            api.setBaseUrl("https://fakeapi.net");
            config.setApi(api);

            String desc = ReportPortalPropertiesWriter.enrichDescription("Test", "Web", config);
            assertTrue(desc.contains("Base URL: https://www.saucedemo.com/"),
                    "Web run should show execution.baseUrl, got: " + desc);
            assertFalse(desc.contains("fakeapi.net"),
                    "Web run should NOT show api.baseUrl, got: " + desc);
        }
    }

    @Test
    public void enrichDescription_apiRunType_showsApiBaseUrl() {
        synchronized (LOCK) {
            System.clearProperty("env");
            TestFlyConfig.Execution exec = new TestFlyConfig.Execution();
            exec.setBaseUrl("https://www.saucedemo.com/");
            config.setExecution(exec);
            TestFlyConfig.Api api = new TestFlyConfig.Api();
            api.setBaseUrl("https://fakeapi.net");
            config.setApi(api);

            String desc = ReportPortalPropertiesWriter.enrichDescription("Test", "API", config);
            assertTrue(desc.contains("Base URL: https://fakeapi.net"),
                    "API run should show api.baseUrl, got: " + desc);
            assertFalse(desc.contains("saucedemo"),
                    "API run should NOT show execution.baseUrl, got: " + desc);
        }
    }

    @Test
    public void enrichDescription_containsTriggeredBy() {
        synchronized (LOCK) {
            System.clearProperty("env");
            String desc = ReportPortalPropertiesWriter.enrichDescription("Base", "Web", config);
            assertTrue(desc.contains("Triggered by:"),
                    "Description should contain triggered-by info, got: " + desc);
        }
    }

    @Test
    public void enrichDescription_nullBase_usesDefault() {
        synchronized (LOCK) {
            System.clearProperty("env");
            String desc = ReportPortalPropertiesWriter.enrichDescription(null, "API", config);
            assertTrue(desc.startsWith("Automated test execution powered by TestFly"),
                    "Null base should use default, got: " + desc);
        }
    }

    // ── Run type resolution ────────────────────────────────────────────────────

    @Test
    public void resolveRunType_explicitOverridesConfig() {
        synchronized (LOCK) {
            assertEquals(ReportPortalPropertiesWriter.resolveRunType("web", "API"), "API");
        }
    }

    @Test
    public void resolveRunType_configApi() {
        synchronized (LOCK) {
            assertEquals(ReportPortalPropertiesWriter.resolveRunType("api", null), "API");
        }
    }

    @Test
    public void resolveRunType_configWeb() {
        synchronized (LOCK) {
            assertEquals(ReportPortalPropertiesWriter.resolveRunType("web", null), "Web");
        }
    }

    @Test
    public void resolveRunType_auto_defaultsToWeb() {
        synchronized (LOCK) {
            System.clearProperty("testfly.run.type");
            assertEquals(ReportPortalPropertiesWriter.resolveRunType("auto", null), "Web");
        }
    }

    // ── reapplyWithRunType ─────────────────────────────────────────────────────

    @Test
    public void reapplyWithRunType_updatesSystemProperties() {
        synchronized (LOCK) {
            System.clearProperty("env");
            System.clearProperty("testfly.run.type");
            clearRpSystemProperties();
            ReportPortalPropertiesWriter.reapplyWithRunType(config, "API");

            String launch = System.getProperty("rp.launch");
            assertNotNull(launch, "rp.launch should be set after reapply");
            assertTrue(launch.contains("— API"),
                    "System property should contain API run type, got: " + launch);

            String desc = System.getProperty("rp.description");
            assertNotNull(desc, "rp.description should be set after reapply");
            assertTrue(desc.contains("Run type: API"),
                    "System property description should contain API run type, got: " + desc);
        }
    }
}
