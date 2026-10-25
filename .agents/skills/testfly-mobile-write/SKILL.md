---
name: testfly-mobile-write
description: Author consumer-facing native iOS and Android tests with TestFly's incubating io.testfly.mobile API - find MobileLocator selectors on a running simulator, emulator or device through mobilecli, write @MobileFind page objects with iOS and Android selectors side by side, and wire them into TestNG, JUnit 5 or Cucumber. Not for changes to io.testfly.mobile itself (use testfly-mobile).
---

# TestFly native mobile test authoring

Write tests against the public `io.testfly.mobile` API only; never import `io.testfly.mobile.internal.*` in a test. The module is incubating (ADR 0002), so check signatures in `src/main/java/io/testfly/mobile/` before using a method.

## Find locators on a running device

Use `testfly-mobile-locator-finder`: `mobilecli snapshot` for the overview, `mobilecli dump ui` JSON for the ids, `mobilecli io tap @eN` to move between screens, and the selector priority (id → role+name → label → placeholder → text). Never guess an id.

## Page objects

Pages `extends MobilePage`; fields are private; tests talk to page methods, never to locators; a method that changes the screen returns the next page.

```java
public class LoginPage extends MobilePage {
    @MobileFind(ios = "LoginScreen.nameTextField", android = "com.app:id/etUsername")
    @WaitFor                                       // new LoginPage() waits for it
    private MobileLocator username;

    @MobileFind("role=button;name=Sign in")        // same selector on both platforms
    private MobileLocator signIn;

    public HomePage signIn(String user) { username.fill(user); signIn.tap(); return new HomePage(); }
}

new LoginPage().signIn("ada").shouldShowGreeting("ada");
```

- `new X()` sets fields for the running platform, waits for `@WaitFor` fields, then calls `onLoad()` (banners: `tapIfShown(locator)`). Mark one or two elements that prove the screen is shown with `@WaitFor`.
- Never give `@MobileFind` fields an initial value (it overwrites the locator); `onLoad()` runs before subclass field initializers.
- Cucumber: keep page instances in step-class fields; creating a page whose screen is gone times out.
- An element that exists on one platform only gets only that attribute; using it on the other platform fails with a clear message. Leave unknown ids out instead of inventing them, and mark them with `// shortcut: android id unknown, add when known`.
- Device: `device().setOrientation(MobileOrientation.LANDSCAPE)`, `setLocation(lat, lon)` (both undone at close), `startRecording(path)`/`stopRecording()`; automatic videos with `mobile.recordVideo: failures`.
- Actions: `tap`, `doubleTap`, `longPress`, `fill`/`type` (clears), `append`, `clear`, `swipe`. Reads: `getText`, `getValue`, `count`, `isVisible`. Assert with `expect(locator).toBeVisible()`, `toHaveText`, `toBeHidden`, `toBeEnabled`, … inside page `should…` methods. Several matches fail (strict mode) unless `first()`, `last()` or `nth(i)`. Checkboxes/switches: `check()` / `uncheck()`. Several checks in one test: `MobileSoftAssertions soft = MobileAssertions.softly(); … soft.assertAll();`. Hybrid screens: `device().webView().getByRole("button", "Pay").click()`. The Mobile Inspector's Record mode generates a first page object to refine.
- One config for both platforms: `mobile.ios:` / `mobile.android:` blocks, and `@Test` + `@MobileTest(platform = MobilePlatform.IOS)` per test (method wins over class).
- System alerts and Android permission dialogs: set `mobile.alerts: accept|dismiss` in the profile instead of tapping them in tests. Extras on `device()`: clipboard, `pushFile`/`pullFile`, `hideKeyboard()`, `backgroundApp(...)`, `webView()`; on locators `pinchIn/pinchOut`, `dragTo`.
- Outside page objects use `MobileLocator.id(...)`, `accessibilityId(...)`, `xpath(...)`, `iosClassChain(...)`, `iosPredicate(...)`, `byText(...)`, …: they bind to the current test's device, so `static final` fields are safe. Prefer ids over XPath/class chain; use those only for repeated ids.
- Locators may also live in YAML: `MobileLocators.load("mobile-locators/<page>.yml").get("login.username")` with `ios:`/`android:`/`mobile:` blocks or a plain value (same selector strings, checked at load).
- No `Thread.sleep`; waits are `@WaitFor`, `waitForVisible()`/`waitForHidden()` and the auto-waiting actions.

## Engines

| Engine | Test class | Notes |
|---|---|---|
| TestNG | `extends BaseMobileTest` | `screen()`, `device()`, `page(...)`, `expect(...)`, plus API/data/soft-assert support |
| JUnit 5 | `extends junit5.BaseJUnit5MobileTest` | same helpers |
| Cucumber | steps `implements MobileSupport`, scenario tag `@mobile` | features under `src/test/resources/features-mobile/`; see `examples/cucumber/mobile/` |

- Profiles: one YAML per app/platform under `src/test/resources/testfly-<name>.yml` with `mobile.platform`, `mobile.deviceId: ${MOBILE_DEVICE_ID:-}`, `mobile.app.bundleId`, timeouts (`actionMs`, `rpcMs`). No `browser`/`execution`/`timeouts` blocks are needed in a mobile profile.
- Run: `MOBILE_DEVICE_ID=<id> mvn test -Dtestfly.profile=<name> -Dtest=<Class>`.
- Same test on both platforms: one test class, one page object, two profiles (`-Dtestfly.profile=app-ios` / `app-android`).

## Preconditions and setup

- Shared setup such as a login: `@PreCondition("loggedIn")` on the test, `@ConditionProvider("loggedIn")` in a `MobileConditions` subclass written with page objects, registered in `META-INF/services/io.testfly.mobile.MobileConditions` (see `examples/mobile/LoginConditions.java`). It runs before every test, or once per device when `mobile.app.reset: none` keeps the app state; a retry reruns it. Cucumber does not read `@PreCondition`: use a `Background`.
- `@DependsOnApi("https://…/health")` works on mobile tests: the test is skipped when the backend is down.
- App state per test: `mobile.app.reset` (`none`, `relaunch`, `clearData`) in the profile.
- Credentials only from environment variables (`MOBILE_LOGIN_USER`, …); never in code, features or YAML.

## Verify

- Compile: `mvn -q test-compile`. Without a device you can only compile; say so.
- With a device: run the test alone and report `Tests run/Failures/Skipped`. A test that never reached the device (`Skipped`) is not a pass.
- Examples to imitate: `src/test/java/io/testfly/examples/mobile/` (`LoginPage`, `HomePage`, `IosLoginExampleTest`, `AndroidLoginExampleTest` (+ `testfly-mobile-login.yml`), `IosSettingsExampleTest`) and `examples/cucumber/mobile/`.
