# ADR 0003: Load-test workload model semantics

- Status: Accepted (2026-10-26). Decision 4 is implemented by ADR 0004 (workload API).
- Date: 2026-10-25
- Deciders: TestFly maintainers

## Context

The load-test API describes load with four values: `users`, `rampUp`, `hold` and `cooldown` (`LoadScenario`, `@LoadTest`, `loadtest:` in `testfly.yml`). Javadoc and docs call `users` "concurrent virtual users" and `cooldown` "gradual user removal after hold". The audit (`docs/audits/gatling-2026-10/`, BUG-07, V11, V12) measured what the two engines actually do.

| Value | Gatling engine (`TestFlyGatlingSimulation`) | JDK engine (`JdkLoadEngine`) |
|---|---|---|
| `users` | `rampUsers(users).during(rampUp)` — an open model: `users` is the number of arrivals | `users` threads, each started `rampUp/(users-1)` after the previous one |
| `rampUp` | arrival window | start offset window |
| `hold` | `during(hold)` loop per user, counted from each user's own arrival | loop per thread, counted from each thread's own start |
| `cooldown` | ignored | ignored (only extends the safety timeout) |

Both engines run the same shape: users arrive evenly over `rampUp`, and each one loops for `hold` from its own start. As a result:

- total run time is about `rampUp + hold`, not `rampUp + hold + cooldown`;
- peak concurrency is `users` only when `rampUp <= hold`. With `users=10, rampUp=6s, hold=2s` the server saw a peak of 5 (Gatling) and 4 (JDK);
- `cooldown` has no effect.

Gatling separates two models that must not be mixed in one injection profile ([Injection](https://docs.gatling.io/concepts/injection/)):

- **Open:** you control the arrival rate (`rampUsers`, `constantUsersPerSec`, …). Concurrency is an outcome.
- **Closed:** you control concurrency (`constantConcurrentUsers`, `rampConcurrentUsers`). A new user starts only when another one finishes.

## Decision

1. **No silent semantic change.** `users/rampUp/hold` keep today's behaviour in both engines. We do **not** switch the existing API to `rampConcurrentUsers` under the same names. Tests written against today's numbers (request totals, throughput, duration) would change meaning without any code change on the consumer side.
2. **Document the real semantics now.** Docs and Javadoc describe `users` as "number of virtual users started over `rampUp`; each runs the scenario in a loop for `hold`". They state that peak concurrency equals `users` only when `rampUp <= hold`.
3. **Make `cooldown` visible, not effective.** In this release `cooldown` stays accepted for compatibility. When it is greater than zero, the runner logs one warning per JVM that it is not applied. Implementing ramp-down is deferred to decision 4, because a closed-model ramp-down cannot be retrofitted onto the current open-model profile.
4. **Introduce explicit models as new, opt-in API (later minor release).** The design direction is:
   - `LoadScenario.workload(Workload)` with `Workload.open(...)` steps (`atOnce`, `ramp`, `constantRate`, `rampRate`, `nothingFor`) and `Workload.closed(...)` steps (`constantConcurrent`, `rampConcurrent`);
   - a validator that rejects mixing open and closed steps;
   - each engine declares which steps it supports, so an unsupported step fails fast and is never ignored;
   - `cooldown` gets a defined meaning only inside `Workload.closed(...)` (a `rampConcurrent(n → 0)` step).

   The existing `users/rampUp/hold` values stay as shorthand for today's shape and are not deprecated until the new API has shipped one minor release.
5. **Versioning.** Decisions 1–3 are documentation and logging only and are compatible within 1.x. Decision 4 is additive (new types and methods with `@TestFlyApi(since = ...)`). Changing the meaning of `users` itself would require a major version.

## Consequences

- Consumers keep identical load shapes after upgrading. The only new behaviour is the cooldown warning.
- Constant concurrency and arrival-rate control are available through `LoadScenario.workload(...)` (decision 4, ADR 0004; Gatling engine only).
- The audit items W-02, W-03, W-05 and W-06 stay open and are tracked as P1 work, not as P0 reliability fixes.

## Alternatives considered

- **Map `users/rampUp/hold/cooldown` to a closed model (`rampConcurrentUsers` → `constantConcurrentUsers` → ramp-down).** This would match the documentation wording, but it silently changes request volume and duration for every existing test. Rejected for 1.x.
- **Remove `cooldown`.** It is a public `@TestFlyApi` method and annotation element. Removal is a breaking change. Rejected.
