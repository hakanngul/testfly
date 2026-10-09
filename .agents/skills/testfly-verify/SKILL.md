---
name: testfly-verify
description: Select and run TestFly validation gates for Java changes, public API compatibility, SPI discovery, downstream consumers, documentation, agent infrastructure, and release readiness. Use before implementation handoff or when asked whether a change or release is ready; not for diagnosing an unexplained test failure.
---

# TestFly verification

Run `scripts/agent/verify.sh <mode>` from the repository root. Start narrow during iteration; run every gate implied by the changed contract before handoff.

## Modes

- `agent`: shared skills, Kiro adapters, discovery metadata, and obsolete-infrastructure checks.
- `code`: Java 21 Maven unit suite.
- `api [baseline-ref]`: compiled `@TestFlyApi` surface comparison against a tag/ref, then `code`.
- `spi`: registry and discovery tests, then `code`.
- `consumer [checkout]`: install the current SDK, compile a clean temporary consumer copy, and run configured downstream smoke tests.
- `docs`: English/Turkish path parity and both Docusaurus production builds.
- `release [baseline-ref] [consumer-checkout]`: release metadata/workflow checks plus agent, code, API, SPI, docs, and consumer gates. Consumer availability is mandatory; `TESTFLY_CONSUMER_DIR` is also accepted.
- `full [baseline-ref]`: agent, code, API, SPI, and docs. Consumer remains explicit because it needs another checkout.

## Interpret results

- A green API comparison covers compiled annotated signatures and supertypes; it does not prove behavioral, configuration, serialization, or downstream source compatibility.
- A consumer pass covers compilation plus the selected smoke tests. Record `TESTFLY_CONSUMER_TESTS` when overriding the default API-only selectors.
- Missing checkouts, credentials, browsers, services, toolchains, or network access are unavailable checks—not passes.
- Release readiness also requires a SemVer project version, matching changelog/release metadata, clean diff syntax, and the actual release workflow. Publishing and credentialed external integrations remain CI/operator responsibilities.

Report the exact commands/modes, baseline, consumer source/selectors, pass/fail status, and every unavailable gate.
