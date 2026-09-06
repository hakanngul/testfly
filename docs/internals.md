# TestFly – Internal Design & Execution Model

This document describes the internal design contracts of TestFly.
It defines how execution, lifecycle management, threading, waits, retries, and reporting interact internally.

This document is intended for framework maintainers and advanced contributors.

---

## Java Baseline

- Minimum supported Java version: **Java 17**
- Language features may leverage modern Java constructs
- Backward compatibility below Java 17 is not supported

---

## Core Internal Principles

- One test thread owns exactly one WebDriver instance
- WebDriver lifecycle is framework-managed only
- No shared mutable state across test threads
- Fail fast on misconfiguration or misuse
- Deterministic execution over dynamic behavior

---

## Execution Lifecycle Overview

TestFly integrates with TestNG using listeners and execution hooks.

High-level lifecycle:

1. Framework bootstrap
2. Configuration loading and validation
3. TestNG execution initialization
4. Driver provisioning per thread
5. Test execution with waits and retries
6. Failure evidence capture
7. Report generation
8. Resource cleanup

---

## Configuration Bootstrap

- Configuration is loaded once at startup
- YAML is parsed into immutable configuration objects
- Validation is performed before any test execution
- Invalid configuration fails the build immediately

Configuration objects are read-only during execution.

---

## Driver Lifecycle Model

### Thread Ownership

- Each TestNG thread receives one WebDriver instance
- WebDriver instances are stored using ThreadLocal
- No driver sharing across threads is allowed

### Creation

- Driver is created lazily before first test method execution
- Browser type and execution mode are resolved from configuration
- Driver capabilities are finalized before session creation

### Destruction

- Driver is quit after test execution completes
- Cleanup occurs even in case of test failure or interruption

---

## Parallel Execution Strategy

- Parallel execution is enabled by default
- Thread count is configuration-driven
- Thread safety is enforced at framework boundaries
- Tests must be stateless and independent

Parallelism is controlled centrally to avoid unpredictable behavior.

---

## Wait Strategy

### Explicit Waits

- All waits are explicit and centrally managed
- Timeout values are defined in configuration
- Common wait conditions are standardized

### Implicit Waits

- Implicit waits are explicitly disabled
- Any attempt to enable implicit waits is overridden

This prevents wait compounding and flakiness.

---

## Retry Strategy

### Retry Scope

- Retries apply at the test method level
- Retries are limited to a fixed maximum attempt count
- Retries do not apply to configuration or setup failures

### Failure Classification

- Transient failures may be retried
- Deterministic failures fail immediately
- Retry logic does not suppress final failure status

---

## Failure Handling & Evidence Capture

On test failure:

- Screenshot is captured automatically
- Page source is optionally stored
- Browser logs may be collected (future)

Evidence capture is guaranteed to execute once per failure.

---

## Reporting Pipeline

- Reporting is event-driven via execution hooks
- Reports are generated after execution completion
- Reporting does not affect test execution flow

Report generation failures must not fail the test run.

---

## Logging Strategy

- Framework logs execution lifecycle events
- Test logs remain test-owned
- Logging verbosity is configuration-driven

Logs are structured to support CI environments.

---

## Error Handling Rules

- Framework errors fail fast
- Test assertion failures are isolated
- Infrastructure failures abort execution safely
- Partial execution states are cleaned up deterministically

---

## Extension Points (Controlled)

Framework allows extensions only at defined boundaries:

- Driver providers
- Execution lifecycle hooks
- Reporting adapters
- Configuration overrides
- AI providers (LLM backends)

Extensions must not alter core lifecycle guarantees.

---

## Agentic AI Execution Model

TestFly's Agentic AI layer operates on a **Compile & Freeze** principle to ensure deterministic, low-latency test execution while leveraging LLM reasoning.

### Action Compilation Flow

1. **Goal Intake**: Natural language goal (e.g., `"Delete first item in cart"`) enters via `act(String goal)`.
2. **Cache Lookup**: `ActionCache` checks `.testfly/action-cache.json` for a frozen plan matching `(urlPattern, goal)`.
   - **Hit**: Plan replays directly via `ActionExecutor` with **0ms LLM latency**.
   - **Miss**: Proceeds to compilation.
3. **DOM Pruning**: `DomPruner` strips `<script>`, `<style>`, `<iframe>`, SVGs, comments, and non-interactive nodes, reducing 100K+ token DOMs to **<8K tokens** while preserving semantic attributes (`id`, `name`, `data-testid`, `role`, `aria-*`, text).
4. **LLM Compilation**: `ActionCompiler` sends pruned DOM + goal to configured LLM provider, receiving a structured `ActionPlan` with ordered `ActionStep` primitives.
5. **Plan Freeze**: Compiled plan persists to `.testfly/action-cache.json` for future runs.
6. **Execution**: `ActionExecutor` maps each `ActionStep` to Selenium WebDriver operations via `WaitEngine`.

### Supported Action Primitives

| ActionType | Selenium Operation | Description |
|------------|-------------------|-------------|
| `CLICK` | `WebElement.click()` | Clicks buttons, links, inputs |
| `TYPE` | `clear()` + `sendKeys(value)` | Types text into form fields |
| `CLEAR` | `clear()` | Empties input fields |
| `HOVER` | `Actions.moveToElement()` | Hovers over navigation menus |
| `WAIT_VISIBLE` | `WaitEngine.until(visible)` | Waits for async element render |
| `PRESS_ENTER` | `sendKeys(Keys.ENTER)` | Submits forms/search |
| `SELECT` | `new Select(el).selectByVisibleText(value)` | Chooses a `<select>` option by visible text; requires `value` |
| `NAVIGATE` | `driver.get(resolveTargetUrl(value))` | Absolute URL passes through; a path is resolved against `execution.baseUrl`. Needs no locator, so it bypasses `parseLocator` |

### Semantic Assertion Engine

Semantic assertions are split across two layers — the assertion classes own DOM capture and pruning, `AiAssertEngine` owns the LLM round-trip:

1. **DOM Capture + Pruning (caller side)**: `PageAssert` prunes the whole page via `DomPruner.prune(driver)`; `LocatorAssert` prunes only the targeted element's HTML via `DomPruner.prune(rawHtml)`. Pruning happens **before** `AiAssertEngine` is ever called.
2. **Config & Credential Resolution**: `AiAssertEngine.verify(driver, contextHtml, condition, expectSatisfaction)` reads `TestFlyContext.getConfig()`, resolves `ai.apiKey` through `AiFailureAnalyzer.resolveApiKey` (which expands `${ENV_VAR}` placeholders), then obtains the provider via `AiProviderRegistry.get(ai.provider, ai.baseUrl)`.
3. **Prompt Build**: `buildPrompt` attaches page metadata (`driver.getCurrentUrl()`, `driver.getTitle()`), the pruned HTML, the condition, and its polarity (`expectSatisfaction` → satisfies vs. violates).
4. **LLM Call + Parse**: The provider must return a strict JSON object `{"passed", "confidence", "reason"}`. `parseResult` strips markdown code fences before Jackson parsing.
5. **Fail-Safe Result**: Missing `ai` block, blank API key, unknown provider, empty response, or malformed JSON all return `AiAssertionResult(false, 0.0, <reason>)` — the assertion **fails closed** rather than silently passing. No exception escapes to the test.

`AiAssertEngine` is `@TestFlyApi(since = "1.9.0")`, stateless (private constructor, static methods only), and therefore inherently thread-safe.

Unlike traditional polling (500ms intervals), semantic assertions evaluate the DOM **once**, preventing API rate limits and excessive LLM costs.

### AI Self-Healing Pipeline

When a locator fails, `SelfHealingLocator` drives the recovery:

1. **Static Fallbacks**: Rule-based healing runs first (ID extraction, `name` attributes, exact text matches).
2. **AI Healing Gate**: `SelfHealingLocator` calls `AiHealingEngine.heal(driver, original, testId)` only when `locators.aiHealing: true`. The engine also bails out if the `ai` block or `ai.apiKey` is missing, or if `ai.provider` is unresolvable. Healing is non-critical — it logs and returns rather than failing the test.
3. **Token-Budgeted Pruning**: `DomPruner` truncates to `locators.maxDomTokens`, falling back to `DomPruner.DEFAULT_MAX_TOKENS` when the configured value is `<= 0`.
4. **Visibility Verification**: The LLM-proposed locator is **re-checked against the live page**. If it does not resolve to a visible element the suggestion is discarded (`"AI suggested locator ... was not visible on the page"`), which keeps hallucinated selectors out of the cache.
5. **Dual Persistence** (`HealLog`):
   - `.testfly/healed-locators.json` — durable cache written via `HealingCache`; survives `mvn clean`.
   - `target/healed-locators.json` — per-session export consumed by the HTML report.

   Successful heals are recorded as `HealEvent(testId, originalDesc, healedDesc, "ai-healed")`.
6. **0ms Replay**: Subsequent runs resolve the healed locator directly from `.testfly/healed-locators.json` with no AI latency.

### Failure Analysis & Auto-PR Remediation

With `ai.failureAnalysis: true`:
- `AiFailureAnalyzer` examines stack traces, screenshots, and DOM snapshots.
- Generates human-readable root cause explanations in HTML report.

With `ai.generatePatch: true`:
- `SourceCodeLocator` (`io.testfly.ai.remediation`) maps failures back to test/Page Object source lines.
- `RemediationPatchGenerator` produces Unified Git Diff `.patch` files in `target/remediations/`.
- Developers apply patches via `git apply target/remediations/*.patch`.

### LLM Provider Abstraction

`AiProviderRegistry` resolves providers by name:

| Provider name | Implementation | Protocol |
|---------------|---------------|----------|
| `claude` | `ClaudeProvider` (new instance per call) | Anthropic Messages API (`/v1/messages`) |
| `anthropic` | Alias → `ClaudeProvider` | Anthropic Messages API (`/v1/messages`) |
| `openai-compatible` | `OpenAiCompatibleProvider` (new instance per call) | OpenAI Chat Completions (`/v1/chat/completions`) |
| `openai` | Alias → `OpenAiCompatibleProvider` | OpenAI Chat Completions (`/v1/chat/completions`) |
| `gemini` | `GeminiProvider` (registered singleton) | Google Generative AI REST API |

All providers implement `AiProvider.call(apiKey, model, prompt, timeoutSeconds)` returning raw LLM response text.

Resolution rules in `AiProviderRegistry.get(name, baseUrl)`:

- A `null` or blank name falls back to `ClaudeProvider`.
- `claude` / `anthropic` and `openai-compatible` / `openai` construct a **new instance per call** so the configured `baseUrl` is always honoured.
- Any other name is looked up in the registry, which holds only the `claude` and `gemini` singletons.
- **There is no `deepseek` alias.** To target DeepSeek, Qwen (Alibaba Cloud), Groq, Ollama, or Together AI, set `provider: openai-compatible` plus that vendor's `baseUrl`.

### Thread Safety & Parallel Execution

- `AiProviderRegistry` stores providers in a `ConcurrentHashMap`; `claude` and `openai-compatible` are rebuilt per call so a custom `baseUrl` never leaks across threads, while `gemini` is a shared stateless singleton.
- DOM pruning and LLM calls are isolated per test thread.
- No shared mutable state across parallel test executions.

---

## Internal Constraints

To preserve correctness:

- No static global state
- No driver access outside framework scope
- No test-managed retries
- No dynamic configuration mutation

Violations are considered framework misuse.

---

## Summary

TestFly internals prioritize correctness, predictability, and maintainability.
The framework defines strict execution contracts to ensure stability across
parallel, long-running, enterprise-scale test suites.
