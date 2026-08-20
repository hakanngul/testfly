---
description: "Send TestNG test results to ReportPortal with zero boilerplate."
id: report-portal
title: ReportPortal Integration
sidebar_position: 3
---

# ReportPortal Integration

TestFly can stream TestNG results to [ReportPortal](https://reportportal.io/) in real time. The integration uses the official ReportPortal TestNG agent and is fully opt-in via `testfly.yml`.

## What gets reported

* Every TestNG test method is reported as a test item in ReportPortal.
* Launch name, description, and attributes are taken from `testfly.yml`.
* Test statuses are mapped automatically by the ReportPortal agent.
* After the suite finishes, TestFly prints the ReportPortal dashboard URL and a summary to the console.

## Prerequisites

The ReportPortal TestNG agent is an **optional** dependency in TestFly. Add it explicitly to your project:

```xml
<dependency>
    <groupId>com.epam.reportportal</groupId>
    <artifactId>agent-java-testng</artifactId>
    <version>5.6.8</version>
</dependency>
```

Or with Gradle:

```groovy
testImplementation 'com.epam.reportportal:agent-java-testng:5.6.8'
```

## Enable ReportPortal

Add the `reporting.reportportal` block to `testfly.yml`:

```yaml
reporting:
  reportportal:
    enabled: true
    endpoint: http://localhost:8080
    apiKey: ${RP_API_KEY}
    project: superadmin_personal
    launch: "TestFly Regression"
    description: "Automated TestFly test execution"
    attributes: "env:ci;branch:main"
```

### Configuration options

| Option | Required | Default | Description |
|--------|----------|---------|-------------|
| `enabled` | no | `false` | Toggle ReportPortal reporting. |
| `endpoint` | yes | — | ReportPortal server URL. |
| `apiKey` | yes | — | ReportPortal API key. Use `${RP_API_KEY}` and inject via environment. |
| `project` | yes | `superadmin_personal` | ReportPortal project name. |
| `launch` | yes | `TestFly Launch` | Launch name. |
| `description` | no | `Automated TestFly test execution` | Launch description. |
| `attributes` | no | — | Semicolon-separated key:value pairs, e.g. `env:ci;branch:main`. |

## How it works

1. `FrameworkBootstrap.initialize()` reads the ReportPortal config.
2. The config is converted to ReportPortal system properties (`rp.endpoint`, `rp.api.key`, etc.).
3. `SuiteExecutionListener` registers `com.epam.reportportal.testng.ReportPortalTestNGListener` dynamically when the agent is on the classpath.
4. The agent uploads results while tests run.
5. After the suite finishes, `ReportPortalReportAdapter` logs the dashboard URL and summary.

## Environment variables

Use environment variables for secrets instead of hardcoding them:

```yaml
reporting:
  reportportal:
    apiKey: ${RP_API_KEY}
    endpoint: ${RP_ENDPOINT}
```

## Disabling ReportPortal

Set `enabled: false` or remove the `reportportal` block entirely:

```yaml
reporting:
  reportportal:
    enabled: false
```

## Troubleshooting

### `ReportPortal is enabled but the TestNG agent is not on the classpath`

Add `com.epam.reportportal:agent-java-testng` to your project dependencies as shown in [Prerequisites](#prerequisites).

### Missing required field errors

Check the server log for messages like:

```text
[TestFly] ReportPortal adapter disabled: reporting.reportportal.endpoint is required ...
```

Make sure `endpoint`, `apiKey`, `project`, and `launch` are all provided.

### Launch does not appear in ReportPortal

Verify that:

* The ReportPortal server is reachable from the test runner.
* The `apiKey` has permission to write to the configured `project`.
* The `project` name matches the project URL slug in ReportPortal (case-sensitive).
