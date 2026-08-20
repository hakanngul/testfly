package io.testfly.unit;

import io.testfly.reporting.ReportPaths;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link ReportPaths} — the report output location resolver that
 * lets multiple test engines in one build avoid overwriting each other's report.
 */
public class ReportPathsTest {

    @AfterMethod
    public void clearOverride() {
        System.clearProperty("testfly.reports.dir");
    }

    @Test
    public void baseDir_defaultsToTarget_whenNoOverride() {
        System.clearProperty("testfly.reports.dir");
        assertEquals(ReportPaths.baseDir(), "target");
    }

    @Test
    public void baseDir_honorsOverride() {
        System.setProperty("testfly.reports.dir", "target/junit5");
        assertEquals(ReportPaths.baseDir(), "target/junit5");
    }

    @Test
    public void baseDir_trimsOverride() {
        System.setProperty("testfly.reports.dir", "  target/junit5  ");
        assertEquals(ReportPaths.baseDir(), "target/junit5");
    }

    @Test
    public void baseDir_ignoresBlankOverride() {
        System.setProperty("testfly.reports.dir", "   ");
        assertEquals(ReportPaths.baseDir(), "target");
    }

    @Test
    public void metricsJson_isUnderBaseDir() {
        System.setProperty("testfly.reports.dir", "target/junit5");
        assertEquals(ReportPaths.metricsJson().getPath(),
                "target/junit5/testfly-metrics.json".replace('/', java.io.File.separatorChar));
    }

    @Test
    public void htmlReport_isUnderBaseDir() {
        System.setProperty("testfly.reports.dir", "target/junit5");
        assertEquals(ReportPaths.htmlReport().getPath(),
                "target/junit5/testfly-report.html".replace('/', java.io.File.separatorChar));
    }

    @Test
    public void metricsHistory_isUnderBaseDir() {
        System.setProperty("testfly.reports.dir", "target/junit5");
        assertEquals(ReportPaths.metricsHistoryDir().getPath(),
                "target/junit5/metrics-history".replace('/', java.io.File.separatorChar));
    }

    @Test
    public void defaults_matchHistoricalTargetPaths() {
        System.clearProperty("testfly.reports.dir");
        assertEquals(ReportPaths.metricsJson().getPath(),
                "target/testfly-metrics.json".replace('/', java.io.File.separatorChar));
        assertEquals(ReportPaths.htmlReport().getPath(),
                "target/testfly-report.html".replace('/', java.io.File.separatorChar));
    }
}
