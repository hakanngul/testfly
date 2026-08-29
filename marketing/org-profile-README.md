<!--
  Org profile README for the `testfly` GitHub organization.
  Publish by creating a public repo `testfly/.github` and placing this file at
  `profile/README.md`. GitHub renders it on https://github.com/testfly
-->

<div align="center">

# TestFly

### The complete, AI-native platform for Java test automation.

Write less. Ship faster. Let an agent draft the test.

[![Maven Central](https://img.shields.io/maven-central/v/io.testfly/testfly?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.hakanngul/testfly)

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://github.com/testfly/testfly/blob/master/LICENSE)
[![Docs](https://img.shields.io/badge/docs-testfly.github.io-1f6feb)](https://hakanngul.github.io/TestFly)

</div>

---

## Not a wrapper — a platform

Most Selenium add-ons give you one thing. TestFly is a **cohesive ecosystem** where every
piece is built to work with the others:

| | What it is | Where |
|---|---|---|
| 🧪 **TestFly** | Zero-boilerplate Java framework — driver lifecycle, smart waits, retries, parallel, HTML reports, API testing, accessibility-first locators | [`testfly`](https://github.com/hakanngul/testfly) · [Maven Central](https://central.sonatype.com/artifact/io.github.hakanngul/testfly) |
| 🤖 **TestFly MCP** *(coming soon)* | An MCP server that will let Claude / Copilot drive a real browser and **generate ready-to-run tests** — TestNG, JUnit 5, Page Object, Gherkin, Python, C# | Coming soon |
| 🧩 **TestFly MCP VS Code Extension** *(coming soon)* | One-click install — will auto-register the MCP server with GitHub Copilot & Claude Code, no manual setup | Coming soon |
| 📖 **Documentation** | Full guides, configuration reference, deep dives | [testfly.github.io](https://hakanngul.github.io/TestFly) |
| 🚀 **Example project** | A runnable consumer project covering every feature | [`testfly-test`](https://github.com/testfly/testfly-test) |

Java distribution via **Maven Central**, docs, and a runnable example —
all maintained together. AI-powered tooling (MCP server + VS Code extension) is coming soon.

---

## The whole test, and nothing but the test

```java
public class LoginTest extends BaseTest {

    @Test(description = "Valid user can log in")
    public void loginTest() {
        open();
        new LoginPage(getDriver()).login("admin", "secret");
        Assert.assertTrue(new DashboardPage(getDriver()).isLoaded());
    }
}
```

No `WebDriver` setup. No `@AfterMethod` teardown. No wait helpers. No retry config. **Just the test.**

---

## Describe it. Let AI write it. *(Coming Soon)*

**TestFly MCP** — an MCP server that will let Claude / GitHub Copilot drive a real browser,
record your session, and generate ready-to-run TestFly test code from a plain-English description —
is **coming soon**. Stay tuned for the public release.

---

## Start here

- 📘 **[Getting Started](https://hakanngul.github.io/TestFly/docs/getting-started)** — first test in under 5 minutes
- 🧰 **[Documentation](https://hakanngul.github.io/TestFly)**
- 💬 **[Discussions](https://github.com/testfly/testfly/discussions)** — questions, ideas, show & tell
- ⭐ Star the repos if this saves you boilerplate — it helps others find the project.

<div align="center">

_Apache 2.0 · Independent open-source project, not affiliated with Selenium or the Spring Framework._

</div>
