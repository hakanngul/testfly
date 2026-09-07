<!--
  Org profile README for the `testfly` GitHub organization or profile.
  Publish by creating a public repo `.github` and placing this file at
  `profile/README.md`. GitHub renders it on your GitHub profile/organization page.
-->

<div align="center">

# TestFly

### The AI-Native, Modern Java Test Automation Platform

Write less boilerplate. Ship faster. Automate web, API, and BDD with native AI assistance.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.hakanngul/testfly?label=Maven%20Central&color=0969da)](https://central.sonatype.com/artifact/io.github.hakanngul/testfly)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://github.com/hakanngul/testfly/blob/main/LICENSE)
[![Docs](https://img.shields.io/badge/docs-testfly.io-1f6feb)](https://hakanngul.github.io/testfly)
[![MCP Server](https://img.shields.io/badge/MCP-TestFly%20Server-8957e5.svg)](https://github.com/hakanngul/testfly-mcp)

</div>

---

## Not a wrapper — a complete ecosystem

Most Selenium add-ons give you one utility class. **TestFly** is a cohesive, production-ready ecosystem where every component is engineered to work together seamlessly:

| Component | Description | Links |
|---|---|---|
| 🧪 **TestFly Core SDK** | Zero-boilerplate Java automation SDK. Built-in driver lifecycle, smart auto-waits, auto-retries, ThreadLocal isolation, CDP network mocking, and rich interactive HTML reporting. | [`testfly`](https://github.com/hakanngul/testfly) · [Maven Central](https://central.sonatype.com/artifact/io.github.hakanngul/testfly) |
| 🤖 **TestFly MCP** | Official Model Context Protocol (MCP) server that empowers AI agents (Claude, Cursor, Copilot, Antigravity) to drive real browsers and author production-grade TestFly tests. | [`testfly-mcp`](https://github.com/hakanngul/testfly-mcp) |
| 🚀 **Showcase Project** | Runnable consumer test suite demonstrating end-to-end web, REST API, mobile emulation, and Cucumber BDD features. | [`testfly-test`](https://github.com/hakanngul/testfly-test) |
| 📖 **Documentation Site** | Comprehensive guides, interactive code snippets, configuration reference, and architecture deep-dives (English & Turkish). | [Documentation](https://hakanngul.github.io/testfly) |

---

## Pure Test Logic. Zero Ceremony.

No manual `WebDriver` instantiation. No `@AfterMethod` teardown chore. No brittle `Thread.sleep()`. Just clean, expressive, and reliable test code:

```java
public class CheckoutTest extends BaseTest {

    @Test(description = "User completes checkout flow with fluent assertions")
    public void userCompletesCheckout() {
        open("/shop");

        find(".product-card").first().find("button.add-to-cart").click();
        find("#cart-badge").click();

        find("#checkout-btn").click();
        find("#coupon-input").fill("SUMMER2026");
        find("#apply-coupon").click();

        assertThat(find(".discount-label")).isVisible();
        assertThat(find(".total-price")).hasText("$79.99");
        assertThatPage().hasTitle("Order Confirmation — TestFly Store");
    }
}
```

---

## ⚡ Key Platform Capabilities

- **Universal Test Framework Support**: First-class runners for **TestNG** (`BaseTest`), **JUnit 5** (`BaseJUnit5Test`), and **Cucumber 7 BDD** (`@TestFlySession`).
- **Declarative Network Mocking**: Full CDP v152 Chrome/Edge network routing, payload stubbing, rate limiting, and request assertions via `page().route()`.
- **Playwright-Inspired Fluent Locators & Assertions**: `assertThat(locator)` and `assertThatPage()` with automatic retry loops and descriptive failure diffs.
- **Unified REST API Testing**: Synchronous and asynchronous polling API client with built-in JSONPath assertions and timeline cURL tracing.
- **Enterprise Reporting**: Out-of-the-box interactive HTML report with step-by-step screenshots, flakiness radar, timeline charts, and Allure integration.
- **Mobile Device Emulation**: Realistic viewport, pixel ratio, and touch event emulation for smartphones and tablets.

---

## 🤖 Prompt-to-Test with TestFly MCP

TestFly brings AI into your daily test workflow through **[TestFly MCP](https://github.com/hakanngul/testfly-mcp)**:

1. **Connect**: Plug the TestFly MCP server into Claude Desktop, Cursor, VS Code, or Google Antigravity.
2. **Instruct**: Tell the AI agent what flow to automate (e.g., *"Test the password reset flow on staging"*).
3. **Execute & Author**: The agent drives a real browser session, analyzes the live DOM, crafts resilient selectors, and writes idiomatic TestFly Java code directly into your repository.

---

## Getting Started

```xml
<dependency>
    <groupId>io.github.hakanngul</groupId>
    <artifactId>testfly</artifactId>
    <version>1.0.2</version>
</dependency>
```

- 📘 **[Quickstart Guide](https://hakanngul.github.io/testfly/docs/intro)** — Get your first green test running in under 60 seconds.
- 🧰 **[Configuration Guide](https://hakanngul.github.io/testfly/docs/configuration)** — Explore `testfly.yml` environment profiles and capabilities.
- 💬 **[GitHub Discussions](https://github.com/hakanngul/testfly/discussions)** — Questions, ideas, feature proposals, and community support.
- ⭐ **Star the repository** if TestFly saves you time and boilerplate!

<div align="center">

_Apache 2.0 License · Maintained with ❤️ by the TestFly Team_

</div>
