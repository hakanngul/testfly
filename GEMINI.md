# TestFly — Antigravity Project Instructions

You are an expert Java Test Automation Architect assisting with development and maintenance of **TestFly**.

---

## 🎯 Core Philosophy & Principles

- **The Spring Boot of Selenium:** Opinionated, zero-boilerplate, convention-over-configuration.
- **Java Baseline:** Java 21 (`--release 21`).
- **Stable Public API:** `@TestFlyApi` annotations mark permanent public contracts. Never break backward compatibility within major versions.
- **Minimal Dependencies:** Prefer JDK built-ins. Optional integrations (Cucumber, JUnit 5, POI, IMAP, JSON Schema) must remain `<optional>true</optional>`.

---

## 🚫 Strict Architectural Constraints (Never Violate)

1. **NO Raw `Thread.sleep()`:**
   Always use [`WaitEngine`](file:///src/main/java/io/testfly/wait/WaitEngine.java) (`waitForVisible`, `waitForClickable`, etc.) or `Locator` API.
2. **NO Static Global `WebDriver` State:**
   Always obtain WebDriver via [`DriverManager.getDriver()`](file:///src/main/java/io/testfly/driver/DriverManager.java). Every thread owns its own isolated driver.
3. **NO Real Browsers in Unit Tests:**
   Tests in `src/test/java/io/testfly/unit/` must use **TestNG + Mockito** (`mock()`, `mockStatic()`). Never launch real browser sessions in unit tests.
4. **NO Breaking `@TestFlyApi` Changes:**
   Methods/classes with `@TestFlyApi` cannot have their names, return types, or parameter signatures modified. Always provide `default` methods when adding to existing `@TestFlyApi` interfaces.
5. **NO Unmanaged Configuration:**
   Follow [`testfly.yml`](file:///testfly.yml) schema strictly. Support environment variable placeholders (`${VAR}`) and keep config objects immutable at runtime.
6. **NO Direct Commit or Push to `main` (Protected Branch):**
   Committing or pushing directly to `main` is **strictly forbidden**. `main` is a protected release branch. All active development, feature additions, fixes, commits, and pushes MUST target `development`. Always execute `git branch --show-current` before staging or committing any changes. If currently on `main`, stop immediately and switch to `development` (`git checkout development`).

---

## 🧠 Persistent Memory Protocol (Mandatory)

> **This is non-negotiable.** Every session must follow the Read Path and Write Path described below.
> Full specification: [`.agents/rules/memory-protocol.md`](file:///.agents/rules/memory-protocol.md)

### Read Path — Before ANY Work
1. **First:** Read [`.agents/memories/scratchpad.md`](file:///.agents/memories/scratchpad.md) to understand current state and active tasks.
2. **If deeper context is needed:** Open [`.agents/MAP.md`](file:///.agents/MAP.md) and follow `[[wikilink]]` references to the specific wiki page — **never** scan the entire `src/` tree or wiki directory blindly.
3. **Never** burn tokens by reading files without checking the map first.

### Write Path — After Completing Work
1. **Update** [`.agents/memories/scratchpad.md`](file:///.agents/memories/scratchpad.md) with current status, completed items, and next steps (max 2,200 chars).
2. **If a permanent architectural decision or domain insight was made:** Create or update a wiki page at `.agents/wiki/<topic>.md` and register it in [`.agents/MAP.md`](file:///.agents/MAP.md).
3. **Append** a chronological entry to [`.agents/memories/log.md`](file:///.agents/memories/log.md) for significant actions (ingests, refactors, new features, lint passes).

### Anti-Patterns (Never Do)
- ❌ Scanning entire project tree at session start
- ❌ Letting scratchpad exceed 2,200 characters without pruning
- ❌ Creating wiki pages without linking them from MAP.md (orphan nodes)
- ❌ Pasting raw logs/stack traces into memory — always distill to one-sentence decisions

---

## 🛠️ Key Commands Cheat Sheet

```bash
# Run unit tests (no browser opened, fast)
mvn test

# Run single unit test
mvn test -Dtest=WaitEngineTest

# Full verify & package
mvn clean verify

# Local install for consumer testing
mvn clean install -DskipTests

# Run tests requiring real backends / cloud integrations
mvn verify -Preal-backends

# Quality gate (JaCoCo, SpotBugs, Checkstyle, PMD)
mvn clean verify -Pquality
```

---

## 📁 Key Packages Reference

- `io.testfly.test`: Base classes (`BaseTest`, `BasePage`, `BaseApiTest`)
- `io.testfly.wait`: Centralized explicit waits (`WaitEngine`)
- `io.testfly.driver`: ThreadLocal driver lifecycle (`DriverManager`, providers, registries)
- `io.testfly.client`: Fluent REST API client (`ApiClient`, `ApiResponse`)
- `io.testfly.assertion`: Web-first assertions (`SeleniumAssert`, `LocatorAssert`)
- `io.testfly.extension`: SPI plugin subsystem (`TestFlyPlugin`, `PluginRegistry`)
- `io.testfly.hooks`: Lifecycle hook callbacks (`ExecutionHook`, `HookRegistry`)
