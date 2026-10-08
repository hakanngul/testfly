---
description: "Get your first Selenium test running in under 5 minutes: add one dependency, extend BaseTest, and run, with no WebDriver setup or boilerplate."
id: getting-started
title: Getting Started
sidebar_position: 2
---

# Getting Started

:::note Published release and development
The verified Maven Central release is `io.github.hakanngul:testfly:1.0.4`. The installation examples below use that release. This checkout is version `1.0.7`; new development features may not exist in the published artifact. To use the current source, run `mvn clean install -DskipTests` from the TestFly repository root and set your consumer dependency version to `1.0.7`. Do not assume `1.0.7` is available on Central.
:::

Get your first TestFly test running in under 5 minutes.

---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

## Prerequisites

- Java 21+
- Maven 3.8+ **or** Gradle 8.5+
- Chrome, Firefox, or Edge installed

:::info
No WebDriver binaries required — Selenium Manager handles browser driver downloads automatically.
:::

:::tip Instant Setup with TestFly MCP & NPX (Recommended)
You can scaffold a production-ready TestFly Java 21 project with a single command:

```bash
npx @testfly/mcp init my-test-suite
```

This generates `pom.xml`, `testfly.yml`, and ready-to-run sample tests. Learn more in the [TestFly MCP & CLI Guide](/docs/cli).
:::

---

## Manual Setup

If adding TestFly to an existing project, follow the steps below:

### Step 1 — Add the dependency

<Tabs>
<TabItem value="maven" label="Maven (pom.xml)">

```xml title="pom.xml"
<dependency>
    <groupId>io.github.hakanngul</groupId>
    <artifactId>testfly</artifactId>
    <version>1.0.4</version>
</dependency>
```

</TabItem>
<TabItem value="gradle-groovy" label="Gradle Groovy (build.gradle)">

```groovy title="build.gradle"
dependencies {
    testImplementation 'io.github.hakanngul:testfly:1.0.4'
}

test {
    useTestNG()
    systemProperties System.properties
}
```

</TabItem>
<TabItem value="gradle-kotlin" label="Gradle Kotlin (build.gradle.kts)">

```kotlin title="build.gradle.kts"
dependencies {
    testImplementation("io.github.hakanngul:testfly:1.0.4")
}

tasks.test {
    useTestNG()
    systemProperties(System.getProperties().mapKeys { it.key.toString() })
}
```

</TabItem>
</Tabs>

:::tip Using Gradle?
See the full [Gradle Setup Guide](/docs/gradle) for parallel config, JUnit 5, optional deps, and report locations.
:::

---

## Step 2 — Create the configuration file

Create `testfly.yml` in your project root (next to `pom.xml` or `build.gradle`):

```yaml title="testfly.yml"
execution:
  mode: local
  baseUrl: https://your-app.com

browser:
  name: chrome
  headless: false

retry:
  enabled: true
  maxAttempts: 2

timeouts:
  explicit: 10
  pageLoad: 30
```

:::note Required keys
`execution.mode`, `browser.name`, `timeouts.explicit`, and `timeouts.pageLoad` are mandatory. If any of them is missing, TestFly stops at startup with a configuration error. Everything else has a sensible default.
:::

---

## Step 3 — Write your first test

```java title="src/test/java/com/example/LoginTest.java"
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

public class LoginTest extends BaseTest {

    @Test(description = "Valid user can log in")
    public void loginTest() {
        open();  // navigates to baseUrl
        // your test steps here
        softAssert().that(getDriver().getTitle().contains("Dashboard"), "Title should contain Dashboard");
    }
}
```

---

## Step 4 — Create a TestNG suite

```xml title="testng.xml"
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
<suite name="testfly-suite" verbose="1">
    <test name="MyTests">
        <classes>
            <class name="com.example.LoginTest"/>
        </classes>
    </test>
</suite>
```

---

## Step 5 — Run

<Tabs>
<TabItem value="maven" label="Maven">

```bash
mvn test
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```bash
./gradlew test
```

</TabItem>
</Tabs>

---

## What happens

1. Framework loads `testfly.yml`
2. Chrome launches automatically
3. Your test runs
4. Screenshot captured on any failure
5. Browser closes
6. HTML report generated at `target/testfly-report.html` (Maven) or `build/testfly-report/` (Gradle)
7. Metrics JSON at `target/testfly-metrics.json`

---

## Project structure

<Tabs>
<TabItem value="maven" label="Maven">

```
your-project/
├── pom.xml
├── testfly.yml
├── testng.xml
└── src/test/java/com/example/
    ├── pages/LoginPage.java
    └── tests/LoginTest.java
```

</TabItem>
<TabItem value="gradle" label="Gradle">

```
your-project/
├── build.gradle (or build.gradle.kts)
├── testfly.yml
├── testng.xml
└── src/test/java/com/example/
    ├── pages/LoginPage.java
    └── tests/LoginTest.java
```

</TabItem>
</Tabs>

---

## Working example project

A complete working project is available at:
**https://github.com/hakanngul/testfly-test**

Clone it, run `mvn test` (or `./gradlew test`), and you'll have a full working suite with page objects, step logging, and retry configured.

---

## Next steps

- [Configuration Reference](/docs/configuration) — all available config options
- [BasePage](/docs/guides/base-page) — write clean page objects
- [Step Logging](/docs/guides/step-logging) — add named steps to your tests
