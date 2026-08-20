package io.testfly.reporting.reportportal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.reporting.ReportAdapter;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * {@link ReportAdapter} that logs a ReportPortal launch summary after the
 * TestNG suite finishes.
 *
 * <p>The actual result upload is performed by the ReportPortal TestNG agent
 * listener, which reads the runtime properties produced by
 * {@link ReportPortalPropertiesWriter}. This adapter only validates that the
 * configuration is present and prints a human-readable summary containing the
 * ReportPortal dashboard URL.
 *
 * <p>Enable via {@code testfly.yml}:
 * <pre>
 * reporting:
 *   reportportal:
 *     enabled: true
 *     endpoint: http://localhost:8080
 *     apiKey: ${RP_API_KEY}
 *     project: superadmin_personal
 *     launch: "TestFly Launch"
 *     description: "Automated TestFly test execution"
 *     attributes: "env:ci;branch:main"
 * </pre>
 */
public final class ReportPortalReportAdapter implements ReportAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "reportportal";
    }

    @Override
    public void generate(File metricsJson) {
        if (!TestFlyContext.isInitialized()) {
            System.err.println("[TestFly] ReportPortal adapter skipped: TestFly context is not initialized");
            return;
        }

        TestFlyConfig config = TestFlyContext.getConfig();
        TestFlyConfig.Reporting reporting = config.getReporting();
        if (reporting == null || reporting.getReportPortal() == null || !reporting.getReportPortal().isEnabled()) {
            return;
        }

        TestFlyConfig.Reporting.ReportPortal rp = reporting.getReportPortal();
        try {
            ReportPortalPropertiesWriter.validate(rp);
        } catch (IllegalArgumentException e) {
            System.err.println("[TestFly] ReportPortal adapter configuration error: " + e.getMessage());
            return;
        }

        Summary summary = summarize(metricsJson);
        String dashboardUrl = buildDashboardUrl(rp);

        System.out.println("[TestFly] ReportPortal results → " + dashboardUrl);
        System.out.println("[TestFly] ReportPortal launch: " + rp.getLaunch());
        System.out.println("[TestFly] ReportPortal project: " + rp.getProject());
        System.out.println("[TestFly] ReportPortal summary: "
                + summary.passed + " passed, "
                + summary.failed + " failed, "
                + summary.skipped + " skipped, "
                + summary.total + " total");
    }

    private Summary summarize(File metricsJson) {
        Summary summary = new Summary();
        if (metricsJson == null || !metricsJson.exists()) {
            return summary;
        }
        try {
            JsonNode root = MAPPER.readTree(metricsJson);
            JsonNode tests = root.path("tests");
            if (tests.isArray()) {
                for (JsonNode test : tests) {
                    summary.total++;
                    String status = test.path("status").asText("UNKNOWN").toUpperCase(Locale.ROOT);
                    switch (status) {
                        case "PASSED":
                            summary.passed++;
                            break;
                        case "FAILED":
                            summary.failed++;
                            break;
                        case "SKIPPED":
                            summary.skipped++;
                            break;
                        default:
                            summary.other++;
                            break;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[TestFly] ReportPortal adapter could not parse metrics: " + e.getMessage());
        }
        return summary;
    }

    private String buildDashboardUrl(TestFlyConfig.Reporting.ReportPortal rp) {
        String endpoint = rp.getEndpoint().trim();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        String project = rp.getProject().trim();
        return endpoint + "/ui/#" + project + "/launches/all";
    }

    private static final class Summary {
        int total;
        int passed;
        int failed;
        int skipped;
        int other;
    }
}
