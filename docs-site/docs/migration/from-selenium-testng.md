---
description: "Migrate a Selenium + TestNG framework to TestFly: delete your driver factory, wait utils, retry analyzer, and reporting glue, and see the boilerplate disappear side by side."
id: from-selenium-testng
title: Migrate from Selenium + TestNG
sidebar_label: From Selenium + TestNG
sidebar_position: 1
---

# Migrate from Selenium + TestNG

If you already run a hand-rolled **Selenium + TestNG** framework, you have written — and now maintain — a driver factory, a waits utility, a retry analyzer, screenshot-on-failure glue, and a reporting integration. TestFly ships all of that as one dependency.

This guide is a side-by-side **"your current setup → TestFly equivalent."** The short version: most of the plumbing you maintain today simply gets deleted.

:::info Nothing to relearn
TestFly is still Selenium. `WebDriver`, `By`, `WebElement`, and your existing page-object patterns all still work — you're removing boilerplate, not switching tools.
:::

---

## Setup — swap dependencies

Add TestFly first. Remove duplicate Selenium/WebDriverManager dependencies once their remaining usages are migrated:

:::note Published release and development
The verified Maven Central release is `io.github.hakanngul:testfly:1.0.4`. The installation examples below use that release. This checkout is version `1.0.7`; new development features may not exist in the published artifact. To use the current source, run `mvn clean install -DskipTests -Dgpg.skip=true` from the TestFly repository root and set your consumer dependency version to `1.0.7`. Do not assume `1.0.7` is available on Central.
:::

```xml title="pom.xml"
<dependency>
    <groupId>io.github.hakanngul</groupId>
    <artifactId>testfly</artifactId>
    <version>1.0.4</version>
</dependency>
```

TestFly brings Selenium and TestNG transitively. Check dependency overrides before removing duplicate declarations; keep reporting libraries required by integrations you still use.

Then create a small [`testfly.yml`](/docs/configuration) — see [config mapping](#config-mapping) below.

---

:::info Java version and incremental migration
The Central release `1.0.4` requires Java 17+; versions from `1.0.6` on (including the local `1.0.7` build) require Java 21. Update the build tool, IDE and CI JDK together. The dependency coordinates are `io.github.hakanngul:testfly:1.0.4`. Migrate one class first; remove old infrastructure only after its last consumer has migrated. Keep Allure/ExtentReports dependencies if you still use their custom integrations.
:::

## 1. Driver setup

**Before** — a driver factory, `ThreadLocal` juggling for parallel runs, and `WebDriverManager` to fetch binaries:

```java title="DriverFactory.java (delete this)"
public class DriverFactory {
    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    public static void createDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        DRIVER.set(new ChromeDriver(options));
        DRIVER.get().manage().timeouts()
              .implicitlyWait(Duration.ofSeconds(10));
    }

    public static WebDriver getDriver() { return DRIVER.get(); }

    public static void quitDriver() {
        DRIVER.get().quit();
        DRIVER.remove();
    }
}

public class BaseTest {
    @BeforeMethod public void setUp()    { DriverFactory.createDriver(); }
    @AfterMethod  public void tearDown() { DriverFactory.quitDriver(); }
}
```

**After** — extend `BaseTest`. Driver creation, per-thread isolation, and teardown are handled for you:

```java title="LoginTest.java"
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

public class LoginTest extends BaseTest {

    @Test
    public void loginTest() {
        open();  // navigates to execution.baseUrl
        // ...
    }
}
```

- **No `WebDriverManager`.** Modern Selenium (4.6+) bundles **Selenium Manager**, which downloads the right driver binary automatically. TestFly uses it — delete the `.setup()` calls and the dependency. See [Migrate from WebDriverManager](/docs/migration/from-webdrivermanager) for the details.
- **No `ThreadLocal`.** `DriverManager` isolates the driver per thread, so [parallel runs](/docs/guides/parallel) are safe out of the box.
- Need the raw driver? It's still there: `getDriver()`.

:::caution Drop the implicit wait
Delete `implicitlyWait(...)`. TestFly's locators auto-wait explicitly; mixing implicit and explicit waits is a classic source of flaky, slow tests.
:::

---

## 2. Waits

**Before** — a `WaitUtils` helper wrapping `WebDriverWait` / `ExpectedConditions`, imported into every page:

```java title="WaitUtils.java (delete this)"
public class WaitUtils {
    public static WebElement waitVisible(WebDriver driver, By locator) {
        return new WebDriverWait(driver, Duration.ofSeconds(10))
            .until(ExpectedConditions.visibilityOfElementLocated(locator));
    }
    public static void waitClickable(WebDriver driver, By locator) {
        new WebDriverWait(driver, Duration.ofSeconds(10))
            .until(ExpectedConditions.elementToBeClickable(locator));
    }
}

// usage
WaitUtils.waitVisible(driver, By.id("login")).click();
```

**After** — TestFly locator actions auto-wait. `getWait()` returns a configured Selenium `WebDriverWait`, so use `until(ExpectedConditions...)` for custom conditions:

```java
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

find("#login").click();                          // auto-waits for clickable
getWait().until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".spinner")));
getWait().until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector("h1"), "Welcome back"));
```

No `Thread.sleep()`, no per-page `WebDriverWait` construction, no passing `driver` around. See the [WaitEngine guide](/docs/guides/wait-engine).

---

## 3. Retry / flaky tests

**Before** — an `IRetryAnalyzer` plus a listener to attach it to every method:

```java title="RetryAnalyzer.java + RetryListener.java (delete both)"
public class RetryAnalyzer implements IRetryAnalyzer {
    private int count = 0;
    private static final int MAX = 2;
    @Override public boolean retry(ITestResult result) {
        return count++ < MAX;
    }
}

public class RetryListener implements IAnnotationTransformer {
    @Override public void transform(ITestAnnotation ann, Class c,
                                    Constructor ctor, Method m) {
        ann.setRetryAnalyzer(RetryAnalyzer.class);
    }
}
// + register the listener in testng.xml
```

**After** — one config line turns on retry for the whole suite:

```yaml title="testfly.yml"
retry:
  enabled: true
  maxAttempts: 2   # total attempts including the first run
```

Override per test with `@Retryable` when you need to:

```java
@Test
@Retryable(maxAttempts = 3)
public void flakyTest() { /* ... */ }
```

Recovered vs. still-failing retries are broken out in the report. See the [Retry guide](/docs/guides/retry).

---

## 4. Screenshots on failure

**Before** — an `ITestListener` that reaches into the driver on `onTestFailure`, encodes a PNG, and writes it somewhere your report can find:

```java title="ScreenshotListener.java (delete this)"
public class ScreenshotListener implements ITestListener {
    @Override public void onTestFailure(ITestResult result) {
        WebDriver driver = DriverFactory.getDriver();
        File png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        // ...copy to /screenshots, attach to report, handle IOException...
    }
}
```

**After** — nothing. TestFly captures a screenshot on every failure automatically and embeds it in the HTML report. Delete the listener. See [Screenshots](/docs/guides/screenshots).

---

## 5. Reporting

**Before** — wire in ExtentReports/Allure: a listener, a `flush()` in an `@AfterSuite`, and per-test logging calls scattered through your code.

**After** — a self-contained **HTML report** at `target/testfly-report.html` (pass-rate gauge, retries, embedded screenshots, flakiness) and a **JUnit XML** file for CI, both generated automatically after every run. Add named steps with the optional [Step Logging](/docs/guides/step-logging) API if you want richer reports.

See [HTML Report](/docs/reporting/html-report) and [JUnit XML](/docs/reporting/junit-xml).

---

## What gets deleted

| Your current setup | TestFly |
|---|---|
| `DriverFactory` + `ThreadLocal<WebDriver>` | ✅ Built in — extend `BaseTest` |
| `WebDriverManager.chromedriver().setup()` | ✅ Selenium Manager (automatic) |
| Implicit-wait config | ✅ Auto-waiting locators |
| `WaitUtils` / `WebDriverWait` helpers | ✅ `WaitEngine` + auto-wait |
| `IRetryAnalyzer` + `IAnnotationTransformer` | ✅ `retry:` config + `@Retryable` |
| Screenshot-on-failure `ITestListener` | ✅ Automatic on failure |
| ExtentReports/Allure wiring | ✅ HTML report + JUnit XML |
| `@BeforeMethod` / `@AfterMethod` lifecycle glue | ✅ Framework-managed lifecycle |

Your page objects and `@Test` methods stay — they just get shorter.

---

Remove only the old lifecycle/retry/screenshot listeners that duplicate TestFly responsibilities. Keep listeners implementing business setup, test data or external integrations. Do not register TestFly listeners a second time.

## Config mapping

Settings that lived in `testng.xml` attributes and scattered constants move into one file:

```yaml title="testfly.yml"
execution:
  mode: local
  baseUrl: https://your-app.com
  parallel: methods        # was: <suite parallel="methods">
  threadCount: 4           # was: thread-count="4"

browser:
  name: chrome
  headless: false          # auto-forced true when CI is detected

timeouts:
  explicit: 10             # was: your WaitUtils constant
  pageLoad: 30

retry:
  enabled: true
  maxAttempts: 2           # was: RetryAnalyzer MAX
```

You still keep a minimal `testng.xml` to list your test classes — TestFly registers its own listeners, so remove only duplicate listener entries from the `<listeners>` block. See the [Configuration Reference](/docs/configuration) for every option.

---

## Migrating incrementally

You don't have to convert everything at once:

1. Add the dependency and a `testfly.yml`.
2. Point **one** test class at `BaseTest`, remove driver creation/quit from its `@BeforeMethod`/`@AfterMethod` while keeping business setup and cleanup, and run it.
3. Once green, delete your `DriverFactory`, `WaitUtils`, retry analyzer, and screenshot listener as the last class stops referencing them.

Because TestFly *is* Selenium, a half-migrated suite runs fine.

---

## Keep existing Page Objects

Pass the managed driver into an existing Page Object with `new LoginPage(getDriver())`. You can keep `By`, `WebElement`, `PageFactory` and TestNG assertions during the first migration. Auto-wait applies to TestFly `Locator` operations; raw `WebElement` calls still need their Selenium waits.

When adopting the fluent API, define locators in the page object:

```java title="LoginPage.java"
import io.testfly.test.BasePage;
import io.testfly.locator.Locator;

public class LoginPage extends BasePage {
    private final Locator username = find("#username");
    private final Locator password = find("#password");
    private final Locator submit = find("button[type='submit']");

    public void login(String user, String pass) {
        username.type(user);
        password.type(pass);
        submit.click();
    }
}
```

## Next steps

- [Getting Started](/docs/getting-started) — the 5-minute version
- [BaseTest](/docs/guides/base-test) / [BasePage](/docs/guides/base-page) — the base classes you'll extend
- [Accessibility-First Locators](/docs/guides/semantic-locators) — `getByRole`/`getByLabel`, once the boilerplate is gone
- [Configuration Reference](/docs/configuration) — the full `testfly.yml`
