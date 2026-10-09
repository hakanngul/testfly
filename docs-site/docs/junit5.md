---
description: "Run TestFly with JUnit 5: opt in via BaseJUnit5Test, @EnableTestFly, or @ExtendWith(TestFlyExtension) to share most TestNG BaseTest capabilities, including UI, API, DB, and accessibility testing, with documented retry and lifecycle differences."
id: junit5
title: JUnit 5 Support
sidebar_position: 10
---

# JUnit 5 Support

TestFly supports both **TestNG** (built-in) and **JUnit 5** (opt-in). Rather than a minimal runner, the JUnit 5 integration is a first-class citizen sharing core capabilities with TestNG `BaseTest`: framework-managed WebDriver lifecycle, ThreadLocal isolation, fluent locators, web-first and soft assertions, built-in REST API testing, multi-user sessions, HTML timeline reporting, AI failure analysis, and flakiness tracking.

---

## Setup

### Maven

Add JUnit 5 dependencies alongside TestFly:

```xml title="pom.xml"
<dependencies>
    <!-- TestFly Core -->
    <dependency>
        <groupId>io.github.hakanngul</groupId>
        <artifactId>testfly</artifactId>
        <version>1.0.7</version>
    </dependency>

    <!-- JUnit 5 Jupiter & Platform Launcher -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.2</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.junit.platform</groupId>
        <artifactId>junit-platform-launcher</artifactId>
        <version>1.10.2</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Maven Surefire 3.x auto-detects JUnit 5 without any extra plugin configuration.

### Gradle

```groovy title="build.gradle"
dependencies {
    testImplementation 'io.github.hakanngul:testfly:1.0.7'
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.10.2'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

---

## Integration Options

TestFly gives you three ways to integrate with JUnit 5, matching any architecture.

### Option A — Extend `BaseJUnit5Test` (Recommended)

The simplest and most feature-complete approach. It provides identical convenience APIs to TestNG's `BaseTest`:

```java
import io.testfly.junit5.BaseJUnit5Test;
import io.testfly.locator.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

class LoginTest extends BaseJUnit5Test {

    @Test
    @DisplayName("User can log in with valid credentials")
    void validLogin() {
        open("/login");

        step("Enter credentials");
        getByLabel("Username").type("admin");
        getByLabel("Password").type("secret");
        getByRole(Role.BUTTON, "Sign In").click();

        step("Verify dashboard with screenshot", true);
        assertThat(By.id("dashboard")).isVisible();
    }
}
```

#### What `BaseJUnit5Test` provides out of the box:

| Category | Available Methods & Capabilities |
|---|---|
| **Navigation** | `open()`, `open(path)`, `getDriver()`, `getWait()` |
| **Semantic Locators** | `getByRole(Role, name)`, `getByText()`, `getByLabel()`, `getByPlaceholder()`, `getByTestId()`, `getByAltText()`, `getByTitle()` |
| **Fluent Locators** | `find(css)`, `find(By)`, `$(css)`, `$$(css)` |
| **Web-First Assertions** | `assertThat(By).isVisible()`, `assertThat(Locator).hasText(...)`, `assertThat(...).count(n)` |
| **Soft Assertions** | `softAssert(By).isVisible()`, `softAssert(By).hasText(...)`, `softAssert().that(...)` |
| **REST API Testing** | `apiClient()`, `apiGet(path)`, `apiPost(path)`, `apiPut(path)`, `apiPatch(path)`, `apiDelete(path)` (each returns an `ApiClient` builder; finish with `.send()`) |
| **Multi-Session** | `session(name)`, `withSession(name, runnable)` for multi-user / chat / marketplace flows |
| **Database Checks** | `db()` (default datasource) and `db("datasourceName")` for SQL queries and row assertions such as `assertRowExists(table, conditions)` |
| **Email Verification** | `mailbox()`, `to("user@example.com")` for OTP, link, and content checks |
| **Accessibility (a11y)**| `accessibility().scan()`, `assertAccessibility()` using axe-core |
| **Step Logging** | `step(name)`, `step(name, takeScreenshot)` for HTML timeline reporting |

---

### Option B — `@EnableTestFly` on Your Own Base Class

If your project already uses a custom base class hierarchy, annotate it with `@EnableTestFly`:

```java
import io.testfly.driver.DriverManager;
import io.testfly.junit5.EnableTestFly;
import org.openqa.selenium.WebDriver;

@EnableTestFly
public abstract class CustomAppTest {

    protected WebDriver getDriver() {
        return DriverManager.getDriver();
    }
}
```

`@EnableTestFly` is a composed annotation that registers `TestFlyExtension` under the hood.

---

### Option C — `@ExtendWith(TestFlyExtension.class)` & Parameter Injection

For pure POJO test classes with zero inheritance, register `TestFlyExtension` directly and inject `WebDriver` as a method parameter:

```java
import io.testfly.junit5.TestFlyExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

@ExtendWith(TestFlyExtension.class)
class DirectInjectionTest {

    @Test
    void loginWithInjectedDriver(WebDriver driver) {
        driver.get("https://example.com/login");
        driver.findElement(By.id("username")).sendKeys("admin");
        driver.findElement(By.cssSelector("button[type='submit']")).click();
    }
}
```

`TestFlyExtension` automatically provisions a driver for the thread, passes it into your test method, and cleanly tears it down afterwards.

---

## Non-UI Tests with `@NoBrowser`

If a JUnit 5 test class or method only interacts with REST APIs, databases, or mailboxes, annotate it with `@NoBrowser`. TestFly skips WebDriver initialization completely, speeding up test execution and conserving resources:

```java
import io.testfly.client.ApiResponse;
import io.testfly.junit5.BaseJUnit5Test;
import io.testfly.test.NoBrowser;
import org.junit.jupiter.api.Test;

import java.util.Map;

class UserApiIntegrationTest extends BaseJUnit5Test {

    @Test
    @NoBrowser  // No browser is opened; executes purely via HTTP
    void verifyUserCreationViaApi() {
        ApiResponse response = apiPost("/api/users")
                .body("{\"name\":\"John Doe\",\"email\":\"john@example.com\"}")
                .send();

        response.assertStatus(201)
                .assertBodyContains("John Doe");

        // Verify record in database
        db().assertRowExists("users", Map.of("email", "john@example.com"));
    }
}
```

You can also place `@NoBrowser` at the class level to make all test methods browser-less.

---

## Hybrid API & UI Testing Example

Because `BaseJUnit5Test` includes `ApiSupport`, you can seamlessly seed state via API before testing the UI:

```java
import io.testfly.junit5.BaseJUnit5Test;
import io.testfly.locator.Role;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

class OrderHistoryTest extends BaseJUnit5Test {

    @Test
    void userCanViewCreatedOrder() {
        // 1. Seed order data quickly via REST API (bypassing slow UI forms)
        step("Seed order data via API");
        String orderId = apiPost("/api/orders")
                .body("{\"item\":\"Widget\",\"qty\":2}")
                .send()
                .json("$.id");

        // 2. Open browser to order history page
        step("Open order history in browser");
        open("/orders/" + orderId);

        // 3. Assert using modern accessibility locators & soft asserts
        softAssert(getByRole(Role.HEADING, "Order Details")).isVisible();
        softAssert(By.id("order-id")).hasText(orderId);
        softAssert(By.className("order-status")).hasText("CONFIRMED");
    }
}
```

---

## Multi-User / Multi-Session Workflows

To test collaboration tools, chat applications, or marketplaces (e.g. buyer vs. seller), use `session()` or `withSession()`:

```java
import io.testfly.junit5.BaseJUnit5Test;
import io.testfly.locator.Role;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

class MarketplaceChatTest extends BaseJUnit5Test {

    @Test
    void buyerAndSellerCanChat() {
        // Session 1: Buyer sends a message
        session("buyer");
        open("/chat/101");
        getByPlaceholder("Type a message...").type("Is this item still available?");
        getByRole(Role.BUTTON, "Send").click();

        // Session 2: Seller receives the message in an isolated browser window
        session("seller");
        open("/chat/101");
        assertThat(By.cssSelector(".message.received"))
                .hasText("Is this item still available?");

        getByPlaceholder("Type a message...").type("Yes, ready to ship!");
        getByRole(Role.BUTTON, "Send").click();

        // Switch back to buyer session to verify response
        session("buyer");
        assertThat(By.cssSelector(".message.incoming"))
                .hasText("Yes, ready to ship!");
    }
}
```

Both sessions run with completely isolated cookies, cache, and localStorage on the same thread. All active sessions are automatically closed when the test completes.

---

## Session Caching with `@PreCondition`

Use `@PreCondition` to execute authentication or complex setup steps once, caching the browser session (cookies + localStorage). Successive tests reuse the cached session without logging in again:

```java
import io.testfly.junit5.BaseJUnit5Test;
import io.testfly.precondition.PreCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

class DashboardTest extends BaseJUnit5Test {

    @Test
    @PreCondition("loginAsAdmin")
    @DisplayName("View dashboard — cached session restored")
    void viewDashboard() {
        open("/dashboard");
        assertThat(By.id("welcome-header")).isVisible();
    }

    @Test
    @PreCondition("loginAsAdmin")
    @DisplayName("Edit profile — same cached session reused")
    void editProfile() {
        open("/profile");
        assertThat(By.id("profile-form")).isVisible();
    }
}
```

### Defining the Condition Provider

Implement `BaseConditions` and register it via Java SPI:

```java
import io.testfly.precondition.BaseConditions;
import io.testfly.precondition.ConditionProvider;
import org.openqa.selenium.By;

public class AppConditions extends BaseConditions {

    @ConditionProvider("loginAsAdmin")
    public void loginAsAdmin() {
        open("/login");
        type(By.id("username"), "admin");
        type(By.id("password"), "secret");
        click(By.id("login-btn"));
    }
}
```

Create the SPI registration file:

```text title="src/test/resources/META-INF/services/io.testfly.precondition.BaseConditions"
com.yourcompany.conditions.AppConditions
```

> **Automatic Retry Invalidation:** If a test with `@PreCondition` fails and is retried, TestFly automatically invalidates the cached session, ensuring the retry runs with a fresh authentication sequence.

---

## Smart Retries with `@Retryable`

Use `@Retryable` on a test method or class to automatically retry flaky tests. Each retry attempt runs with a **fresh WebDriver instance**:

```java
import io.testfly.junit5.BaseJUnit5Test;
import io.testfly.listeners.Retryable;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

class PaymentTest extends BaseJUnit5Test {

    @Test
    @Retryable(maxAttempts = 2) // Retries up to 2 times on failure (3 attempts total)
    void processPayment() {
        open("/checkout");
        find("#pay-btn").click();
        assertThat(By.id("receipt")).isVisible();
    }
}
```

- Placing `@Retryable` on the class applies retries to all its test methods.
- Omitting `maxAttempts` defaults to `retry.maxAttempts` from `testfly.yml`:

```yaml title="testfly.yml"
retry:
  enabled: true
  maxAttempts: 1
```

Retried tests display a **↻ Nx** badge in the HTML report.

:::caution Retry differences from TestNG
JUnit 5 retries are implemented inside `TestFlyExtension.interceptTestMethod`, not by re-scheduling the test:

- Only the test method body is re-invoked. Your own `@BeforeEach` / `@AfterEach` methods and TestFly's `beforeEach` setup (test data loading, `@UseAuth`, recording start, console-error shim) are **not** repeated between attempts. The driver is recreated and `@PreCondition` is re-run.
- JUnit reports a single test result (the last attempt). Earlier failed attempts are not shown as separate JUnit entries; they are visible only as the retry count in the TestFly report.
- `maxAttempts` means **additional** attempts (`maxAttempts = 2` → up to 3 runs).
- Only `WebDriver` method parameters are replaced with the new driver on retry; other injected parameters keep their original values.
:::

---

## Browser Lifecycle: Per-Test vs. Per-Suite

Configure driver lifecycle in `testfly.yml`:

```yaml title="testfly.yml"
browser:
  name: chrome
  lifecycle: per-test   # or 'per-suite'
```

- **`per-test` (Default):** A fresh browser is opened before each test (`beforeEach`) and closed immediately after (`afterEach`). Best for total isolation.
- **`per-suite`:** A single browser instance is reused across tests for faster execution. In JUnit 5, `TestFlyExtension.afterAll()` quits all suite drivers when each test class finishes, so reuse is effectively **per test class**, not across the whole run as with TestNG.

---

## Parallel Execution

JUnit 5 supports parallel test execution out of the box. Add `junit-platform.properties` to your test resources:

```properties title="src/test/resources/junit-platform.properties"
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=concurrent
junit.jupiter.execution.parallel.mode.classes.default=concurrent
junit.jupiter.execution.parallel.config.strategy=fixed
junit.jupiter.execution.parallel.config.fixed.parallelism=4
```

TestFly's ThreadLocal driver architecture guarantees complete driver and session isolation across concurrent threads.

---

## Enterprise Integrations

JUnit 5 benefits from all TestFly enterprise features with zero boilerplate:

### 1. AI Failure Analysis
When a JUnit 5 test fails, TestFly captures the DOM context, page URL, title, and stack trace, generating an actionable root-cause analysis via Google Gemini or Anthropic Claude:

```yaml title="testfly.yml"
ai:
  failureAnalysis: true
  provider: gemini
  apiKey: ${GEMINI_API_KEY}
```

### 2. Test Management (TestRail & Xray)
Test results, execution times, and failure reasons are automatically pushed to TestRail or Xray.

### 3. ReportPortal Integration
If `reporting.reportportal.enabled=true` in `testfly.yml`, `TestFlyExtension` auto-bridges test lifecycle events, launches, and failure logs directly to your ReportPortal instance.

### 4. Quarantining Flaky Tests
Quarantine flaky JUnit 5 tests dynamically without modifying test code using `testfly-quarantine.yml`:

```yaml title="testfly-quarantine.yml"
quarantine:
  - test: com.yourcompany.tests.FlakyCheckoutTest#testPayment
    reason: "Under investigation for payment gateway timeout"
```

Quarantined tests are safely skipped before any browser or resource allocation occurs.

---

## Feature Comparison: TestNG vs. JUnit 5

Most `BaseTest` capabilities are available in JUnit 5. ⚠️ marks features that work with the differences described above.

| Feature | TestNG | JUnit 5 |
|---|:---:|:---:|
| Automated WebDriver Lifecycle | ✅ | ✅ |
| `per-suite` Browser Reuse | ✅ | ⚠️ per test class |
| ThreadLocal Driver Isolation | ✅ | ✅ |
| Fluent Locators (`$()`, `find()`) | ✅ | ✅ |
| Semantic Locators (`getByRole`, `getByText`, etc.) | ✅ | ✅ |
| Web-First Assertions (`assertThat`) | ✅ | ✅ |
| Soft Assertions (`softAssert`) | ✅ | ✅ |
| Built-in REST Client (`apiClient`, `apiGet/Post`) | ✅ | ✅ |
| Multi-User Sessions (`session()`, `withSession()`) | ✅ | ✅ |
| Database Assertions (`db()`) | ✅ | ✅ |
| Email & Mailbox Testing (`mailbox()`) | ✅ | ✅ |
| Accessibility Audits (`accessibility().scan()`) | ✅ | ✅ |
| `@NoBrowser` Non-UI Execution | ✅ | ✅ |
| HTML Report + Step Timeline | ✅ | ✅ |
| Automatic Screenshot on Failure | ✅ | ✅ |
| AI Failure Root Cause Analysis | ✅ | ✅ |
| Execution Tracing & Screen Recording | ✅ | ✅ |
| JavaScript Console Error Detection | ✅ | ✅ |
| `@PreCondition` Session Caching | ✅ | ✅ |
| `@Retryable` Smart Retry Mechanism | ✅ | ⚠️ method body only |
| `testfly-quarantine.yml` Quarantine Support | ✅ | ✅ |
| ReportPortal, TestRail & Xray Sync | ✅ | ✅ |
