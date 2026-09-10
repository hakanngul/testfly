---
name: testfly-workflow
description: >-
  Procedures, rules, and runbooks for developing, testing, refactoring, and maintaining the TestFly automation framework.
  Use when writing unit tests, implementing framework features, adding SPI extensions, or handling release/version bumps.
---

# TestFly Development & Maintenance Workflow

This skill guides you through implementing features, writing unit tests, adding SPI extensions, and running checks in the TestFly codebase.

---

## 1. Feature Implementation Guidelines

When implementing a new feature in TestFly:

1. **API Design & Public Contract:**
   - Mark public classes and methods intended for end-users with `@TestFlyApi(since = "X.Y.Z")`.
   - Internal helper classes belong in `io.testfly.internal.*` or should package-private without the `@TestFlyApi` annotation.
   - If adding a method to an existing interface, provide a `default` implementation to prevent breaking third-party implementations.

2. **Driver & Wait Engine Interaction:**
   - Never interact with `WebDriver` via static global state.
   - Always route calls through `DriverManager.getDriver()`.
   - All waits must go through `WaitEngine` explicit methods (`waitForVisible`, `waitForClickable`, etc.). Never call `Thread.sleep()` directly or set `implicitlyWait`.

3. **Configuration & Defaults:**
   - Map any new configuration properties in `io.testfly.config.TestFlyConfig`.
   - Ensure sensible defaults in `TestFlyDefaults`.

---

## 2. Unit Testing Runbook

Every new feature or bug fix must include comprehensive unit tests in `src/test/java/io/testfly/unit/`.

### Unit Test Rules
- **No Real Browsers:** Tests must run headless without opening a physical or virtual browser.
- **Mockito & Static Mocking:** Mock `WebDriver`, `WebElement`, `DriverManager`, and `TestFlyContext`.
- **Parallel Safety:** If mocking static methods (`mockStatic`), ensure proper teardown in `@AfterMethod` and use `@Test(singleThreaded = true)` if state collision is possible.

### Template: Unit Test with Mockito
```java
package io.testfly.unit;

import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import org.mockito.MockedStatic;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

@Test(singleThreaded = true)
public class MyFeatureTest {

    private WebDriver mockDriver;
    private MockedStatic<DriverManager> driverManagerMock;
    private MockedStatic<TestFlyContext> contextMock;

    @BeforeMethod
    public void setUp() {
        mockDriver = mock(WebDriver.class);
        driverManagerMock = mockStatic(DriverManager.class);
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockDriver);

        TestFlyConfig config = new TestFlyConfig();
        // configure test timeouts / settings
        contextMock = mockStatic(TestFlyContext.class);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);
    }

    @AfterMethod
    public void tearDown() {
        if (driverManagerMock != null) driverManagerMock.close();
        if (contextMock != null) contextMock.close();
    }

    @Test
    public void testFeatureBehavior() {
        // Assertions and test logic
    }
}
```

---

## 3. Extension & SPI Development

When creating or extending SPI integration points:

- **Custom Driver Providers:** Implement `NamedDriverProvider` and register in `src/main/resources/META-INF/services/io.testfly.driver.NamedDriverProvider`.
- **Report Adapters:** Implement `ReportAdapter` and register in `src/main/resources/META-INF/services/io.testfly.reporting.ReportAdapter`.
- **Execution Hooks:** Implement `ExecutionHook` and register in `src/main/resources/META-INF/services/io.testfly.hooks.ExecutionHook`.
- **Plugins:** Implement `TestFlyPlugin` and register in `src/main/resources/META-INF/services/io.testfly.extension.TestFlyPlugin`.

---

## 4. Verification Commands

```bash
# 1. Run unit test suite
mvn test

# 2. Run specific test
mvn test -Dtest=MyFeatureTest

# 3. Clean verify (compile, test, package)
mvn clean verify

# 4. Quality gate check
mvn clean verify -Pquality
```

---

## 5. Version Bump Checklist

When bumping the framework version (e.g., `1.0.0` -> `1.1.0`), update **all** occurrences:

1. `pom.xml` (`<version>` tag)
2. `README.md` (Maven dependency snippet & `Current release` line)
3. `CHANGELOG.md` (New release notes at top)
4. `docs-site/docs/getting-started.md`
5. `docs-site/docs/junit5.md`
6. `docs-site/docs/changelog.md`
7. `docs-site/src/pages/index.js`
