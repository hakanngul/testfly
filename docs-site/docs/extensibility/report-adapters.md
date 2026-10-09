---
description: "Turn Selenium test results into any format with ReportAdapter: Slack messages, Allure, email summaries, or custom dashboards from the metrics JSON."
id: report-adapters
title: Report Adapters
sidebar_position: 4
---

# Report Adapters

`ReportAdapter` lets you generate any output format from the metrics JSON — Slack messages, Allure input, email summaries, custom dashboards. The built-in HTML adapter always runs; your adapters are appended after it.

---

## Create a report adapter

The example below parses the metrics JSON and posts a one-line summary to Slack.

:::note `SlackClient` is hypothetical
`SlackClient` stands in for **your own** Slack integration (an incoming-webhook wrapper, the official Slack SDK, etc.). It is **not** part of TestFly and is not shipped with the framework. Jackson (`ObjectMapper`) is already on the classpath as a TestFly dependency.
:::

```java
import io.testfly.reporting.ReportAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.slack.SlackClient; // hypothetical — your own Slack integration
import java.io.File;
import java.io.IOException;

public class SlackReportAdapter implements ReportAdapter {

    private final SlackClient slack = new SlackClient(System.getenv("SLACK_WEBHOOK_URL"));

    @Override
    public String getName() {
        return "slack";
    }

    @Override
    public void generate(File metricsJson) {
        // ReportAdapter.generate(File) does not declare checked exceptions,
        // so IOException from Jackson must be handled here.
        final JsonNode root;
        try {
            root = new ObjectMapper().readTree(metricsJson);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read TestFly metrics: " + metricsJson, e);
        }

        int total    = root.path("totalTests").asInt();
        int passed   = root.path("passedTests").asInt();
        int failed   = root.path("failedTests").asInt();
        double rate  = root.path("passRate").asDouble();

        String message = String.format(
            "Test run complete — %d/%d passed (%.1f%%)%s",
            passed, total, rate,
            failed > 0 ? " :red_circle: " + failed + " failures" : " :white_check_mark:"
        );

        slack.post(message);
    }
}
```

The summary fields (`totalTests`, `passedTests`, `failedTests`, `passRate`) match the keys written to the metrics JSON. An exception thrown from `generate()` is caught and logged by the registry; it does not fail the build or stop other adapters.

---

## Register via Java SPI (auto-discovery)

```
src/main/resources/META-INF/services/io.testfly.reporting.ReportAdapter
```

Contents:

```
com.example.reporting.SlackReportAdapter
```

---

## Register programmatically

```java
import io.testfly.reporting.ReportAdapterRegistry;

ReportAdapterRegistry.register(new SlackReportAdapter());
```

---

## Metrics JSON structure

The `metricsJson` file passed to `generate()` (`target/testfly-metrics.json` by default — see [Output location](#report-paths)) contains:

```json
{
  "totalTests": 25,
  "passedTests": 23,
  "failedTests": 1,
  "skippedTests": 1,
  "passRate": 92.0,
  "flakyTests": 2,
  "recoveredTests": 1,
  "totalTimeMs": 45231,
  "tests": [
    {
      "testId": "LoginTest#validLogin",
      "testClassName": "LoginTest",
      "status": "PASSED",
      "startTime": 1710000000000,
      "endTime": 1710000002341,
      "totalMs": 2341,
      "retryCount": 0,
      "errorMessage": null,
      "stackTrace": null,
      "steps": [
        { "name": "Open login page", "offsetMs": 0, "status": "INFO", "screenshotBase64": null }
      ]
    }
  ]
}
```

---

## Adapter execution order

At framework bootstrap the registry is populated in this order:

1. Built-in `HtmlReportAdapter`
2. SPI-discovered adapters (in `ServiceLoader` discovery order)
3. Programmatically registered adapters — e.g. from a plugin's `onLoad`, plus opt-in built-ins such as the Allure adapter

At suite finish, adapters run **serially, in registration order**, on a snapshot of the registry — never in parallel, because adapters may read or write the same output files. Every adapter receives the same `metricsJson` `File`, and the HTML adapter has already written its report by the time later adapters run. Treat shared files as read-only and write your own output to a separate file name. An exception thrown by one adapter is logged (`[TestFly] ReportAdapter [name] failed: ...`) and the remaining adapters still run.

:::caution Register after bootstrap
`register(...)` simply appends to the list. If you call it before the framework bootstraps, your adapter is placed **before** the HTML adapter. Register from a `TestFlyPlugin.onLoad` (or later) to keep the order above.
:::

## Output location (`ReportPaths`) {#report-paths}

`ReportPaths` resolves the report base directory:

- The `testfly.reports.dir` system property, if set and not blank (e.g. `-Dtestfly.reports.dir=target/junit5`)
- Otherwise `build` for a Gradle layout (`build/` exists and `target/` does not)
- Otherwise `target`

The metrics file passed to `generate()` is `<baseDir>/testfly-metrics.json`, and the HTML report is `<baseDir>/testfly-report.html`. Point each engine at a different `testfly.reports.dir` (for example TestNG via Surefire and JUnit 5 via Failsafe) so they do not overwrite each other's reports. Resolve your own output paths from `metricsJson.getParentFile()` rather than hard-coding `target/`.

---

## Allure integration

TestFly includes an Allure result adapter. Enable `reporting.allure.enabled: true` and follow the [Allure guide](/docs/reporting/allure); no `allure-testng` dependency is required for that adapter.

If you separately use Allure's TestNG listener, configure that external integration yourself. Avoid writing two integrations into the same results directory or counting the same tests twice.
