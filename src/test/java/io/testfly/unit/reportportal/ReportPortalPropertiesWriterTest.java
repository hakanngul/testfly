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
        assertEquals(props.getProperty("rp.launch"), "TestFly Launch");
        assertEquals(props.getProperty("rp.description"), "Automated run");
        assertEquals(props.getProperty("rp.attributes"), "env:ci;branch:main");
    }

    @Test
    public void toProperties_omitsBlankValues() {
        config.getReporting().getReportPortal().setAttributes("   ");
        config.getReporting().getReportPortal().setDescription(null);

        Properties props = ReportPortalPropertiesWriter.toProperties(config);

        assertFalse(props.containsKey("rp.attributes"));
        assertFalse(props.containsKey("rp.description"));
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
}
