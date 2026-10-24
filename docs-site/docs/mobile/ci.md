---
id: ci
title: Mobile Tests in CI
sidebar_position: 11
description: "Run native mobile tests on GitHub Actions with a KVM-accelerated Android emulator or an iOS simulator on macOS, as TestFly's own Mobile Emulator Smoke workflow does."
---

# Mobile Tests in CI

TestFly's own workflow, `.github/workflows/mobile-emulator-smoke.yml` (**Mobile Emulator Smoke**), is a working template. It runs on pushes to `development` that touch the mobile code or examples, and on demand. It never gates the main CI workflow.

| Job | Runner | What it does |
|---|---|---|
| Android emulator smoke (API 30, 34) | `ubuntu-latest` with KVM | boots an emulator with `reactivecircus/android-emulator-runner`, runs `scripts/ci/android-mobile-smoke.sh` |
| iOS simulator smoke | `macos-latest` | boots an iPhone simulator, runs `scripts/ci/ios-mobile-smoke.sh` |

## Steps of a job {#steps-of-a-job}

1. Java 21 and Node; `npm install -g mobilecli@1.0.20` (pin the version you tested with).
2. Build: `mvn -DskipTests install`.
3. Boot the device. Android: enable KVM, then the emulator runner with `disable-animations: true`. iOS: `xcrun simctl boot <udid>` and `xcrun simctl bootstatus <udid> -b`.
4. Start mobilecli and wait for `server.info`. The scripts start it explicitly to keep its log (`target/mobilecli-server.log`); with `autoStartServer` TestFly would start it too.
5. Install the agent: `mobilecli agent install --device <id>`.
6. **iOS only: warm up DeviceKit.** The first screen read on a fresh simulator can take longer than a minute while DeviceKit starts. The script reads the view tree once with a 180 s limit before Maven runs:

   ```bash
   curl -sf --max-time 180 -X POST http://localhost:12000/rpc -H 'Content-Type: application/json' \
     -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"device.dump.ui\",\"params\":{\"deviceId\":\"$udid\"}}"
   ```

7. Run the tests with the device id: `MOBILE_DEVICE_ID=<id> mvn test -Dtestfly.profile=<profile> -Dtest=<Class>`.
8. Upload `target/surefire-reports/`, `target/reports/screenshots/` and the mobilecli log as artifacts, also when the job fails.

## Tips

- Emulators on CI are slow to settle: the example profiles use `actionMs: 30000`, `pollMs: 500`, `rpcMs: 60000`.
- Settings-app texts depend on the device language; CI images are English.
- A job that reports `Skipped` tests did not reach the device; treat it as a failure.
- Device ids: on Android the AVD name (from `mobilecli devices`), on iOS the simulator UDID.
- Secrets for a login test go into the CI secret store and reach the test as environment variables.
