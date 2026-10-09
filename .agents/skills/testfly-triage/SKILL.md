---
name: testfly-triage
description: Diagnose TestFly test, build, CI, browser, API, engine, SPI, or flaky parallel-execution failures without prematurely changing production code. Use when a test is failing, flaky, hanging, timing out, or behaving differently across local and CI environments; not for routine verification or writing new tests.
---

# TestFly failure triage

## Preserve evidence

1. Capture the exact command, failing test identity, first relevant exception, environment/JDK/browser details, and whether the failure reproduces.
2. Inspect `target/surefire-reports`, `target/failsafe-reports`, TestFly reports/artifacts, screenshots, recordings, traces, and CI logs that exist. Do not rerun before saving ephemeral evidence when reruns may overwrite it.
3. Reduce to the narrowest deterministic command. Compare isolated, class, module/full-suite, and—only when relevant—parallel execution.

## Classify before fixing

- **Product regression:** deterministic framework behavior differs from its contract.
- **Test defect:** assertion, fixture, isolation, ordering, cleanup, or invalid test data is wrong.
- **Environment/integration:** browser/driver, network, credentials, optional service, clock, port, filesystem, or version drift.
- **Concurrency/flakiness:** shared state, unsafe singleton, timing assumption, retry masking, or leaked session/thread.
- **Infrastructure:** Maven/plugin/toolchain, CI configuration, resource exhaustion, or report collection.

Trace TestFly failures through engine adapter → lifecycle/execution → domain adapter → reporting/cleanup. For API tests include request/response evidence with secrets redacted; for browser tests include locator, page state, waits, driver/session ownership, and screenshot/recording evidence.

## Outcome

State the smallest reproduction, evidence, classification, root cause or ranked hypotheses, and the next discriminating check. Do not weaken assertions, add sleeps/retries, or alter production behavior merely to make a symptom disappear. If asked to fix the confirmed cause, switch to `testfly-change` or `testfly-test-authoring`, then use `testfly-verify`.
