package io.testfly.unit;

import io.testfly.reporting.ReportPaths;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link ReportPaths} — the report output location resolver that
 * lets multiple test engines in one build avoid overwriting each other's report.
 * Thread-safe for parallel=methods via singleThreaded + global lock.
 */
@Test(singleThreaded = true)
public class ReportPathsTest {

    private static final Object LOCK = ReportPaths.class;

    @BeforeMethod
    public void setUp() {
        synchronized (LOCK) {
            System.clearProperty("testfly.reports.dir");
        }
    }

    @AfterMethod
    public void clearOverride() {
        synchronized (LOCK) {
            System.clearProperty("testfly.reports.dir");
        }
    }

    @Test
    public void baseDir_defaultsToTarget_whenNoOverride() {
        synchronized (LOCK) {
            System.clearProperty("testfly.reports.dir");
            assertEquals(ReportPaths.baseDir(), "target");
        }
    }

    @Test
    public void baseDir_honorsOverride() {
        synchronized (LOCK) {
            System.setProperty("testfly.reports.dir", "target/junit5");
            try {
                assertEquals(ReportPaths.baseDir(), "target/junit5");
            } finally {
                System.clearProperty("testfly.reports.dir");
            }
        }
    }

    @Test
    public void baseDir_trimsOverride() {
        synchronized (LOCK) {
            System.setProperty("testfly.reports.dir", "  target/junit5  ");
            try {
                assertEquals(ReportPaths.baseDir(), "target/junit5");
            } finally {
                System.clearProperty("testfly.reports.dir");
            }
        }
    }

    @Test
    public void baseDir_ignoresBlankOverride() {
        synchronized (LOCK) {
            System.setProperty("testfly.reports.dir", "   ");
            try {
                assertEquals(ReportPaths.baseDir(), "target");
            } finally {
                System.clearProperty("testfly.reports.dir");
            }
        }
    }

    @Test
    public void metricsJson_isUnderBaseDir() {
        synchronized (LOCK) {
            System.setProperty("testfly.reports.dir", "target/junit5");
            try {
                assertEquals(ReportPaths.metricsJson().getPath(),
                        "target/junit5/testfly-metrics.json".replace('/', java.io.File.separatorChar));
            } finally {
                System.clearProperty("testfly.reports.dir");
            }
        }
    }

    @Test
    public void htmlReport_isUnderBaseDir() {
        synchronized (LOCK) {
            System.setProperty("testfly.reports.dir", "target/junit5");
            try {
                assertEquals(ReportPaths.htmlReport().getPath(),
                        "target/junit5/testfly-report.html".replace('/', java.io.File.separatorChar));
            } finally {
                System.clearProperty("testfly.reports.dir");
            }
        }
    }

    @Test
    public void metricsHistory_isUnderBaseDir() {
        synchronized (LOCK) {
            System.setProperty("testfly.reports.dir", "target/junit5");
            try {
                assertEquals(ReportPaths.metricsHistoryDir().getPath(),
                        "target/junit5/metrics-history".replace('/', java.io.File.separatorChar));
            } finally {
                System.clearProperty("testfly.reports.dir");
            }
        }
    }

    @Test
    public void defaults_matchHistoricalTargetPaths() {
        synchronized (LOCK) {
            System.clearProperty("testfly.reports.dir");
            assertEquals(ReportPaths.metricsJson().getPath(),
                    "target/testfly-metrics.json".replace('/', java.io.File.separatorChar));
            assertEquals(ReportPaths.htmlReport().getPath(),
                    "target/testfly-report.html".replace('/', java.io.File.separatorChar));
        }
    }
}
