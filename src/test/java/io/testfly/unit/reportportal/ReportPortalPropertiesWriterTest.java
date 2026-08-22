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
 */
public class ReportPortalPropertiesWriterTest {

    private TestFlyConfig config;

    @BeforeMethod
    public void setup() {
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

        clearRpSystemProperties();
    }

    @AfterMethod
    public void cleanup() {
        clearRpSystemProperties();
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
        Properties props = ReportPortalPropertiesWriter.toProperties(config);

        assertEquals(props.getProperty("rp.endpoint"), "http://localhost:8080");
        assertEquals(props.getProperty("rp.api.key"), "test-api-key");
        assertEquals(props.getProperty("rp.project"), "superadmin_personal");
        // Launch name is enriched: "TestFly Launch — Web | <timestamp>"
        assertTrue(props.getProperty("rp.launch").startsWith("TestFly Launch — "),
                "Launch should start with configured name + enrichment separator, got: "
                        + props.getProperty("rp.launch"));
        // Description is enriched: starts with base + runtime context
        assertTrue(props.getProperty("rp.description").startsWith("Automated run"),
                "Description should start with configured base, got: "
                        + props.getProperty("rp.description"));
        assertTrue(props.getProperty("rp.description").contains("Run type:"),
                "Description should contain run type info");
        assertEquals(props.getProperty("rp.attributes"), "env:ci;branch:main");
    }

    @Test
    public void toProperties_omitsBlankAttributes() {
        config.getReporting().getReportPortal().setAttributes("   ");

        Properties props = ReportPortalPropertiesWriter.toProperties(config);

        assertFalse(props.containsKey("rp.attributes"),
                "Blank attributes should be omitted");
        // Description is always enriched (never blank), so it should be present
        assertTrue(props.containsKey("rp.description"),
                "Description should always be present due to enrichment");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".*endpoint is required.*")
    public void toProperties_requiresEndpoint() {
        config.getReporting().getReportPortal().setEndpoint(null);
        ReportPortalPropertiesWriter.toProperties(config);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = ".*apiKey is required.*")
    public void toProperties_requiresApiKey() {
        config.getReporting().getReportPortal().setApiKey("");
        ReportPortalPropertiesWriter.toProperties(config);
    }

    @Test
    public void applyAsSystemProperties_setsProperties() {
        ReportPortalPropertiesWriter.applyAsSystemProperties(config);

        assertEquals(System.getProperty("rp.endpoint"), "http://localhost:8080");
        assertEquals(System.getProperty("rp.api.key"), "test-api-key");
        assertEquals(System.getProperty("rp.project"), "superadmin_personal");
    }

    @Test
    public void writeToFile_createsPropertiesFile() throws IOException {
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

    @Test
    public void toMaskedString_masksApiKey() {
        config.getReporting().getReportPortal().setApiKey("my-secret-key");
        String masked = ReportPortalPropertiesWriter.toMaskedString(config);

        assertTrue(masked.contains("rp.api.key"));
        assertFalse(masked.contains("my-secret-key"));
    }

    // ── Launch name enrichment ─────────────────────────────────────────────────

    @Test
    public void enrichLaunchName_containsBaseNameAndRunType() {
        String launch = ReportPortalPropertiesWriter.enrichLaunchName("My Launch", "API");
        assertTrue(launch.startsWith("My Launch — API"),
                "Launch should contain base name + run type, got: " + launch);
    }

    @Test
    public void enrichLaunchName_containsTimestamp() {
        String launch = ReportPortalPropertiesWriter.enrichLaunchName("Test", "Web");
        // Timestamp format: yyyy-MM-dd HH:mm → contains a pipe separator
        assertTrue(launch.contains(" | "),
                "Launch should contain timestamp separator, got: " + launch);
    }

    @Test
    public void enrichLaunchName_withEnv_includesEnvLabel() {
        System.setProperty("env", "staging");
        try {
            String launch = ReportPortalPropertiesWriter.enrichLaunchName("Demo", "API");
            assertTrue(launch.contains("staging"),
                    "Launch should contain env label, got: " + launch);
        } finally {
            System.clearProperty("env");
        }
    }

    @Test
    public void enrichLaunchName_nullBaseName_usesDefault() {
        String launch = ReportPortalPropertiesWriter.enrichLaunchName(null, "API");
        assertTrue(launch.startsWith("TestFly Launch — API"),
                "Null base name should use default, got: " + launch);
    }

    // ── Description enrichment ─────────────────────────────────────────────────

    @Test
    public void enrichDescription_containsRunType() {
        String desc = ReportPortalPropertiesWriter.enrichDescription("Base", "API", config);
        assertTrue(desc.contains("Run type: API"),
                "Description should contain run type, got: " + desc);
    }

    @Test
    public void enrichDescription_containsTriggeredBy() {
        String desc = ReportPortalPropertiesWriter.enrichDescription("Base", "Web", config);
        assertTrue(desc.contains("Triggered by:"),
                "Description should contain triggered-by info, got: " + desc);
    }

    @Test
    public void enrichDescription_nullBase_usesDefault() {
        String desc = ReportPortalPropertiesWriter.enrichDescription(null, "API", config);
        assertTrue(desc.startsWith("Automated TestFly test execution"),
                "Null base should use default, got: " + desc);
    }

    // ── Run type resolution ────────────────────────────────────────────────────

    @Test
    public void resolveRunType_explicitOverridesConfig() {
        assertEquals(ReportPortalPropertiesWriter.resolveRunType("web", "API"), "API");
    }

    @Test
    public void resolveRunType_configApi() {
        assertEquals(ReportPortalPropertiesWriter.resolveRunType("api", null), "API");
    }

    @Test
    public void resolveRunType_configWeb() {
        assertEquals(ReportPortalPropertiesWriter.resolveRunType("web", null), "Web");
    }

    @Test
    public void resolveRunType_auto_defaultsToWeb() {
        System.clearProperty("testfly.run.type");
        assertEquals(ReportPortalPropertiesWriter.resolveRunType("auto", null), "Web");
    }

    // ── reapplyWithRunType ─────────────────────────────────────────────────────

    @Test
    public void reapplyWithRunType_updatesSystemProperties() {
        ReportPortalPropertiesWriter.reapplyWithRunType(config, "API");

        String launch = System.getProperty("rp.launch");
        assertTrue(launch.contains("— API"),
                "System property should contain API run type, got: " + launch);

        String desc = System.getProperty("rp.description");
        assertTrue(desc.contains("Run type: API"),
                "System property description should contain API run type, got: " + desc);
    }
}
