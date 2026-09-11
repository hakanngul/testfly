---
id: recorder
title: Interactive Recorder & Chrome Companion
sidebar_label: Interactive Recorder
sidebar_position: 3
description: Record live web interactions in Google Chrome and automatically generate clean, production-ready TestFly Java tests and Page Objects.
---

# Interactive Recorder & Chrome Companion

The **TestFly Interactive Recorder** is a live companion testing studio that bridges real user browser interactions directly with production-grade Java automation code. 

By pairing an injected Chrome companion window with real-time Server-Sent Events (SSE), every click, text entry, dropdown selection, and web assertion is streamed to the studio and instantly compiled into **TestFly Java 17+** code across multiple test architectures.

```bash
testfly record https://www.saucedemo.com
```

---

## Architecture Overview

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                              GOOGLE CHROME (COMPANION)                                 │
│  ┌────────────────────────────────────┐       ┌─────────────────────────────────────┐  │
│  │       Live Web Application         │ ────▶ │     Injected Recorder JS (CDP)      │  │
│  │   (Clicks, Typing, Hover, Select)  │       │ (Event Interceptor & Debounce Buff) │  │
│  └────────────────────────────────────┘       └──────────────────┬──────────────────┘  │
└──────────────────────────────────────────────────────────────────┼─────────────────────┘
                                                                   │ POST /api/event (Live User Actions)
                                                                   ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        TESTFLY LOCAL SERVER & CODEGEN ENGINE                           │
│  ┌────────────────────────┐      ┌─────────────────────────┐      ┌─────────────────┐  │
│  │  HTTP Server & Mode    │ ───▶ │ TestFly Codegen Engine  │ ───▶ │  SSE Broadcast  │  │
│  │  (/api/event, /mode)   │      │ (POM, TestNG, Cucumber) │      │  (/api/stream)  │  │
│  └────────────────────────┘      └─────────────────────────┘      └────────┬────────┘  │
└────────────────────────────────────────────────────────────────────────────┼───────────┘
                                                                             │ Real-time Stream
                                                                             ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                              TESTFLY WEB STUDIO (:8765)                                │
│  ┌────────────────────────┐      ┌─────────────────────────┐      ┌─────────────────┐  │
│  │ Recorded Step Timeline │      │  Smart Locator Tester   │      │ Multi-Framework │  │
│  │ (Actions, Assertions)  │      │ (Live Match Validator)  │      │   Code Center   │  │
│  └────────────────────────┘      └─────────────────────────┘      └────────┬────────┘  │
└────────────────────────────────────────────────────────────────────────────┼───────────┘
                                                                             │ "Save to Project"
                                                                             ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                               YOUR TEST REPOSITORY                                     │
│  ┌────────────────────────┐      ┌─────────────────────────┐      ┌─────────────────┐  │
│  │  src/test/java/...     │      │   src/test/java/...     │      │src/test/resource│  │
│  │        /pages/         │      │        /tests/          │      │  s/features/    │  │
│  │  (BasePage Objects)    │      │  (BaseTest & Runners)   │      │(.feature files) │  │
│  └────────────────────────┘      └─────────────────────────┘      └─────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Starting the Recorder

### Command-Line Usage

```bash
# Record against any target website
testfly record https://example.com

# Specify a custom studio port (auto-increments if port is busy)
testfly record https://example.com --port 9000

# Start studio without opening desktop browser automatically
testfly record https://example.com --no-browser
```

### Automatic Chrome Companion Launch
When `testfly record` starts:
1. **Port Selection:** TestFly binds to `8765` by default. If the port is already occupied, it gracefully scans the next 20 ports (`8766`, `8767`, ...) and binds to the first available port.
2. **Web Security Flags:** Google Chrome is launched with dedicated isolated user flags (`--disable-web-security`, `--allow-running-insecure-content`) ensuring seamless local callbacks to the recorder server without CORS or preflight restrictions.
3. **CDP Injection:** TestFly injects `injected_recorder.js` via the Chrome DevTools Protocol (`Page.addScriptToEvaluateOnNewDocument`), meaning the recorder activates instantly across page navigations, reloads, and redirects.

---

## Recording Interactions

### 1. Natural User Actions
Interact with the Chrome companion window just as an end-user would:
- **Clicks:** Elements are captured with accessibility-first selectors (`data-testid`, accessible role, ARIA label, ID, CSS).
- **Text Entry:** Keystrokes are buffered with intelligent typing debouncing and coalescing. Consecutive keystrokes are merged into a single `enterUsername("standard_user")` call rather than separate single-character events.
- **Form Controls:** Dropdown selections, checkboxes, and buttons are automatically formatted into fluent TestFly actions.

### 2. Assertion Recording Modes
TestFly Studio provides dedicated toolbar buttons to record web-first assertions without manual coding:

| Assertion Mode | Shortcut / Action | Generated Code |
| :--- | :--- | :--- |
| **👁️ Assert Visible** | Click element in inspection mode | `assertThat(getByTestId("item-header")).isVisible();` |
| **✅ Assert Enabled** | Click element in enabled mode | `assertThat(getByTestId("submit-btn")).isEnabled();` |
| **💬 Assert Text** | Click element; enter expected text in prompt | `assertThat(getByTestId("price")).hasText("29.99");` |

> [!TIP]
> When **Assert Text** is triggered, a modal dialog appears in the Studio allowing you to verify the element's inner text and choose between **Exact Match** (`hasText(...)`) or **Contains Match** (`containsText(...)`).

---

## Smart Locator Tester

The bottom of the Timeline panel includes an interactive **Smart Locator Tester**:
1. Type or paste any locator expression (e.g. `getByTestId("login-button")`, `//button[@type='submit']`, `#user-name`).
2. The indicator badge dynamically reports the match status against the live companion page (`1 match`, `0 matches`, or `N matches`).
3. Click the **📋 Copy** button to quickly copy the verified selector to your clipboard.

---

## Multi-Framework Java Codegen

As you interact with the companion browser, the right-hand panel renders syntax-highlighted code in real-time across four supported architectures:

### 1. Page Object Model (`pom`)
Separates concerns into reusable Page Object classes and corresponding tests:
- **BasePage Class:** Holds accessibility-first `Locator` fields and fluent action methods (`clickLogin()`, `enterUsername(text)`).
- **BaseTest Class:** Manages the test lifecycle with `open("/")` and inline web-first assertions.

```java
// ============================================================
// File: com/example/pages/HomePage.java
// ============================================================
package com.example.pages;

import io.testfly.test.BasePage;
import io.testfly.locator.Locator;
import org.openqa.selenium.WebDriver;

public class HomePage extends BasePage {

    private final Locator username = getByTestId("username");
    private final Locator password = getByTestId("password");
    private final Locator login = getByTestId("login-button");
    private final Locator continueElement = getByTestId("continue");

    public HomePage(WebDriver driver) {
        super(driver);
    }

    public HomePage enterUsername(String text) {
        username.type(text);
        return this;
    }

    public HomePage clickLogin() {
        login.click();
        return this;
    }

    public HomePage clickContinueElement() {
        continueElement.click();
        return this;
    }
}
```

### 2. Standalone TestNG (`testng`)
Generates single-file standalone TestNG test classes extending `BaseTest` with framework-managed driver lifecycles.

### 3. Standalone JUnit 5 (`junit5`)
Generates single-file JUnit 5 tests extending `BaseJUnit5Test`.

### 4. Cucumber BDD (`cucumber`)
Generates a complete three-tier BDD suite:
- **Gherkin Feature:** Saved to `src/test/resources/features/*.feature`
- **Step Definitions:** Java class extending `BaseCucumberSteps` with annotated `@When`, `@Then` methods
- **Test Runner:** Runner class extending `BaseCucumberTest`

---

## Save to Project & Clean File Routing

When you click **💾 Save to Project** in TestFly Studio, files are saved directly into your project's Maven/Gradle folder structure without merging disparate classes into single files.

### Directory Resolution
TestFly automatically detects your project root and places generated files in standard directories:

```
your-project/
├── pom.xml
└── src/
    └── test/
        ├── java/
        │   └── com/example/
        │       ├── pages/
        │       │   └── HomePage.java            <-- Page Object (BasePage)
        │       └── tests/
        │           ├── HomeTest.java            <-- POM Test (BaseTest)
        │           ├── RecordedWebTest.java     <-- TestNG / JUnit5 Standalone
        │           ├── steps/
        │           │   └── HomeSteps.java       <-- Cucumber Step Definitions
        │           └── runners/
        │               └── RunCucumberTest.java <-- Cucumber Runner
        └── resources/
            └── features/
                └── home.feature                 <-- Cucumber Gherkin Feature
```

### Compiler Safeguards
- **Java Reserved Keyword Protection:** Identifiers matching Java language keywords (e.g. `continue`, `break`, `return`, `class`, `default`, `switch`, `goto`) are automatically converted into safe identifiers (e.g. `continueElement`, `clickContinueElement()`).
- **Numeric Prefix Protection:** Elements whose text starts with digits are given safe prefixes (e.g. `el1Items`) to comply with Java naming standards.
- **Commented File Boundaries:** All file headers use language-safe comments (`//` for Java, `#` for Gherkin), ensuring that files remain 100% syntactically valid under strict Java compilers (javac / Eclipse JDT).
