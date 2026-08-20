package io.testfly.reporting;

import io.testfly.api.TestFlyApi;
import java.io.File;

/**
 * Extension point for custom report generation.
 *
 * <p>After each suite finishes, TestFly calls {@link #generate(File)} on
 * every registered adapter. The built-in HTML adapter is always included.
 *
 * <p>Register additional adapters via Java SPI:
 * <pre>META-INF/services/io.testfly.reporting.ReportAdapter</pre>
 * or programmatically:
 * <pre>ReportAdapterRegistry.register(new MySlackAdapter());</pre>
 *
 * <p>Example — posting a summary to Slack:
 * <pre>
 * public class SlackReportAdapter implements ReportAdapter {
 *     public String getName() { return "slack"; }
 *     public void generate(File metricsJson) {
 *         // parse metricsJson, build message, POST to webhook
 *     }
 * }
 * </pre>
 */
@TestFlyApi(since = "0.3.0")
public interface ReportAdapter {

    /** Unique human-readable name used in log messages. */
    String getName();

    /**
     * Generates a report from the supplied metrics JSON file.
     *
     * @param metricsJson the {@code target/testfly-metrics.json} file
     *                    written by {@link io.testfly.metrics.ExecutionMetrics}
     */
    void generate(File metricsJson);
}
