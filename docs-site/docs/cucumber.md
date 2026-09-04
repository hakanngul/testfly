---
description: "BDD Selenium testing with Cucumber 7: driver lifecycle, parallel execution, API step helpers, ScenarioContext data sharing, AI failure analysis, and ReportPortal integration."
id: cucumber
title: BDD / Cucumber
sidebar_position: 11
---

# BDD / Cucumber Integration

TestFly integrates with Cucumber 7 out of the box. The framework manages the entire lifecycle — WebDriver provisioning per scenario, ThreadLocal driver isolation, screenshots on failure, step timelines in the HTML report, built-in REST API testing inside steps, cross-step state sharing via `ScenarioContext` without DI boilerplate, quarantine handling, and AI root cause analysis.

---

## Setup

Add Cucumber dependencies alongside TestFly:

```xml title="pom.xml"
<dependencies>
    <!-- TestFly Core -->
    <dependency>
        <groupId>io.testfly</groupId>
        <artifactId>testfly</artifactId>
        <version>1.0.0</version>
    </dependency>

    <!-- Cucumber Java & TestNG -->
    <dependency>
        <groupId>io.cucumber</groupId>
        <artifactId>cucumber-java</artifactId>
        <version>7.20.1</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.cucumber</groupId>
        <artifactId>cucumber-testng</artifactId>
        <version>7.20.1</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## Project Structure

A typical TestFly + Cucumber project layout:

```text
src/
└── test/
    ├── java/
    │   └── com/yourcompany/
    │       ├── bdd/
    │       │   ├── CucumberRunner.java          ← Extends BaseCucumberTest
    │       │   └── steps/
    │       │       ├── AuthSteps.java           ← Extends BaseCucumberSteps
    │       │       ├── ProductSteps.java        ← Extends BaseCucumberSteps
    │       │       └── OrderSteps.java          ← Extends BaseCucumberSteps
    └── resources/
        ├── features/
        │   ├── login.feature
        │   └── checkout.feature
        ├── cucumber.properties                  ← For IDE single-scenario runs
        └── testfly.yml                          ← Framework configuration
```

---

## Runner Class & Parallel Execution

Annotate your runner with `@CucumberOptions` and extend `BaseCucumberTest`.

```java title="src/test/java/com/yourcompany/bdd/CucumberRunner.java"
package com.yourcompany.bdd;

import io.cucumber.testng.CucumberOptions;
import io.testfly.cucumber.BaseCucumberTest;
import org.testng.annotations.DataProvider;

@CucumberOptions(
    features = "src/test/resources/features",
    glue     = {"com.yourcompany.bdd.steps", "io.testfly.cucumber"},
    plugin   = {"pretty", "io.testfly.cucumber.CucumberStepLogger"}
)
public class CucumberRunner extends BaseCucumberTest {

    /**
     * Required for parallel scenario execution in TestNG Cucumber!
     */
    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
```

:::warning CRITICAL: Parallel Scenario Gotcha in Cucumber-TestNG
By default, Cucumber's `AbstractTestNGCucumberTests` runs scenarios **sequentially**, even if `parallel: methods` is set in `testfly.yml`. To execute scenarios concurrently across threads, you **must override `scenarios()` with `@DataProvider(parallel = true)`** in your runner as shown above!
:::

### Parallel Configuration in `testfly.yml`

Control the concurrency level in `testfly.yml`:

```yaml title="testfly.yml"
execution:
  mode: local
  parallel: methods
  threadCount: 4
  maxActiveSessions: 4

browser:
  name: chrome
  headless: true
```

Each scenario receives its own isolated `WebDriver` instance on its own thread, managed safely by `DriverManager`.

### Glue & Plugin Requirements:
- `"io.testfly.cucumber"` in `glue` is **mandatory** — it instructs Cucumber to discover `CucumberHooks`, which orchestrates driver provisioning, metrics, AI analysis, and teardown.
- `CucumberStepLogger` in `plugin` streams Gherkin step names and status directly into the TestFly HTML timeline report.

---

## Step Definitions (`BaseCucumberSteps`)

Step definition classes should extend `BaseCucumberSteps`. It exposes the full TestFly convenience API directly to your step methods:

```java title="src/test/java/com/yourcompany/bdd/steps/LoginSteps.java"
package com.yourcompany.bdd.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.testfly.cucumber.BaseCucumberSteps;
import io.testfly.locator.Role;
import org.openqa.selenium.By;

public class LoginSteps extends BaseCucumberSteps {

    @Given("the user is on the login page")
    public void onLoginPage() {
        open("/login");
    }

    @When("they sign in with username {string} and password {string}")
    public void signIn(String username, String password) {
        getByLabel("Username").type(username);
        getByLabel("Password").type(password);
        getByRole(Role.BUTTON, "Sign In").click();
    }

    @Then("the dashboard header is displayed")
    public void dashboardDisplayed() {
        assertThat(getByRole(Role.HEADING, "Dashboard")).isVisible();
    }
}
```

### What `BaseCucumberSteps` Provides:

| Capability | Available Methods |
|---|---|
| **Navigation** | `open()`, `open(path)`, `getDriver()`, `getWait()` |
| **Semantic Locators** | `getByRole(Role, name)`, `getByText()`, `getByLabel()`, `getByPlaceholder()`, `getByTestId()`, `getByAltText()`, `getByTitle()` |
| **Fluent Locators** | `find(css)`, `find(By)`, `$(css)`, `$$(css)` |
| **Web-First Assertions** | `assertThat(By)`, `assertThat(Locator)` with automatic waiting |
| **Soft Assertions** | `softAssert(By).isVisible()`, `softAssert(By).hasText(...)` |
| **Built-in REST Client** | `apiClient()`, `apiGet(path)`, `apiPost(path, body)`, `apiPut()`, `apiDelete()` |
| **Step Logging** | `step(name)`, `step(name, takeScreenshot)` |
| **Cucumber Context** | `getScenario()` returning the current `io.cucumber.java.Scenario` |

---

## Sharing State Between Steps (`ScenarioContext`)

In standard Cucumber, passing state (such as an auth token, user ID, or generated order number) between different step definition classes requires configuring an external dependency injection container like PicoContainer or Spring.

TestFly provides built-in, thread-safe scenario state sharing via **`ScenarioContext`**:

```java title="src/test/java/com/yourcompany/bdd/steps/OrderSteps.java"
package com.yourcompany.bdd.steps;

import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.testfly.context.ScenarioContext;
import io.testfly.cucumber.BaseCucumberSteps;
import org.openqa.selenium.By;

public class OrderSteps extends BaseCucumberSteps {

    @When("the user places an order for item {string}")
    public void placeOrder(String item) {
        find(".buy-btn").click();
        String orderNumber = find("#confirmation-num").getText();

        // Store state for subsequent steps (even in other step classes!)
        ScenarioContext.put("orderId", orderNumber);
    }

    @Then("the order status should be confirmed")
    public void verifyOrderStatus() {
        // Retrieve state across steps
        String orderId = ScenarioContext.get("orderId", String.class);
        
        open("/orders/" + orderId);
        assertThat(By.id("order-status")).hasText("CONFIRMED");
    }
}
```

> **Automatic Cleanup:** `CucumberHooks` automatically invokes `ScenarioContext.clear()` in `@After(order = 20000)`, guaranteeing zero memory leaks or state leakage between scenarios.

---

## Hybrid API & UI Steps in BDD

BDD scenarios often require prerequisite test data. Instead of wasting time clicking through UI setup flows, use TestFly's built-in `ApiSupport` right inside your step definitions:

```java
public class UserSteps extends BaseCucumberSteps {

    @Given("an active user exists with email {string}")
    public void seedUserViaApi(String email) {
        // Create user instantly via REST API
        String json = String.format("{\"email\":\"%s\",\"role\":\"CUSTOMER\"}", email);
        String userId = apiPost("/api/users", json)
                .assertThat().statusCode(201)
                .jsonPath().getString("id");

        ScenarioContext.put("userId", userId);
    }
}
```

---

## Feature Files & Scenario Outlines

Standard Gherkin syntax works seamlessly:

```gherkin title="src/test/resources/features/checkout.feature"
Feature: Checkout Process

  Background:
    Given an active user exists with email "buyer@testfly.io"
    And the user is on the login page

  Scenario: Complete checkout with credit card
    When they sign in with username "buyer@testfly.io" and password "secret"
    And the user places an order for item "Mechanical Keyboard"
    Then the order status should be confirmed

  Scenario Outline: Promo code discounts
    When they apply promo code "<code>"
    Then the discount reflects "<percentage>"

    Examples:
      | code       | percentage |
      | SUMMER2026 | 20%        |
      | WELCOME10  | 10%        |
```

Every `Examples` row in a Scenario Outline generates an independent entry in the TestFly HTML report with its own execution metrics, screenshots, and step timeline.

---

## IDE Single-Scenario Execution

When running an individual scenario directly from IntelliJ IDEA or Eclipse (Right-Click → *Run 'Scenario: ...'*), IDEs use their own runner and do not read `@CucumberOptions`.

Add a `cucumber.properties` file in `src/test/resources` so `CucumberHooks` and `CucumberStepLogger` are always activated:

```properties title="src/test/resources/cucumber.properties"
cucumber.glue=com.yourcompany.bdd.steps,io.testfly.cucumber
cucumber.plugin=pretty,io.testfly.cucumber.CucumberStepLogger
cucumber.monochrome=true
```

---

## Smart Retries: Global & Tagged

### 1. Global Retry in `testfly.yml`
```yaml title="testfly.yml"
retry:
  enabled: true
  maxAttempts: 1   # 1 retry = 2 attempts total
```

### 2. Per-Scenario Retry Tags
Override global retry behavior on specific scenarios:

| Tag | Behavior |
|---|---|
| `@retryable` | Retries using global `retry.maxAttempts` from `testfly.yml` |
| `@retryable=N` | Retries up to `N` times (e.g., `@retryable=2`), overriding config |

```gherkin
@retryable=2
Scenario: Payment gateway occasionally flakes under heavy load
  When they submit payment details
  Then the receipt is displayed
```

When retried, TestFly launches a **fresh browser instance from step 1** and marks retried scenarios with a **↻ Nx** badge in the HTML report.

---

## Quarantining Scenarios

Temporarily exclude known flaky or under-development scenarios without deleting tests or modifying Git code:

### Method 1: Tag-Based Quarantine
Add the `@quarantine` tag directly to the scenario or feature:

```gherkin
@quarantine
Scenario: Flaky legacy export feature
  When they click export
  Then file downloads
```

You can customize the tag name in `testfly.yml`:
```yaml title="testfly.yml"
quarantine:
  enabled: true
  cucumberTag: "flaky"   # Uses @flaky instead of @quarantine
```

### Method 2: YAML-Based Quarantine (`testfly-quarantine.yml`)
Quarantine scenarios centrally without touching `.feature` files:

```yaml title="testfly-quarantine.yml"
quarantine:
  - scenario: "checkout.feature#Complete checkout with credit card"
    reason: "Downstream payment simulator maintenance"
  - feature: "export.feature"
    reason: "Feature under rewrite"
```

Quarantined scenarios are skipped (`SkipException`) before WebDriver is initialized, saving time and cloud resources.

---

## AI Failure Analysis for Cucumber

When a Cucumber scenario fails, `CucumberHooks` extracts:
- Current page URL & Title
- Failed Gherkin step & line number
- Full exception stack trace & DOM context

It sends this context to Google Gemini or Claude and embeds a formatted root cause analysis at the bottom of the HTML report:

```markdown
**Root Cause:** The step `Then the order status should be confirmed` failed because element `By.id: order-status` was still showing `PENDING` after 10s.
**Recommended Fix:**
- Verify whether the background worker for payment processing is started.
- Add an explicit wait for status transition: `assertThat(By.id("order-status")).hasText("CONFIRMED")`.
```

Enable it in `testfly.yml`:
```yaml title="testfly.yml"
ai:
  failureAnalysis: true
  provider: gemini
  apiKey: ${GEMINI_API_KEY}
```

---

## Enterprise Integrations

### ReportPortal Automatic Step Nesting
`BaseCucumberTest` includes built-in auto-registration for the ReportPortal Cucumber 7 plugin (`com.epam.reportportal.cucumber.ScenarioReporter`).

When `agent-java-cucumber7` is on the classpath and `reporting.reportportal.enabled=true`, TestFly auto-registers the reporter:
- Features appear as Root Launches/Suites
- Scenarios appear as Test Items
- Steps (`Given`, `When`, `Then`) appear as nested child logs with screenshots attached to the failing step!

### JavaScript Console Error Capture
During scenario execution, `ConsoleErrorCollector` monitors browser console output. If JavaScript errors are detected, they appear as warning steps (`[JS Error]`) in the scenario timeline.

If `browser.failOnConsoleErrors: true` is configured in `testfly.yml`, the scenario will fail if unhandled JS errors occurred.

---

## Running Cucumber Scenarios

```bash
# Run all Cucumber scenarios
mvn test -Dtest=CucumberRunner

# Run a specific feature file
mvn test -Dtest=CucumberRunner -Dcucumber.features=src/test/resources/features/checkout.feature

# Run scenarios with a specific tag
mvn test -Dtest=CucumberRunner -Dcucumber.filter.tags="@smoke and not @quarantine"

# Run with an environment profile (testfly-staging.yml)
mvn test -Dtest=CucumberRunner -Dtestfly.profile=staging
```
