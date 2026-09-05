# TestFly Feature Roadmap & Architecture Specifications

Welcome to the **TestFly Advanced Feature Specifications (RFCs)**. This directory contains detailed architectural plans, design specifications, and phased delivery roadmaps for the flagship features transforming TestFly into the most modern, developer-friendly Java test automation framework in the industry.

---

## 🚀 Flagship Feature Specifications

| # | Feature RFC | Target Version | Category | Status | Summary |
|---|---|---|---|---|---|
| **01** | [**Time-Travel & Interactive Trace Viewer**](./01-time-travel-trace-viewer.md) | `v1.2.0` | Debugging & Diagnostics | Proposed | Visual timeline scrubber, DOM snapshots, micro-screencasts, and console/network logs packaged into offline `.zip` & interactive HTML. |
| **02** | [**Declarative Network Interception & Mocking DSL**](./02-declarative-network-mocking.md) | `v1.2.0` | Network & API | Proposed | Playwright-style native browser request routing, response mocking, latency delays, and ad-blocking via Selenium 4 BiDi/CDP without proxy servers. |
| **03** | [**Agentic CI Failure Auto-Healer & PR Creator**](./03-agentic-ci-failure-auto-healer.md) | `v1.3.0` | AI & CI/CD Automation | Proposed | Automated GitHub Action that diagnoses CI test failures via `testfly-mcp`, heals broken selectors, and opens ready-to-merge Pull Requests with full RCA. |
| **04** | [**Built-in Visual Regression & Pixel Diffing Engine**](./04-visual-regression-testing.md) | `v1.2.0` | Visual Testing | Proposed | Zero-dependency pure-Java visual testing with automatic baselines, dynamic element masking, antialiasing tolerance, and HTML split-diff slider. |
| **05** | [**IDE In-Gutter Live Locator Inspector & Highlighting**](./05-ide-in-gutter-locator-inspector.md) | `v1.3.0` | Developer Experience (DX) | Proposed | IntelliJ IDEA and VS Code plugins with gutter icons that pulse live browser elements, show match counts inline, and suggest healed selectors on hover. |

---

## 🎯 How TestFly Compares to Modern Tooling

By combining the strengths of modern browser protocols (BiDi/CDP) with enterprise Java ecosystems and cutting-edge Model Context Protocol (MCP) agents, TestFly achieves features previously exclusive to NodeJS frameworks like Playwright and Cypress—while introducing agentic AI workflows no traditional framework offers:

```
┌───────────────────────────────────────┬────────────┬────────────┬────────────────────────┐
│ Feature / Capability                  │ Playwright │  Selenium  │    TestFly (Planned)   │
├───────────────────────────────────────┼────────────┼────────────┼────────────────────────┤
│ Language Native Ecosystem             │ Node/Python│    Java    │       Java 21+         │
│ Auto-Waiting & Smart Polling          │     ✅     │  Manual    │     ✅ Built-in        │
│ Declarative Semantic Locators         │     ✅     │     ❌     │     ✅ Built-in        │
│ Time-Travel Trace Viewer              │     ✅     │     ❌     │  ✅ RFC-01 (v1.2)      │
│ In-Browser Network Mocking DSL        │     ✅     │ Requires   │  ✅ RFC-02 (v1.2)      │
│                                       │            │ WireMock   │  (Native BiDi/CDP)     │
│ Zero-Vendor Visual Regression Engine  │     ✅     │ Requires   │  ✅ RFC-04 (v1.2)      │
│                                       │            │ Applitools │  (Pure Java)           │
│ Agentic CI Self-Healing Pull Requests │     ❌     │     ❌     │  ✅ RFC-03 (v1.3)      │
│                                       │            │            │  (testfly-mcp AI)      │
│ In-Editor Gutter Inspector (IntelliJ) │     ❌     │     ❌     │  ✅ RFC-05 (v1.3)      │
└───────────────────────────────────────┴────────────┴────────────┴────────────────────────┘
```

---

## 📅 Roadmap & Milestones

### Milestone `v1.1.0` (Current Focus)
- Multi-browser cross-platform matrix testing (Chrome, Firefox, Safari, Edge)
- Parallel stability improvements and thread-safety hardening
- Allure & HTML reporting enhancements

### Milestone `v1.2.0` (Debugging & Quality Suite)
- [RFC 01: Time-Travel & Interactive Trace Viewer](./01-time-travel-trace-viewer.md)
- [RFC 02: Declarative Network Interception & Mocking DSL](./02-declarative-network-mocking.md)
- [RFC 04: Built-in Visual Regression & Pixel Diffing Engine](./04-visual-regression-testing.md)

### Milestone `v1.3.0` (AI & Next-Gen Developer Experience)
- [RFC 03: Agentic CI Failure Auto-Healer & PR Creator](./03-agentic-ci-failure-auto-healer.md)
- [RFC 05: IDE In-Gutter Live Locator Inspector & Highlighting](./05-ide-in-gutter-locator-inspector.md)
- Official VS Code and IntelliJ IDEA marketplace extensions

---

## 🤝 Contributing to Feature RFCs

To propose enhancements to these specifications or sponsor a feature:
1. Review the relevant RFC document above.
2. Open an issue on GitHub tagged `RFC: <feature-name>`.
3. Provide feedback on API design, performance implications, or real-world use cases.
