# ADR 0002: Native mobile automation through mobilecli

- Status: Proposed; implementation gated by licensing and device validation
- Date: 2026-10-14
- Revised: 2026-10-18
- Deciders: TestFly maintainers

## Context and evidence

TestFly currently supports browser device emulation, not native Android or iOS automation. This proposal keeps a separate `io.testfly.mobile` domain backed by a Java client for user-installed [mobilecli](https://github.com/mobile-next/mobilecli), inspired by [Mobilewright](https://mobilewright.dev). It describes planned behavior, not shipped support.

### Verified TestFly constraints

Source reviewed at TestFly revision `2fbcfad` establishes these boundaries:

| Source | Planning consequence |
|---|---|
| [`BaseApiTest`](../../src/main/java/io/testfly/test/BaseApiTest.java), [`BaseJUnit5ApiTest`](../../src/main/java/io/testfly/junit5/BaseJUnit5ApiTest.java), `loadtest/BaseLoadTest` | Mobile can be a peer domain sharing backend, data, context, steps, and soft assertions. |
| [`Locator`](../../src/main/java/io/testfly/locator/Locator.java), [`DriverProvider`](../../src/main/java/io/testfly/driver/DriverProvider.java), `WaitEngine` | These are Selenium-specific; mobile needs independent locator/wait/driver contracts. |
| [`TestExecutionListener`](../../src/main/java/io/testfly/listeners/TestExecutionListener.java) | Allocation occurs in `onTestStart`; `beforeInvocation` is a no-op. Adding mobile there alone does not establish availability during user `@BeforeMethod`. |
| [`TestFlyExtension`](../../src/main/java/io/testfly/junit5/TestFlyExtension.java) | Its `beforeEach`/`afterEach` lifecycle is useful, but its retry interceptor recreates WebDriver and reinvokes only the test method. Mobile must not inherit that retry path. |
| [`CucumberHooks`](../../src/main/java/io/testfly/cucumber/CucumberHooks.java) | Current cleanup precedes conventional user after-hooks. The TestNG listener skips Cucumber `runScenario`; preserve one lifecycle owner. |
| [`StepLogger.stepWithScreenshot`](../../src/main/java/io/testfly/steps/StepLogger.java) | Records a step artifact. Test-level screenshots and immediate engine/report attachments require additional integration. |
| [`TestFlyConfig`](../../src/main/java/io/testfly/config/TestFlyConfig.java), [`ConfigurationLoader`](../../src/main/java/io/testfly/config/ConfigurationLoader.java) | Stable JavaBean configuration uses SnakeYAML, placeholder resolution, and unknown-key warnings. |
| [`TestFlyApi`](../../src/main/java/io/testfly/api/TestFlyApi.java), [`check-public-api.sh`](../../scripts/agent/check-public-api.sh) | Stable contracts cannot be bypassed by leaving a getter's return type unannotated. The gate compares public members of annotated source-file types. |
| [`pom.xml`](../../pom.xml) | Jackson is available; Java 21 provides HTTP. JUnit/Cucumber dependencies remain optional. |

`@NoBrowser` is not inherited. Mobile detection must use assignability or explicit annotation lookup rather than relying on an annotation on a base class.

### Upstream evidence and unresolved claims

The original ADR records a **device-free observation against mobilecli 1.0.20**: `server.info` returned `{name, version}`, `devices.list` returned `{devices: [...]}`, an unknown device returned `-32000`, and an unknown method returned `-32601`. Those observations were not rerun for this revision and have no raw fixtures attached here. They do not establish a minimum supported version or validate device operations.

The original proposal describes JSON-RPC 2.0 over `POST /rpc`, view-tree dumps, coordinate input, Android adb/UiAutomation, and an iOS XCUITest agent. Command names, schemas, platform capabilities, and error codes remain validation inputs until checked against a pinned upstream revision and actual devices. Published specifications and Mobilewright usage may differ from the daemon. No device latency benchmark or upstream confirmation is claimed here.

The original ADR identifies mobilecli as FSL-1.1-ALv2 and Mobilewright as Apache-2.0. Verify the exact revision's license, competing-use restriction, change date, and attribution requirements. No redistribution is an architectural choice; it does **not** prove that TestFly's intended SDK, CI, or commercial use is permitted.

## Decision

Proceed conditionally with a native mobile peer domain and replaceable `MobileDriver` adapter. Prove a narrow Android workflow before integrating test engines. Android and iOS support graduate independently.

### D1. Scope and adapter boundary

- Connect to an existing, unmodified server. TestFly does not download, start, patch, fork, bundle, or redistribute mobilecli or its agents.
- Keep runtime types under `io.testfly.mobile`, implementation helpers under `.internal`, and the adapter seam under `.spi`. Preserve Selenium locator, waiting, and driver contracts.
- `MobileDriver` exposes platform-neutral semantics for discovery, immutable snapshots, input, screenshots, and app lifecycle. `MobileCliDriver` owns protocol/platform details. Add operations when needed and verified; unsupported capabilities fail before mutation.
- The first slice targets **one preinstalled app on one dedicated Android emulator**, through explicit `Mobile.connect(options)`: text/test-id queries, tap, replacement fill, visibility/text assertions, screenshot, and bounded close. Installation, roles, additional gestures, and engines follow that proof.
- Discovery calls have no selected-device parameter. Inject device ID only into verified device-scoped calls. Check server identity/version at connection and publish a tested version/capability matrix; a minimum-version comparison alone does not validate later versions.

```text
explicit Mobile.connect(options)       later: runner-owned MobileSupport
                    \                  /
                      MobileDevice
                screen / locator / assertions
                            |
                      MobileDriver SPI
                            |
                      MobileCliDriver
                   JDK HTTP + Jackson
                            |
                  user-installed mobilecli
                            |
                  dedicated Android / iOS
```

Provide a testable programmatic driver-injection path. Defer provider discovery and additional adapters until needed.

### D2. Ownership, reservation, and cleanup

An explicit connection is `AutoCloseable`, owned by its creating thread. Managed sessions add a thread-confined current-session holder and invocation/attempt identity. Reject cross-thread use and nested ownership of the same device; never silently replace a session.

Connection order is mandatory:

1. Validate options/endpoint; perform bounded server discovery and deterministic device selection.
2. Acquire a JVM-wide exclusive reservation **before install, reset, launch, input, or any device-state mutation**, keyed by `(server identity, device ID)`.
3. Under the reservation, recheck availability/capabilities, prepare the app, then publish the active session.
4. On every exit, close partial resources and remove current-session bindings. Capture failure evidence before app termination and release. Close is idempotent and bounded; preserve the primary failure and report cleanup failures separately.

Initially, server identity is the normalized endpoint, including base path/effective port. Daemon name/version is not unique. All workers sharing a daemon must use the same endpoint; aliases/proxies for that daemon are unsupported until a trustworthy identity mechanism exists. Acquisition is interruptible and bounded, with owner/device diagnostics. Separate JVMs and other automation tools require externally exclusive devices; do not promise concurrent Appium access.

Failed acquisition performs no mutation. Release ownership in `finally` after partial-open or close failures. If a timed-out mutation or failed cleanup leaves device quiescence unknown, retain an unavailable-device marker so waiting workers fail with an explicit recovery instruction rather than reusing it. Recovery requires out-of-band reconciliation. Shutdown cleanup is best effort, not the normal lifecycle.

### D3. App state and platform capabilities

Default managed policy: `relaunch`. Each managed attempt applies its policy anew:

| Policy | Before user setup | After user teardown and failure capture |
|---|---|---|
| `none` | No implicit launch, termination, or data change; app ID optional. | No app mutation; release session. |
| `relaunch` | Require app ID; terminate existing instance, then launch; preserve data. | Terminate that app. |
| `clearData` | Require app ID and verified reset capability; terminate, clear data, launch. | Terminate that app. |

Installation is a later explicit option, independent of reset, performed under the lease before preparation. Verify artifact/signing requirements and whether paths belong to the client or daemon before exposing it. Never silently substitute uninstall/reinstall or relaunch for clear-data.

Android, iOS simulator, and iOS real-device reset are separate capabilities. Reject iOS `clearData` until demonstrated for the exact target. Simulator success does not establish physical-device provisioning, installation, or reset support.

### D4. Tree semantics and actions

- Normalize verified dumps into immutable trees preserving ancestry, attributes, bounds, and screen metadata. Missing state remains unknown; do not invent visibility/enabled values. Validate Android resource-ID and iOS identifier mappings with fixtures.
- Evaluate immutable locator queries against one fresh snapshot per poll. `within(container)` means **UI-hierarchy descendants**, excluding the container. Bounds containment/overlap never establishes ancestry; indexing must preserve parent/child relationships.
- Use deterministic tree order for `first`/`last`/zero-based `nth`. Actions require one target unless explicitly narrowed. Duplicate targets fail strictness; no match polls until timeout. Document/test text matching, absent attributes, and accessible-name rules before publishing conveniences.
- Tap requires verified visibility/enabled state, usable bounds, and stability across successive snapshots. Compare target identity/attributes as well as bounds. Re-resolve immediately before dispatch; changed target/geometry restarts polling within the same deadline. Coordinate input cannot atomically bind a snapshot to a tap, so residual races remain documented.
- Establish the transform between tree bounds, input coordinates, and screenshot pixels: origin, units, scale/density, orientation, viewport, and system insets. Do not assume Android pixels equal iOS points. Reject unverified transforms; test rotation and edge targets before claiming orientation support.
- `fill(value)` replaces existing contents. Verify focus, clearing, Unicode, empty strings, and readback on the device; never silently append. Unknown clear/key methods cannot underpin claimed fill support. Redact password/input values from diagnostics.

Role mappings, labels, placeholder queries, scrolling, and gestures follow the core proof. Port only verified logic with required attribution.

### D5. Transport, deadlines, and security

Use JDK `HttpClient` and Jackson. Verify response IDs/envelopes, HTTP failures, malformed bodies, and response-size limits. A small `MobileException` hierarchy separates selection, compatibility/capability, transport/RPC, strictness, and timeout failures. Preserve raw RPC code and sanitized data: do not globally classify `-32000` as device-not-found or assume `-32010`/`-32050` are universal.

Use monotonic deadlines for connection, acquisition, actions/assertions, and cleanup. Requests and sleeps are capped by the enclosing remaining budget; `rpcMs` is a ceiling, not a fresh allowance for each substep. A 5-second action must not wait on a 30-second request timeout. Preserve interruption. Client cancellation cannot guarantee cancellation on the device; ambiguous mutations trigger D2's unavailable-device policy.

Retry verified read-only calls at most once on transient I/O failure within the same deadline. Never automatically retry tap, text, launch, install, reset, or other mutations. Diagnostics include selector, selected device, server version, elapsed budget, and sanitized last matches, without dumping a sensitive tree by default.

Device control is privileged. Default to literal loopback/validated localhost endpoints. Remote endpoints require opt-in, HTTPS, and a documented authenticating proxy or secure tunnel; do not assume native daemon authentication. Reject embedded URL credentials, disable redirects, validate endpoint construction, and redact headers. Local HTTP remains usable behind an SSH tunnel.

### D6. Configuration and stability

The initial slice accepts incubating `MobileOptions` through `Mobile.connect(options)` and changes no stable configuration type. Mobile operational APIs/SPI remain unannotated with `@TestFlyApi` and explicitly incubating. Do not exclude mobile from, or weaken, the public API gate.

Before YAML integration, commit to stable `TestFlyConfig.getMobile`/`setMobile` signatures and a **stable configuration DTO**, including exposed nested value types. It must not expose incubating runtime classes. Mark that commitment deliberately and cover nested DTO compatibility; current source-file discovery is not a complete nested-type audit. Configuration can be stable while operational APIs incubate.

Proposed later configuration, **not currently supported**:

```yaml
mobile:
  serverUrl: ${MOBILECLI_URL:-http://localhost:12000}
  platform: android            # android | ios; required when mobile runs
  deviceId: ""                # exact ID; takes precedence over deviceName
  deviceName: ""              # full regex match
  app:
    bundleId: com.example.app  # Android package / iOS bundle identifier
    reset: relaunch           # none | relaunch | clearData
  timeouts:
    connectMs: 60000           # whole discovery/acquisition/preparation budget
    acquireMs: 30000           # capped by remaining connectMs
    actionMs: 5000
    pollMs: 100
    rpcMs: 30000
    cleanupMs: 10000           # bounded artifact capture and cleanup
  allowRemote: false
  headers: {}                  # secrets via placeholders; never diagnostics
  screenshotOnFailure: true
```

Select online devices of the requested platform by exact ID, otherwise full name-regex match, otherwise the sole candidate. Zero/multiple matches fail with sanitized candidates; never select arbitrarily. Recheck after reservation. Initial support has no pooling.

One authoritative definition supplies options/YAML defaults. Preserve existing loading/override behavior, placeholder resolution, and unknown-key warnings; do not assume generic environment/system-property overrides. Test missing, blank, malformed, and invalid values, regexes, and positive timeout constraints. Absent mobile configuration must cause no mobile discovery/allocation/remote calls for non-mobile execution. Add `TestFlyDefaults` support only with explicit precedence coverage.

### D7. Engine integration after Android proof

| Engine | Planned entry | Required ordering/registration |
|---|---|---|
| TestNG | `mobile.BaseMobileTest`, `MobileSupport`, later `@MobileTest` | Register listeners as `BaseApiTest` does; document registration for annotation-only use. Prove a callback scheme opening before user `@BeforeMethod` and closing after `@AfterMethod`, including configuration failures. Adding to `onTestStart` alone is insufficient. |
| JUnit 5 | `mobile.junit5.BaseJUnit5MobileTest`, later annotation support | Register the extension explicitly. Open before user `@BeforeEach`; close after `@AfterEach`, including failures/aborts. |
| Cucumber | `@mobile` with mobile support | Glue/hooks own the scenario; the TestNG wrapper must not allocate again. Separate early failure capture from final cleanup so documented user after-hooks retain device access and their failures are captured. |

Share appropriate `StepSupport`, `TestDataSupport`, `ContextSupport`, `ApiSupport`, `DbSupport`, and `EmailSupport`. Reuse the shared soft-assertion collector through mobile helpers rather than mixing in the current `SoftAssertSupport`, which also exposes Selenium locator/page overloads. Inherit no web support interfaces. Base/annotation/tag detection skips browser allocation and rejects conflicting domain declarations.

Prove one owner per attempt across success, failure, disabled/quarantined tests, skip/abort after allocation, open/setup/teardown failures, interruption, and retry. Skip-before-allocation allocates nothing. A supported retry must rerun preparation and the complete user lifecycle with a fresh lease; otherwise reject it. **Initially reject TestFly-managed mobile retries** rather than enter web retry behavior. External full-invocation retries require separate executable evidence.

Capture a failure screenshot while the app/session and report item remain available. Reuse its bytes for the step, test-level `ExecutionMetrics` artifact, immediate ReportPortal attachment, and Cucumber attachment as applicable; verify each path. Screenshot failure must not replace the primary failure or prevent release. Route shared `step(..., true)` mobile screenshots through the device. Audit AI analysis, console, tracing, parameter injection, and other web helpers so mobile neither accesses retained web sessions nor creates/recreates WebDriver. Include a mobile failure on a thread previously used by a web test.

### D8. Licensing, alternatives, and deferred scope

Before merging integration, record exact license/revision evidence, intended SDK/CI/commercial use, obligations, and the maintainer conclusion. Add `NOTICE`/license attribution for copied Mobilewright logic. If permission remains unresolved or unfavorable, stop mobilecli delivery and reassess the adapter.

| Option | Assessment |
|---|---|
| Native Java adapter for unmodified mobilecli | Conditional choice: small runtime surface; protocol maturity, device behavior, and permitted use remain gates. |
| Appium Java adapter | Credible alternative with broad ecosystem/cloud support, additional dependencies/server setup; can implement the separate mobile seam. No unsupported performance comparison is claimed. |
| Fork/bundle mobilecli | Rejected for agent maintenance and distribution/licensing scope; requires a new decision if reconsidered. |
| Node/Mobilewright subprocess | Rejected for an additional runtime and cross-process lifecycle, despite reusing upstream behavior. |

Defer pools/cross-JVM allocation, installation, deep links, geolocation, WebViews, WebSocket streaming, screencast, and a separate Maven artifact. Revisit from measured needs after core contracts hold. iOS validation is its own stage.

## Delivery plan and exit criteria

Unchecked items are pending. Fake-server success proves client behavior, never native automation. Each stage must pass before the next integration stage.

### Implementation boundary

The first code increment contains internal foundations under `io.testfly.mobile.internal`: bounded JSON-RPC HTTP transport, process-local device reservations/quarantine, and immutable normalized trees with hierarchical queries. They have deterministic local-server/unit coverage and introduce no stable API or configuration changes.

This increment does not complete Stage 0 or Stage 1. It contains no mobilecli wire-to-tree adapter, `Mobile.connect` facade, device actions, runner integration, or supported-device claim. Pinned protocol/license evidence and Android emulator validation remain prerequisites for integrating and delivering native automation.

The transport connects directly to the configured endpoint; JVM forward-proxy settings are not supported. RPC exceptions retain the numeric code but omit server message/data from diagnostics. Sanitized structured device-error classification is deferred to the adapter work, where pinned fixtures can establish reliable mappings.

### Stage 0 — Establish a permitted, reproducible target

- [ ] Complete D8's revision-specific licensing/provenance review.
- [ ] Pin mobilecli release/commit/spec references; record installation commands, host/emulator OS, Android API level, app fixture version, and agent version where available. Minimum supported version stays unset until validated.
- [ ] Capture sanitized emulator request/response fixtures for discovery, device info, tree, tap, text/clear, screenshot, terminate/launch, and representative errors. Establish coordinate units and working replacement fill; record capability gaps.

**Exit:** maintainers can reproduce a permitted app interaction on the pinned target, with fixtures and an explicit proceed/alternative-adapter decision.

### Stage 1 — Explicit Android vertical slice

- [ ] Implement the minimal driver/SPI, options, selection/leases, bounded transport/cleanup, core queries, tap/fill/assertions, screenshot, and `Mobile.connect(options)`.
- [ ] Unit/fake-server checks cover envelopes/errors/version mismatch, deadlines/cancellation, no mutation retries, hierarchical scoping with overlapping siblings, duplicate targets, coordinate changes, concurrent acquisition, and partial-failure release/unavailable-device behavior.
- [ ] From Java, replace prefilled text including Unicode/empty input, tap, assert resulting text/visibility, save a valid screenshot, and close. Repeat **at least 10 emulator runs without manual recovery**; retain results/timings. Deliberately fail an assertion and verify screenshot plus subsequent safe reacquisition.
- [ ] Document the explicit API, pinned setup, limitations, and fixture provenance. Run `scripts/agent/verify.sh code`, `api`, `spi`, and `docs`; run consumer verification with a representative checkout or explicitly record that gap.

**Exit:** focused checks and real emulator evidence pass. No runner base classes or supported-iOS claim is required; fake-only completion fails this gate.

### Stage 2 — One engine at a time

- [ ] Freeze the configuration DTO; wire/test loading, defaults, validation, precedence, and compatibility.
- [ ] Deliver TestNG, then JUnit 5, then Cucumber. Each engine-driven suite proves D7 lifecycle cases, artifact timing, zero WebDriver side effects, no double ownership, bounded release, and fail-fast unsupported retries.
- [ ] Run the app smoke through each engine, including device access in user setup/teardown and backend assertions via shared support.
- [ ] Add runnable examples, English/Turkish product docs, configuration samples, and attribution. Run `code`, `api`, `spi`, `docs`, and relevant consumer checks after each engine change.

**Exit per engine:** callback/report tests and emulator smoke pass; unfinished engines remain unsupported. Store device evidence in an optional CI job or reproducible local procedure. Missing shared CI hardware does not waive the device gate.

### Stage 3 — Validate platforms and stabilize selectively

- [ ] Validate Android physical devices separately. Validate iOS simulator on macOS/Xcode, then physical iOS with documented signing/provisioning. Repeat core actions, coordinate/screenshot checks, app-state policies, and failure cleanup per claimed target.
- [ ] Publish the tested version/platform/capability matrix, including unsupported reset/installation cases. Validate upstream upgrades before extending it.
- [ ] After at least one incubating minor release and emulator plus physical-device evidence for the claimed platforms, review operational APIs/SPI individually for `@TestFlyApi` promotion; run compatibility/consumer gates.

**Exit:** support/stability claims match execution evidence. Gestures, pooling, and streaming need separate measurable acceptance criteria.
