---
name: testfly-mobile-write
description: Author consumer-facing native iOS and Android tests with TestFly's incubating io.testfly.mobile API - find MobileLocator selectors on a running simulator, emulator or device through mobilecli, write @MobileFind page objects with iOS and Android selectors side by side, and wire them into TestNG, JUnit 5 or Cucumber. Not for changes to io.testfly.mobile itself (use testfly-mobile).
---

# TestFly native mobile test authoring

Write tests against the public `io.testfly.mobile` API only; never import `io.testfly.mobile.internal.*` in a test. The module is incubating (ADR 0002), so check signatures in `src/main/java/io/testfly/mobile/` before using a method.

## Find locators on a running device

Needs `mobilecli server start` (default `http://localhost:12000`) and `mobilecli agent install --device <id>`. If either is missing, say so and stop; do not guess selectors.

1. `mobilecli devices` gives the device id (iOS: simulator UDID, Android: AVD name or serial).
2. Read the screen tree; the first call on a fresh iOS simulator can take minutes (DeviceKit start), so allow 180 s:
   ```bash
   curl -s --max-time 180 -X POST http://localhost:12000/rpc -H 'Content-Type: application/json' \
     -d '{"jsonrpc":"2.0","id":1,"method":"device.dump.ui","params":{"deviceId":"<id>"}}' > /tmp/ui.json
   ```
   Elements carry `type`, `text`, `label`, `name`, `value`, `identifier` (iOS accessibility id; Android resource-id `pkg:id/name`), `placeholder` and `rect`.
3. Pick one selector per element, in this order, and keep it unique on that screen:
   1. `identifier` → bare string, e.g. `LoginScreen.loginButton` or `com.app:id/btnLogin` (short `btnLogin` also matches on Android).
   2. `role=button;name=Sign in` when there is no id (roles: `MobileRole`, matched on both platforms).
   3. `label=…`, then `placeholder=…`, then `text=…`. Avoid `type=…` unless nothing else is unique.
   Texts depend on the device language; say which language you read.
4. Repeat per screen (navigate with taps through `device.io.tap` or ask the user). For both platforms, read each app; never copy an iOS id to Android or the reverse.
5. Output `@MobileFind` fields grouped by page (below), with elements you could not identify listed separately.

## Page objects

```java
public class LoginPage {
    @MobileFind(ios = "LoginScreen.nameTextField", android = "com.app:id/etUsername")
    public MobileLocator username;

    @MobileFind("role=button;name=Sign in")        // same selector on both platforms
    public MobileLocator signIn;

    public void signIn(String user, String password) { username.fill(user); /* … */ }
}
```

- `page(LoginPage.class)` (from `MobileSupport`) sets every field for the running platform; `MobilePages.init(page, screen)` for pages you construct. Fields: non-static `MobileLocator`; a page needs a no-argument constructor.
- An element that exists on one platform only gets only that attribute; using it on the other platform fails with a clear message. Leave unknown ids out instead of inventing them, and mark them with `// shortcut: android id unknown, add when known`.
- Put waits for screen readiness in page methods (`waitForVisible()`, a `ready()` method), not `Thread.sleep`.
- Actions: `tap`, `doubleTap`, `longPress`, `fill`/`type` (clears), `append`, `clear`, `swipe`. Reads: `getText`, `getValue`, `count`, `isVisible`. Assert with `expect(locator).toBeVisible()`, `toHaveText`, `toBeHidden`, `toBeEnabled`, … Several matches fail (strict mode) unless `first()`, `last()` or `nth(i)`.

## Engines

| Engine | Test class | Notes |
|---|---|---|
| TestNG | `extends BaseMobileTest` | `screen()`, `device()`, `page(...)`, `expect(...)`, plus API/data/soft-assert support |
| JUnit 5 | `extends junit5.BaseJUnit5MobileTest` | same helpers |
| Cucumber | steps `implements MobileSupport`, scenario tag `@mobile` | features under `src/test/resources/features-mobile/`; see `examples/cucumber/mobile/` |

- Profiles: one YAML per app/platform under `src/test/resources/testfly-<name>.yml` with `mobile.platform`, `mobile.deviceId: ${MOBILE_DEVICE_ID:-}`, `mobile.app.bundleId`, timeouts (`actionMs`, `rpcMs`). Config validation still needs placeholder `browser`, `execution` and `timeouts` blocks; copy them from `testfly-mobile-ios.yml`.
- Run: `MOBILE_DEVICE_ID=<id> mvn test -Dtestfly.profile=<name> -Dtest=<Class>`.
- Same test on both platforms: one test class, one page object, two profiles (`-Dtestfly.profile=app-ios` / `app-android`).

## Preconditions and setup

- `@PreCondition`/`@ConditionProvider` cache **browser** cookies and localStorage; they do not apply to native app sessions. For mobile, put shared setup in a page method (`HomePage.ready()`), `@BeforeMethod`/`@BeforeEach`, or a Cucumber `Background`.
- `@DependsOnApi("https://…/health")` works on mobile tests: the test is skipped when the backend is down.
- App state per test: `mobile.app.reset` (`none`, `relaunch`, `clearData`) in the profile.
- Credentials only from environment variables (`MOBILE_LOGIN_USER`, …); never in code, features or YAML.

## Verify

- Compile: `mvn -q test-compile`. Without a device you can only compile; say so.
- With a device: run the test alone and report `Tests run/Failures/Skipped`. A test that never reached the device (`Skipped`) is not a pass.
- Examples to imitate: `src/test/java/io/testfly/examples/mobile/` (`LoginPage`, `HomePage`, `IosLoginExampleTest`, `IosSettingsExampleTest`) and `examples/cucumber/mobile/`.
