---
name: testfly-verify
description: Select and run TestFly's deterministic validation gates after Java, build, public API, SPI, consumer, documentation, or agent-infrastructure changes. Use before handing off implementation work; not for read-only analysis.
---

# TestFly verification

Run `scripts/agent/verify.sh <mode>` from the repository root.

Choose the smallest mode covering the change:

- `agent`: agent files, skill discovery, Kiro JSON/steering, and obsolete-infrastructure checks.
- `code`: Java 21 Maven unit suite.
- `api [baseline-ref]`: binary surface comparison for `@TestFlyApi` types plus the code gate.
- `spi`: registry/discovery tests plus the code gate.
- `consumer [checkout]`: install the current SDK and compile/test a temporary copy of a consumer checkout.
- `docs`: English/Turkish path parity and the Docusaurus production build.
- `full`: agent, code, API, SPI, and docs gates. Consumer verification remains explicit because it needs another checkout.

Do not turn an unavailable external checkout, missing toolchain, or skipped network check into a pass. Record the exact mode and outcome. Prefer focused Maven tests during iteration, then run the selected gate before handoff.
