package io.testfly.unit.loadtest;

import io.testfly.loadtest.LoadTestMetrics;
import io.testfly.loadtest.internal.GatlingResultsParser;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    public void testParseSimulationLog() throws IOException {
        Path tempDir = Files.createTempDirectory("gatling-sim");
        Path runDir = tempDir.resolve("mysimulation-20260907");
        Files.createDirectories(runDir);

        // Write a synthetic simulation.log
        String log = String.join("\n",
                "RUN\t1\t1725724800000\t1725724860000\tmysimulation\tTestFly",
                "USER\t1\t1\t1725724800000\t1725724800100\tSTART",
                "REQUEST\tmysimulation\t1\t1725724800100\t1725724800250\tOK\t200",
                "REQUEST\tmysimulation\t1\t1725724800300\t1725724800500\tOK\t200",
                "REQUEST\tmysimulation\t1\t1725724800600\t1725724800900\tKO\t500",
                "REQUEST\tmysimulation\t2\t1725724801000\t1725724801150\tOK\t200",
                "REQUEST\tmysimulation\t2\t1725724801200\t1725724801400\tOK\t201",
                "USER\t1\t1\t1725724860000\t1725724860000\tEND",
                "USER\t2\t2\t1725724860000\t1725724860000\tEND"
        );
        Files.writeString(runDir.resolve("simulation.log"), log);

        LoadTestMetrics metrics = GatlingResultsParser.parse(
                tempDir.toString(), "SimLog Test", 2);

        assertEquals(metrics.scenarioName(), "SimLog Test");
        assertEquals(metrics.totalRequests(), 5);
        assertEquals(metrics.failedRequests(), 1);
        assertEquals(metrics.successfulRequests(), 4);
        assertEquals(metrics.engine(), "gatling");
        assertEquals(metrics.users(), 2);
        assertTrue(metrics.errorRate() > 0.19 && metrics.errorRate() < 0.21,
                "Error rate should be ~0.2 (1/5)");
        assertTrue(metrics.p50LatencyMs() > 0);
        assertTrue(metrics.statusCodes().containsKey(200));
        assertTrue(metrics.statusCodes().containsKey(500));

        deleteRecursive(tempDir.toFile());
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

        // Also write a minimal simulation.log for status code parsing
        Files.writeString(runDir.resolve("simulation.log"),
                "REQUEST\tsim\t1\t100\t200\tOK\t200\nREQUEST\tsim\t1\t300\t500\tKO\t500\n");

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

        assertTrue(metrics.steps().containsKey("/items"));
        assertEquals(metrics.steps().get("/items").totalRequests(), 500);
        assertEquals(metrics.steps().get("/items").p95LatencyMs(), 250.0, 0.01);

        deleteRecursive(tempDir.toFile());
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
