package io.testfly.unit.loadtest;

import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.internal.GatlingResultsParser;
import io.testfly.loadtest.internal.JdkLoadEngine;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.testng.Assert.*;

/**
 * Tests {@link GatlingResultsParser} with synthetic Gatling output files.
 */
@Test(singleThreaded = true)
public class GatlingResultsParserTest {

    @Test
    public void testParseMissingDirectory() {
        LoadTestMetrics metrics = GatlingResultsParser.parse(
                "target/nonexistent-gatling-results", "Test", 10);

        assertNotNull(metrics);
        assertEquals(metrics.scenarioName(), "Test");
        assertEquals(metrics.totalRequests(), 0);
        assertEquals(metrics.engine(), "none");
    }

    @Test
    public void testParseEmptyDirectory() throws IOException {
        Path tempDir = Files.createTempDirectory("gatling-empty");
        try {
            LoadTestMetrics metrics = GatlingResultsParser.parse(
                    tempDir.toString(), "Empty", 5);

            assertEquals(metrics.totalRequests(), 0);
        } finally {
            deleteRecursive(tempDir.toFile());
        }
    }

    @Test
    public void testSimulationLogIsNeverParsed() throws IOException {
        // simulation.log is a binary, internal format since Gatling 3.13: no text fallback, no fabricated data
        Path tempDir = Files.createTempDirectory("gatling-sim");
        Path runDir = tempDir.resolve("mysimulation-20260907");
        Files.createDirectories(runDir);
        Files.writeString(runDir.resolve("simulation.log"),
                "REQUEST\tmysimulation\t1\t1725724800100\t1725724800250\tOK\t200\n");
        try {
            LoadTestMetrics metrics = GatlingResultsParser.parse(tempDir.toString(), "SimLog Test", 2);

            assertEquals(metrics.totalRequests(), 0);
            assertEquals(metrics.engine(), "none");
            assertTrue(metrics.statusCodes().isEmpty());
        } finally {
            deleteRecursive(tempDir.toFile());
        }
    }

    @Test
    public void testParseStatsJson() throws IOException {
        Path tempDir = Files.createTempDirectory("gatling-stats");
        Path runDir = tempDir.resolve("mysimulation-20260907");
        Files.createDirectories(runDir);

        // Write a synthetic stats.json (Gatling Highcharts format)
        String statsJson = """
                {
                  "stats": {
                    "numberOfRequests": {"total": 1000, "ko": 10},
                    "minResponseTime": {"total": 15},
                    "maxResponseTime": {"total": 450},
                    "meanResponseTime": {"total": 120},
                    "percentiles1": {"total": 85},
                    "percentiles2": {"total": 130},
                    "percentiles3": {"total": 210},
                    "percentiles4": {"total": 380},
                    "meanRequestsPerSecond": {"total": 16.67},
                    "duration": 60000
                  },
                  "contents": {
                    "GET /api/health": {
                      "stats": {
                        "numberOfRequests": {"total": 600, "ko": 5},
                        "percentiles3": {"total": 180}
                      }
                    },
                    "POST /api/orders": {
                      "stats": {
                        "numberOfRequests": {"total": 400, "ko": 5},
                        "percentiles3": {"total": 250}
                      }
                    }
                  }
                }
                """;
        Files.writeString(runDir.resolve("stats.json"), statsJson);

        LoadTestMetrics metrics = GatlingResultsParser.parse(
                tempDir.toString(), "Stats Test", 50);

        assertEquals(metrics.scenarioName(), "Stats Test");
        assertEquals(metrics.totalRequests(), 1000);
        assertEquals(metrics.failedRequests(), 10);
        assertEquals(metrics.successfulRequests(), 990);
        assertEquals(metrics.engine(), "gatling");
        assertEquals(metrics.users(), 50);
        assertEquals(metrics.p50LatencyMs(), 85.0, 0.01);
        assertEquals(metrics.p95LatencyMs(), 210.0, 0.01);
        assertEquals(metrics.p99LatencyMs(), 380.0, 0.01);
        assertEquals(metrics.meanLatencyMs(), 120.0, 0.01);
        assertEquals(metrics.minLatencyMs(), 15.0, 0.01);
        assertEquals(metrics.maxLatencyMs(), 450.0, 0.01);
        assertEquals(metrics.throughputRps(), 16.67, 0.01);
        assertEquals(metrics.durationMs(), 60000);
        assertEquals(metrics.errorRate(), 0.01, 0.001);
        assertEquals(metrics.p75LatencyMs(), 130.0, 0.01, "percentiles2 is the pinned p75 slot");
        assertTrue(Double.isNaN(metrics.p90LatencyMs()), "Gatling does not measure p90");
        // no status sidecar → not measured, never fabricated 200/500
        assertTrue(metrics.statusCodes().isEmpty(), "statusCodes=" + metrics.statusCodes());
        assertFalse(metrics.statusCodesMeasured());

        // Per-step metrics
        assertEquals(metrics.steps().size(), 2);
        assertTrue(metrics.steps().containsKey("GET /api/health"));
        assertEquals(metrics.steps().get("GET /api/health").totalRequests(), 600);
        assertEquals(metrics.steps().get("POST /api/orders").p95LatencyMs(), 250.0, 0.01);

        deleteRecursive(tempDir.toFile());
    }

    @Test
    public void testParseStatsJs() throws IOException {
        Path tempDir = Files.createTempDirectory("gatling-statsjs");
        Path runDir = tempDir.resolve("mysimulation-20260907");
        Path jsDir = runDir.resolve("js");
        Files.createDirectories(jsDir);

        String statsJs = """
                var stats = {
                    type: "GROUP",
                    name: "All Requests",
                    stats: {
                        "name": "All Requests",
                        "numberOfRequests": {
                            "total": "500",
                            "ok": "490",
                            "ko": "10"
                        },
                        "minResponseTime": {
                            "total": "20",
                            "ok": "20",
                            "ko": "50"
                        },
                        "maxResponseTime": {
                            "total": "800",
                            "ok": "600",
                            "ko": "800"
                        },
                        "meanResponseTime": {
                            "total": "150",
                            "ok": "140",
                            "ko": "200"
                        },
                        "percentiles1": {
                            "total": "90"
                        },
                        "percentiles2": {
                            "total": "120"
                        },
                        "percentiles3": {
                            "total": "250"
                        },
                        "percentiles4": {
                            "total": "400"
                        },
                        "meanNumberOfRequestsPerSecond": {
                            "total": "25.0"
                        }
                    },
                    contents: {
                        "req_1": {
                            type: "REQUEST",
                            name: "/items",
                            stats: {
                                "numberOfRequests": {
                                    "total": "500",
                                    "ok": "490",
                                    "ko": "10"
                                },
                                "percentiles3": {
                                    "total": "250"
                                }
                            }
                        }
                    }
                }

                function fillStats(stat){
                    $("#numberOfRequests").append(stat.numberOfRequests.total);
                }
                """;
        Files.writeString(jsDir.resolve("stats.js"), statsJs);

        LoadTestMetrics metrics = GatlingResultsParser.parse(
                tempDir.toString(), "StatsJs Test", 10);

        assertEquals(metrics.scenarioName(), "StatsJs Test");
        assertEquals(metrics.totalRequests(), 500);
        assertEquals(metrics.failedRequests(), 10);
        assertEquals(metrics.successfulRequests(), 490);
        assertEquals(metrics.engine(), "gatling");
        assertEquals(metrics.users(), 10);
        assertEquals(metrics.p50LatencyMs(), 90.0, 0.01);
        assertEquals(metrics.p95LatencyMs(), 250.0, 0.01);
        assertEquals(metrics.p99LatencyMs(), 400.0, 0.01);
        assertEquals(metrics.meanLatencyMs(), 150.0, 0.01);
        assertEquals(metrics.throughputRps(), 25.0, 0.01);
        assertEquals(metrics.errorRate(), 0.02, 0.001);
        assertEquals(metrics.p75LatencyMs(), 120.0, 0.01);
        assertTrue(Double.isNaN(metrics.p90LatencyMs()));

        assertTrue(metrics.steps().containsKey("/items"));
        assertEquals(metrics.steps().get("/items").totalRequests(), 500);
        assertEquals(metrics.steps().get("/items").p95LatencyMs(), 250.0, 0.01);

        deleteRecursive(tempDir.toFile());
    }

    // ── Status sidecar (testfly-status.json) and "-" percentiles ─────────

    /** Gatling 3.13 stats.js shape; numbers are strings, absent values are "-". */
    private static Path writeStatsJs(Path base, int total, int ko, String p1, String p2, String p3, String p4)
            throws IOException {
        Path jsDir = base.resolve("rel201-20261025").resolve("js");
        Files.createDirectories(jsDir);
        String statsJs = """
                var stats = {
                    type: "GROUP",
                name: "All Requests",
                path: "",
                pathFormatted: "group_missing-name--1146707516",
                stats: {
                    "name": "All Requests",
                    "numberOfRequests": {"total": "%d", "ok": "%d", "ko": "%d"},
                    "minResponseTime": {"total": "1", "ok": "1", "ko": "-"},
                    "maxResponseTime": {"total": "40", "ok": "40", "ko": "-"},
                    "meanResponseTime": {"total": "5", "ok": "5", "ko": "-"},
                    "standardDeviation": {"total": "3", "ok": "3", "ko": "-"},
                    "percentiles1": {"total": "%s", "ok": "%s", "ko": "-"},
                    "percentiles2": {"total": "%s", "ok": "%s", "ko": "-"},
                    "percentiles3": {"total": "%s", "ok": "%s", "ko": "-"},
                    "percentiles4": {"total": "%s", "ok": "%s", "ko": "-"},
                    "meanNumberOfRequestsPerSecond": {"total": "10", "ok": "10", "ko": "-"}
                },
                contents: {
                "req_create-1": {
                        type: "REQUEST",
                        name: "create",
                path: "create",
                pathFormatted: "req_create-1",
                stats: {
                    "name": "create",
                    "numberOfRequests": {"total": "%d", "ok": "%d", "ko": "%d"},
                    "percentiles3": {"total": "%s", "ok": "%s", "ko": "-"}
                }
                    }
                }
                }

                function fillStats(stat){
                    $("#numberOfRequests").append(stat.numberOfRequests.total);
                }
                """.formatted(total, total - ko, ko, p1, p1, p2, p2, p3, p3, p4, p4,
                total, total - ko, ko, p3, p3);
        Files.writeString(jsDir.resolve("stats.js"), statsJs);
        return base;
    }

    @Test
    public void testSidecarStatusCodesReplaceFabrication() throws IOException {
        Path base = writeStatsJs(Files.createTempDirectory("gatling-sidecar"), 12, 0, "3", "4", "9", "20");
        try {
            Files.writeString(base.resolve(GatlingResultsParser.STATUS_SIDECAR_FILE), "{\"create\":{\"201\":12}}");

            LoadTestMetrics m = GatlingResultsParser.parse(base.toString(), "Rel201", 2);

            assertEquals(m.totalRequests(), 12);
            assertEquals(m.statusCodes(), Map.of(201, 12L), "real 201s, not a fabricated 200");
            assertTrue(m.statusCodesMeasured());
            assertEquals(m.p50LatencyMs(), 3.0);
            assertEquals(m.p75LatencyMs(), 4.0);
            assertEquals(m.p95LatencyMs(), 9.0);
            assertEquals(m.p99LatencyMs(), 20.0);
            assertTrue(Double.isNaN(m.p90LatencyMs()));
        } finally {
            deleteRecursive(base.toFile());
        }
    }

    @Test
    public void testSidecarSumsRequestNamesAndReportsNoResponseAsMinusOne() throws IOException {
        Path base = writeStatsJs(Files.createTempDirectory("gatling-sidecar-ko"), 10, 6, "3", "4", "9", "20");
        try {
            // 4 responses (200 with a failed check + 500), 6 requests without any response
            Path sidecar = base.resolve("custom-status.json");
            Files.writeString(sidecar, "{\"a\":{\"200\":1,\"500\":1},\"b\":{\"200\":2}}");

            LoadTestMetrics m = GatlingResultsParser.parse(base.toString(), "Mixed", 2, sidecar);

            assertEquals(m.statusCodes(), Map.of(200, 3L, 500, 1L, JdkLoadEngine.TRANSPORT_ERROR_STATUS, 6L));
            long sum = m.statusCodes().values().stream().mapToLong(Long::longValue).sum();
            assertEquals(sum, (long) m.totalRequests(), "status counts must add up to the request total");
        } finally {
            deleteRecursive(base.toFile());
        }
    }

    @Test
    public void testSidecarSumAboveStatsTotalKeepsRealCountsWithoutMinusOne() throws IOException {
        Path base = writeStatsJs(Files.createTempDirectory("gatling-sidecar-over"), 4, 0, "3", "4", "9", "20");
        try {
            Files.writeString(base.resolve(GatlingResultsParser.STATUS_SIDECAR_FILE),
                    "{\"a\":{\"200\":4,\"302\":2}}");

            LoadTestMetrics m = GatlingResultsParser.parse(base.toString(), "Over", 1);

            // a WARN with both numbers is logged; counts are not silently dropped or negated
            assertEquals(m.statusCodes(), Map.of(200, 4L, 302, 2L));
            assertFalse(m.statusCodes().containsKey(JdkLoadEngine.TRANSPORT_ERROR_STATUS));
        } finally {
            deleteRecursive(base.toFile());
        }
    }

    @Test
    public void testAllRequestsWithoutResponse() throws IOException {
        Path base = writeStatsJs(Files.createTempDirectory("gatling-refused"), 5, 5, "1", "1", "2", "2");
        try {
            Files.writeString(base.resolve(GatlingResultsParser.STATUS_SIDECAR_FILE), "{}");

            LoadTestMetrics m = GatlingResultsParser.parse(base.toString(), "Refused", 1);

            assertEquals(m.statusCodes(), Map.of(JdkLoadEngine.TRANSPORT_ERROR_STATUS, 5L));
        } finally {
            deleteRecursive(base.toFile());
        }
    }

    @Test
    public void testMissingOrUnreadableSidecarMeansNotMeasured() throws IOException {
        Path base = writeStatsJs(Files.createTempDirectory("gatling-nosidecar"), 7, 1, "3", "4", "9", "20");
        try {
            LoadTestMetrics missing = GatlingResultsParser.parse(base.toString(), "NoSidecar", 1);
            assertEquals(missing.totalRequests(), 7);
            assertTrue(missing.statusCodes().isEmpty(), "statusCodes=" + missing.statusCodes());
            assertFalse(missing.statusCodesMeasured());

            LoadTestMetrics nullPath = GatlingResultsParser.parse(base.toString(), "NullSidecar", 1, null);
            assertTrue(nullPath.statusCodes().isEmpty());

            Files.writeString(base.resolve(GatlingResultsParser.STATUS_SIDECAR_FILE), "not json {");
            LoadTestMetrics corrupt = GatlingResultsParser.parse(base.toString(), "Corrupt", 1);
            assertTrue(corrupt.statusCodes().isEmpty(), "statusCodes=" + corrupt.statusCodes());
        } finally {
            deleteRecursive(base.toFile());
        }
    }

    @Test
    public void testDashPercentilesAreNaNNotZero() throws IOException {
        Path base = writeStatsJs(Files.createTempDirectory("gatling-dash"), 3, 0, "-", "-", "-", "-");
        try {
            LoadTestMetrics m = GatlingResultsParser.parse(base.toString(), "Dash", 1);

            assertTrue(Double.isNaN(m.p50LatencyMs()), "p50=" + m.p50LatencyMs());
            assertTrue(Double.isNaN(m.p75LatencyMs()));
            assertTrue(Double.isNaN(m.p95LatencyMs()));
            assertTrue(Double.isNaN(m.p99LatencyMs()));
            assertTrue(Double.isNaN(m.step("create").p95LatencyMs()));
            assertEquals(m.meanLatencyMs(), 5.0);
        } finally {
            deleteRecursive(base.toFile());
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        file.delete();
    }
}
