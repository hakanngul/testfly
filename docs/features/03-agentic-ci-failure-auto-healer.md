# Feature Plan: Agentic CI Failure Auto-Healer & PR Creator

- **Feature Name**: `testfly-ci-healer`
- **Target Version**: `v1.3.0`
- **Status**: Proposed / Architecture Design
- **Author**: TestFly Core Team

---

## 1. Executive Summary

In fast-moving engineering teams, UI test failures in Continuous Integration (CI) are often caused by benign frontend changes: a CSS class rename, an updated `data-testid`, a shifted DOM hierarchy, or a minor copy edit on a button. Engineers waste hours checking logs, downloading CI artifacts, checking out the branch, fixing one selector, and waiting for CI to re-run.

The **TestFly Agentic CI Failure Auto-Healer** turns test failures into automated pull requests. Powered by the existing `testfly-mcp` engine and GitHub Actions, when a test suite fails in CI, TestFly bundles the failure context (stack trace, DOM snapshot at the moment of failure, screenshot, and the test source file), diagnoses the root cause using an LLM agent, generates the code fix, validates the selector against the captured DOM snapshot, and automatically opens a Pull Request with a detailed Root Cause Analysis (RCA) and one-click merge.

---

## 2. Motivation & Problem Statement

### The Problem Today
1. **Selector Rot & Maintenance Overhead**: Front-end redesigns regularly break test selectors (`#submit-btn` becomes `.btn-checkout`), halting deployment pipelines even though business logic is intact.
2. **Context Switching**: Developers and QA engineers must stop feature work, download artifact archives from CI, inspect screenshots, and manually push a 1-line selector fix.
3. **Existing "Self-Healing" Frameworks are Opaque**: Many commercial tools heal selectors dynamically at runtime in memory without updating the source code, creating hidden technical debt and unreliable test suites.

### The Solution: Transparent, Source-Controlled Healing via Pull Requests
Instead of silent in-memory healing during runtime, TestFly heals at the **repository level**:
- If a test fails in CI, an AI agent diagnoses the breakage.
- If it is a clear selector drift with >90% confidence, it creates a branch: `fix/testfly-auto-heal-{testName}`.
- It pushes the corrected code and submits a GitHub Pull Request with before/after screenshots and explanation.
- The developer simply reviews and merges the PR with a single click.

---

## 3. Architecture & Technical Design

```
+-----------------------------------------------------------------------------------+
|                            GitHub Actions CI Runner                               |
|                                                                                   |
|  $ mvn clean test  ---> [Test Failed: CheckoutTest#verifyOrderPlaced]             |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|               io.testfly.reporter.FailureBundlePackager                           |
|  Generates: target/testfly-failures/failure-bundle.json                           |
|  - Failed Test Source Code (`CheckoutTest.java:L45`)                              |
|  - Exception & Stack Trace (`NoSuchElementException: .btn-pay`)                  |
|  - DOM Snapshot at moment of failure (HTML + attributes)                          |
|  - Visual Screenshot (Base64 / PNG)                                               |
|  - Last 10 executed actions from StepLogger                                       |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                      testfly-ci-healer (GitHub Action)                            |
|                                                                                   |
|  1. Spins up `testfly-mcp` in headless agent mode                                 |
|  2. Calls MCP tool: `diagnose_failure(failure_bundle)`                             |
|  3. LLM evaluates DOM snapshot vs broken locator:                                |
|     - Original: find(".btn-pay")                                                  |
|     - Match in DOM: <button data-testid="checkout-submit">Pay Now</button>        |
|     - Suggested fix: getByTestId("checkout-submit") or getByRole(BUTTON, "Pay Now")|
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                             Automated Git Bot                                     |
|  - Creates branch: `testfly-heal/checkout-test-btn-pay`                           |
|  - Patches `CheckoutTest.java` with the resilient locator                         |
|  - Runs `mvn test -Dtest=CheckoutTest` to verify fix                              |
|  - Opens PR to `main` with detailed RCA & diff                                    |
+-----------------------------------------------------------------------------------+
```

---

## 4. GitHub Actions Workflow Integration

### Workflow Definition (`.github/workflows/e2e.yml`)
```yaml
name: TestFly E2E Suite

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          
      - name: Run TestFly Tests
        run: mvn test
        
      - name: TestFly Auto-Healer (on failure)
        if: failure()
        uses: hakanngul/testfly-healer-action@v1
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          llm_api_key: ${{ secrets.GEMINI_API_KEY }}
          auto_pr: true
          confidence_threshold: 0.85
```

---

## 5. Sample Pull Request Output

When a test failure is diagnosed and repaired, the bot generates a PR formatted as follows:

```markdown
## 🤖 TestFly Auto-Healer: Fixed Broken Locator in `CheckoutTest.java`

### ❌ Failure Cause
The test failed with `NoSuchElementException` on line 48:
`find(".btn-pay").click();`

### 🔍 Root Cause Analysis (RCA)
The target button class `.btn-pay` was replaced by the frontend team with Tailwind utility classes in commit `a8f190c`. However, the element now provides an accessible name and a stable `data-testid`.

### 🛠️ Proposed Solution
Replaced brittle CSS class locator with a resilient, accessible locator:

```diff
- find(".btn-pay").click();
+ getByRole(Role.BUTTON, "Pay Now").click();
```

### 📊 Healing Confidence
- **Confidence Score**: `98%`
- **DOM Element Matches**: Exactly 1 unique match found in captured DOM snapshot
- **Re-Verification**: Automated dry-run compilation and unit execution passed ✅
```

---

## 6. Phased Implementation Plan

### Phase 1: Failure Bundle Packaging in Core TestFly (Sprint 1)
- [ ] Implement `FailureBundlePackager` in `io.testfly.reporter`.
- [ ] Capture the exact line of code from stack trace and extract surrounding code snippet.
- [ ] Export DOM snapshot and screenshot bundle into `target/testfly-failures/` as structured JSON.

### Phase 2: Healer Tooling in `testfly-mcp` (Sprint 2)
- [ ] Add `diagnose_test_failure` tool to `testfly-mcp`:
  - Input: `failure_bundle.json`.
  - Parses DOM snapshot using BeautifulSoup / HTML parser.
  - Matches failed locator intent (text, role, testid, coordinates).
  - Evaluates confidence score (0.0 to 1.0).
- [ ] Add `apply_source_patch` tool to safely update Java AST or source lines.

### Phase 3: GitHub Action & PR Workflow (Sprint 3)
- [ ] Develop `hakanngul/testfly-healer-action` composite action.
- [ ] Implement git branch creation, patch commit, and PR creation via GitHub REST API / Octokit.
- [ ] Add safeguards: never push to protected branches; only open PRs or comment on existing PRs.

---

## 7. Security, Safety & Governance

1. **No Direct Master/Main Pushes**: The healer **never** writes directly to `main` or release branches. It strictly creates feature branches and opens PRs requiring human review.
2. **Confidence Threshold Gating**: If the AI confidence score is below 85% (e.g. if the failure looks like a genuine business logic regression rather than a selector change), no PR is opened. Instead, it only leaves an analytical diagnostic comment on the CI run.
3. **Data Privacy**: Failure bundles contain only DOM structures and screenshots from test environments; sensitive credentials or tokens in config files are redacted via existing `testfly` credential sanitizers.
