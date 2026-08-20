<!--
  Org profile README for the `testfly` GitHub organization.
  Publish by creating a public repo `testfly/.github` and placing this file at
  `profile/README.md`. GitHub renders it on https://github.com/testfly
-->

<div align="center">

# TestFly

### The complete, AI-native platform for Java test automation.

Write less. Ship faster. Let an agent draft the test.

[![Maven Central](https://img.shields.io/maven-central/v/io.testfly/testfly?label=Maven%20Central)](https://central.sonatype.com/artifact/io.testfly/testfly)
[![PyPI](https://img.shields.io/pypi/v/testfly-mcp?label=PyPI%20(MCP))](https://pypi.org/project/testfly-mcp/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://github.com/testfly/testfly/blob/master/LICENSE)
[![Docs](https://img.shields.io/badge/docs-testfly.github.io-1f6feb)](https://testfly.github.io/testfly)

</div>

---

## Not a wrapper — a platform

Most Selenium add-ons give you one thing. TestFly is a **cohesive ecosystem** where every
piece is built to work with the others:

| | What it is | Where |
|---|---|---|
| 🧪 **TestFly** | Zero-boilerplate Java framework — driver lifecycle, smart waits, retries, parallel, HTML reports, API testing, accessibility-first locators | [`testfly`](https://github.com/testfly/testfly) · [Maven Central](https://central.sonatype.com/artifact/io.testfly/testfly) |
| 🤖 **TestFly MCP** | An MCP server that lets Claude / Copilot drive a real browser and **generate ready-to-run tests** — TestNG, JUnit 5, Page Object, Gherkin, Python, C# | [`testfly-mcp`](https://github.com/testfly/testfly-mcp) · [PyPI](https://pypi.org/project/testfly-mcp/) |
| 🧩 **TestFly MCP VS Code Extension** | One-click install — auto-registers the MCP server with GitHub Copilot & Claude Code, no manual setup | [Marketplace](https://marketplace.visualstudio.com/items?itemName=testfly.testfly-mcp) |
| 📖 **Documentation** | Full guides, configuration reference, deep dives | [testfly.github.io](https://testfly.github.io/testfly) |
| 🚀 **Example project** | A runnable consumer project covering every feature | [`testfly-test`](https://github.com/testfly/testfly-test) |

Java distribution via **Maven Central**, AI tooling via **PyPI**, docs, and a runnable example —
all maintained together. That's the part that's hard to copy.

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

## Describe it. Let AI write it.

**One-click in VS Code** — install the
[TestFly MCP extension](https://marketplace.visualstudio.com/items?itemName=testfly.testfly-mcp);
it auto-registers the MCP server with GitHub Copilot and Claude Code, no config needed.

**Or via pip** for any MCP client:

```bash
pip install testfly-mcp
```

Then describe a flow in plain English — the AI drives a real Chrome/Firefox browser and writes the
TestFly test for you, with self-healing locators and codegen for Java / Python / C# / Gherkin.

---

## Start here

- 📘 **[Getting Started](https://testfly.github.io/testfly/docs/getting-started)** — first test in under 5 minutes
- 🧰 **[Documentation](https://testfly.github.io/testfly)**
- 💬 **[Discussions](https://github.com/testfly/testfly/discussions)** — questions, ideas, show & tell
- ⭐ Star the repos if this saves you boilerplate — it helps others find the project.

<div align="center">

_Apache 2.0 · Independent open-source project, not affiliated with Selenium or the Spring Framework._

</div>
