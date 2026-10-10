---
name: testfly-web-write
description: Author consumer-facing TestFly Web UI automation, including Page Objects, semantic and CSS locators, browser interactions, synchronization, UI assertions, and TestNG, JUnit 5, or Cucumber scenarios. Use for writing or refactoring browser tests to native TestFly APIs; not for API/load tests, general Java refactoring, SDK architecture changes, or TestFly implementation work.
---

# TestFly Web UI authoring

Write Java 21 browser tests against TestFly's public Page Object, locator, assertion, navigation, browser, and framework-lifecycle APIs. Do not modify the SDK for a consumer test-authoring request and do not expose `io.testfly.internal.*`.

## Workflow

1. Inspect the installed TestFly version's public signatures when the repository or dependency version differs from these references.
2. Choose the lifecycle boundary: `BaseTest` for TestNG, `BaseJUnit5Test` or `@EnableTestFly` for JUnit 5, and `BaseCucumberSteps` plus `io.testfly.cucumber` glue for Cucumber.
3. Put locators and reusable interactions in a small `BasePage` Page Object or focused component. Let the framework own ordinary driver setup and teardown.
4. Prefer semantic locators (`getByRole`, `getByLabel`, `getByTestId`, `getByPlaceholder`) when the UI exposes stable semantics; use `find(...)` for stable CSS or supported Selenium `By` interoperability.
5. Rely on Locator terminal auto-waits and retrying TestFly assertions. Add an explicit condition only for state that an action or assertion does not already synchronize.
6. Compile the authored test or example, then use `testfly-verify` for the smallest relevant repository gate. Report browser execution separately from compilation.

## Non-negotiable rules

- Use only verified methods. There is no `.filter(hasText(...))`, `.fill()`, `.val()`, `RoleOptions`, `selectOption`, `check`, `uncheck`, or Locator `press` API.
- Valid refinements include `filter(String css)`, `withText(String)`, `within(By)`, `nth(int)`, `first()`, `last()`, `withName(String)`, `withLevel(int)`, and `exact()` where their documented locator kind supports them.
- Use `locator.type(value)` to clear and type, `append(value)` to retain existing text, and `inputValue()` to read an input value. Use `element().sendKeys(Keys...)` only for keyboard operations without a TestFly wrapper.
- `getWait()` returns Selenium `WebDriverWait`; call `getWait().until(ExpectedConditions...)`. For TestFly explicit waits call static `WaitEngine` methods. Never write `getWait().waitForVisible(...)`.
- Do not use deprecated `$()` aliases or `SessionCache` in new code. Prefer `find(...)` and `BrowserSessionCache`.
- Do not pass `WebDriver` through every Page Object constructor. A no-argument `BasePage` subclass resolves the framework-managed, thread-confined driver lazily; accept a driver only for a deliberate custom/session boundary.
- Keep page/test state per scenario or thread. Never store mutable driver, element, session, or assertion state in static fields, and do not cache `WebElement` across page changes.
- Treat self-healing as an optional fallback, not synchronization. Stable locators and deterministic waits remain the reliability mechanism.
- Use raw Selenium only at an explicit interoperability boundary; keep it localized and retain TestFly lifecycle ownership.

## Read only what the task needs

- Locator factories, chaining, actions, form controls, CSS/Selenium interop, and known invalid APIs: [references/locators-and-actions.md](references/locators-and-actions.md)
- Page Objects, reusable components, state ownership, navigation, lifecycle, and parallel safety: [references/page-object-patterns.md](references/page-object-patterns.md)
- Automatic/explicit waiting, assertions, soft assertions, and exact healing eligibility: [references/waits-and-assertions.md](references/waits-and-assertions.md)
- Compile-checked Page Object, TestNG, JUnit 5, and Cucumber examples: [references/framework-examples.md](references/framework-examples.md)
- Capability status inventory plus frames, Shadow DOM, sessions, network, visual, accessibility, performance, recording, and configuration: [references/advanced-web.md](references/advanced-web.md)

## Handoff

State the framework integration used, optional dependencies/configuration, checks run, and whether a real browser was executed. Call out unsupported behavior instead of inventing a wrapper. For BDD output, group Step Definition methods first and put the required Page Object methods in a separate block.
