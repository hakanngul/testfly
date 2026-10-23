---
name: testfly-k6-write
description: Write k6 load, stress, spike and soak test scripts (JavaScript) for the APIs and pages a TestFly project tests, with thresholds, checks, scenarios and environment-driven configuration, stored under load/k6/. Use for k6 scripts; for Java load tests with TestFly's own engine (BaseLoadTest, @LoadTest) use testfly-test-authoring.
---

# k6 load test authoring

k6 scripts live in `load/k6/` and run with the k6 binary (`k6 run load/k6/<name>.js`). They are separate from the Maven build: no Java code, no new Maven dependency. Do not confuse them with `io.testfly.loadtest` (`BaseLoadTest`, `@LoadTest`), which is TestFly's own Java engine.

## Layout

```
load/k6/
  lib/config.js      # BASE_URL, auth headers from __ENV
  lib/auth.js        # login once in setup(), return the token
  <flow>.js          # one script per flow: smoke, load, stress, spike or soak profile via scenarios
  README.md          # how to run, env vars, thresholds
```

## Script rules

- Configuration from the environment only: `const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'`. Secrets (`__ENV.API_TOKEN`, passwords) never in the script, never logged.
- Every script has `options.thresholds` that fail the run: at least `http_req_failed: ['rate<0.01']` and a latency bound such as `http_req_duration: ['p(95)<500']`. Tag requests (`{ tags: { name: 'GET /users/:id' } }`) so URLs with ids group into one metric, and set per-tag thresholds where it matters.
- Use `scenarios` with an explicit executor (`constant-vus`, `ramping-vus`, `constant-arrival-rate`, `ramping-arrival-rate`). Arrival-rate executors for throughput targets; VU executors for user-journey realism. Offer a `smoke` scenario (1 VU, few iterations) selected via `__ENV.PROFILE`.
- `check()` every response for status and one body property; checks do not fail the run by themselves, so add `checks: ['rate>0.99']` to thresholds.
- Login once in `setup()` and pass the token through `data`; per-VU login only when the test is about login.
- Think time: `sleep(1)` style pauses inside user journeys only, never in arrival-rate scenarios.
- Test data: `SharedArray` from a JSON/CSV file under `load/k6/data/`; generated data must be unique per VU and iteration (`__VU`, `__ITER`).
- Write requests (POST/PUT/DELETE) only against a test environment; put a guard that refuses a production-looking `BASE_URL` unless `__ENV.ALLOW_PROD=1`.
- Only built-in k6 modules (`k6`, `k6/http`, `k6/data`, `k6/metrics`, `k6/execution`, `k6/encoding`, `k6/crypto`); no npm packages and no extensions unless the user asks.

## Preconditions

- Mirror the Java tests' preconditions in `setup()`: health check first (`http.get(BASE_URL + '/health')`, abort with `exec.test.abort()` (`import exec from 'k6/execution'`) when it fails, the k6 counterpart of `@DependsOnApi`), then login, then create fixture data. Clean up in `teardown(data)`.
- Reuse endpoints, payloads and auth flow from the project's API tests (`src/test/java/**/api/`, `ApiClient` services) so load and functional tests hit the same contract.

## Verify

- `k6 inspect load/k6/<name>.js` parses the script and prints options; `k6 run --vus 1 --iterations 1 -e PROFILE=smoke …` against a local or mock target proves it runs.
- If k6 is not installed, say so; never claim a run you did not make.
- Report the thresholds and what each scenario models (users, rate, duration).
