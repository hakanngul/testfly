# ADR 0004: TestFly-owned load metrics, workload models, simulations and SLAs

- Status: Proposed
- Date: 2026-10-25
- Deciders: TestFly maintainers
- Related: ADR 0003 (workload model semantics), `docs/audits/gatling-2026-10/`

## Context

After the P0 reliability round, TestFly reads Gatling latency and request counts from the HTML report's `js/stats.js`, and reads status codes from its own sidecar (`testfly-status.json`). Three facts force a change:

1. **Gatling 3.14+ no longer writes `js/stats.js`.** In v3.13.5, `ReportsGenerator` runs `StatsReportGenerator` (`ChartsFiles.statsJsFile`). Both are gone in v3.14.0, and in v3.16.0 `js/all_sessions.js` is gone too. The upgrade guides do not mention this. TestFly cannot upgrade past 3.13.5 while it depends on that file.
2. **Gatling writes no assertion results file.** Assertion results exist only in `index.html` and as console lines (`"<message> : <bool> (actual : <v>)"`). Gatling exits with code 2 when an assertion fails (verified with a 3.13.5 run). Neither the console nor the HTML report is a supported contract.
3. **`simulation.log` is internal and binary** (Gatling maintainer statement; binary since 3.13.0).

The enterprise gaps in the audit (workload models, multiple scenarios, native SLAs, p90 on Gatling, regression comparison) all need a metrics source that TestFly controls.

### Spike evidence (Gatling 3.13.5, local server, 2026-10-25)

**Status and latency per request name.** A protocol-level `transformResponse` counted statuses and latency (`endTimestamp - startTimestamp`). The counts matched both the server-observed counts and `stats.js` exactly, including responses whose checks failed (4818/4818).

**Per-request OK/KO.** Each request is wrapped in three steps:

- before it: `exec(session -> { attempt++; return session.markAsSucceeded(); })`;
- the request itself;
- after it: `exec(session -> { (session.isFailed() ? ko : ok)++; return session.markAsSucceeded(); })`.

With `during(d, false)` (no exit in mid-iteration), the counts matched `stats.js` exactly for OK, failed-check and connection-refused requests: 5092 each, with `attempt == ok + ko`. With the default `during(d)` (`exitASAP = true`), the last iteration was cut off after a request but before its marker, and up to 5 outcomes per run were lost.

**Requests that cannot be built.** A request that fails to build (for example a missing `#{var}`) does not appear in `stats.js`. The markers count it as KO with no response.

**Two populations.** One closed population (`constantConcurrentUsers(5)`, then `rampConcurrentUsers(5).to(0)`) and one open population (`constantUsersPerSec(10)` for 4s) ran in one simulation:

- with request names prefixed per scenario, the per-scenario counts matched the server (79 and 40);
- the server saw 5 concurrent requests, then a ramp to 0;
- the open population produced exactly 40 arrivals.

**Prefix separator.** The non-ASCII `›` separator was printed as `?` in Gatling's console. Prefixes must be ASCII.

## Decision

1. **TestFly owns the Gatling metrics.** The forked simulation records the following per request name:
   - attempts, OK and KO, from the session markers above;
   - a millisecond latency histogram, from `transformResponse`;
   - status codes, from `transformResponse`, which already exists.

   It writes them to one sidecar, `testfly-metrics.json` (versioned), in `after()`.

   `GatlingResultsParser` builds `LoadTestMetrics` from this sidecar only:
   - totals are OK + KO;
   - percentiles p50/p75/p90/p95/p99 are computed from the histogram over requests that received a response;
   - throughput is total requests divided by the run duration measured by the simulation (`before()` to `after()`);
   - no-response requests appear under status `-1`;
   - `attempt − (ok + ko) > 0` marks the run as partial, logs a WARN and fails any SLA.

   `js/stats.js` is no longer read at runtime. While Gatling 3.13.5 is pinned, it is used only in tests, as a cross-check.
2. **No mid-iteration exit.** The legacy `users/rampUp/hold` loop becomes `during(hold, false)`. The JDK engine already finishes the iteration in progress, so this aligns the engines. A run can now last up to one iteration longer than `rampUp + hold`. Workload-model scenarios run each user once and have no loop.
3. **Workload models (ADR 0003, decision 4).**
   - New `Workload.open(OpenStep...)` and `Workload.closed(ClosedStep...)`, with distinct step types so mixing them cannot compile.
   - `LoadScenario.workload(...)` cannot be combined with `users/rampUp/hold/cooldown`.
   - The feature is Gatling-only. The JDK engine fails before any request is sent.
4. **Simulations.** `LoadSimulation` runs several `LoadScenario`s, each with its own workload, as one Gatling simulation.
   - Request names are prefixed `"<scenario> :: <step>"` (ASCII).
   - Per-scenario and global metrics are both computed from the sidecar, including per-scenario percentiles.
   - The feature is Gatling-only.
5. **SLAs.**
   - `LoadSla` is evaluated by TestFly from `LoadTestMetrics`, the same way on both engines, so the verdict never depends on parsing Gatling output.
   - On Gatling, the same SLA is also registered as native `assertions(...)`, so it appears in Gatling's HTML report.
   - When native assertions were registered, exit code 2 means "Gatling assertions failed" and is not a crash; the run is parsed as usual. TestFly's verdict is authoritative. If it disagrees with the exit code on the natively registered rules, a WARN names both estimators.
   - Rules with no native counterpart (a scenario's global rules inside a multi-scenario simulation, unknown steps) are evaluated by TestFly only.
   - A rule fails with reason "metrics are partial" when the run is partial, "step not found" when its step is absent, and "not measured" when the value is NaN, in that order of precedence.
   - `run()` records the metrics, including the SLA results, before it throws the `AssertionError`, so reports always contain the run.
6. **Baselines.** `LoadBaseline.save/load` (versioned JSON) and `LoadTestAssert.assertNoRegression(baseline, Tolerance)`. Metrics that are unmeasured on either side are reported as "not compared", never as a pass.
7. **Gatling upgrade.** Once decision 1 is in place, upgrading to 3.16.x no longer depends on report internals. The upgrade is a separate change, verified by the same regression suites.

## Consequences

- **p90 becomes measured on Gatling.** It is no longer `NaN`. Percentiles are now computed exactly by TestFly from a millisecond histogram (linear interpolation, the same method as the JDK engine). Gatling's own HTML report estimates percentiles with t-digest (`AVLTreeDigest(100)` in 3.13.5), and that estimate depends on the order samples arrive. On small samples, high percentiles can differ visibly: one run's p99 was 106 ms in TestFly, while t-digest gave anywhere from 103 to 205 ms depending on insertion order.
- **Different request totals.** TestFly counts requests that fail to build as KO; Gatling's report omits them.
- **Larger child JVM sidecar.** Histograms are bounded by the number of distinct milliseconds per request name, not by the number of requests.
- **New public types** (`Workload`, `OpenStep`, `ClosedStep`, `LoadSimulation`, `LoadSimulationResult`, `LoadSla`, `StepSla`, `SlaRule`, `SlaResult`, `LoadBaseline`, `Tolerance`) are additive, with `@TestFlyApi(since = "1.1.0")`. `LoadTestMetrics` (`slaResults`) and `LoadTestMetrics.StepMetrics` (`p99LatencyMs`) gain trailing components; their earlier constructors are kept.

## Alternatives considered

- **Parse `simulation.log`, `index.html` or the console output.** All three are internal or presentation formats with no compatibility promise. Rejected.
- **Stay on 3.13.5 forever.** That locks out Gatling fixes and still gives no p90 or SLA file. Rejected (user decision, 2026-10-25).
- **Write a custom Gatling DataWriter.** It is not exposed in the Java DSL and is internal API. Rejected.
