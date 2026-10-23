---
id: configuration
title: Mobile Configuration
sidebar_position: 3
description: "The mobile: block of testfly.yml: device selection, device pool for parallel runs, app reset, mobilecli server auto-start, timeouts, profiles and security."
---

# Mobile Configuration

Nothing mobile loads unless the active configuration has a `mobile:` section. Every key except `platform` has a default.

```yaml
mobile:
  serverUrl: ${MOBILECLI_URL:-http://localhost:12000}
  autoStartServer: true        # start mobilecli when the loopback server does not answer
  mobilecliPath: mobilecli     # full path when mobilecli is not on PATH
  platform: android            # android | ios (required)
  deviceId: ""                 # exact id; wins over deviceName
  deviceName: ""               # regex on the device name
  devices: []                  # device pool: ids handed out one per test
  pool: false                  # pool every online device matching deviceName
  app:
    bundleId: com.example.app  # Android package / iOS bundle id
    install: ""                # optional .apk/.ipa/.app path, installed when the device opens
    reset: relaunch            # none | relaunch | clearData
  timeouts:
    actionMs: 5000             # auto-wait budget of one action or assertion
    pollMs: 100                # pause between two reads of the screen
    rpcMs: 30000               # upper bound of one request to mobilecli
    reserveMs: 300000          # wait for a device another test in this JVM holds
    serverStartMs: 20000       # wait for an auto-started server to answer
  allowRemote: false
  headers: {}                  # e.g. Authorization: Bearer ${MOBILECLI_TOKEN}
  screenshotOnFailure: true
```

| Key | Default | Description |
|---|---|---|
| `serverUrl` | `http://localhost:12000` | mobilecli server; TestFly calls `<serverUrl>/rpc`. |
| `autoStartServer` | `true` | Start `mobilecli server start` when a loopback `serverUrl` does not answer. |
| `mobilecliPath` | `mobilecli` | Command used by `autoStartServer`. |
| `platform` | — (required) | `android` or `ios`. |
| `deviceId` | `""` | Exact device id (AVD name, serial or simulator UDID). |
| `deviceName` | `""` | Regex on the device name, used when `deviceId` is empty. |
| `devices` | `[]` | Device pool: ids handed out one per test. |
| `pool` | `false` | Pool every online device of the platform that matches `deviceName`. |
| `app.bundleId` | `""` | App under test: Android package or iOS bundle id. |
| `app.install` | `""` | `.apk`, `.ipa` or `.app` path installed when the device opens. |
| `app.reset` | `relaunch` | `none`, `relaunch` or `clearData`, applied before each test. |
| `timeouts.actionMs` | `5000` | Auto-wait budget of one action, read, assertion or `@WaitFor`. |
| `timeouts.pollMs` | `100` | Pause between two reads of the screen. |
| `timeouts.rpcMs` | `30000` | Upper bound of one request to mobilecli. |
| `timeouts.reserveMs` | `300000` | How long a test waits for a device another test in this JVM holds. |
| `timeouts.serverStartMs` | `20000` | How long an auto-started server may take to answer. |
| `allowRemote` | `false` | Allow a non-loopback `serverUrl` (needs `https://`). |
| `headers` | `{}` | Extra HTTP headers on every request, e.g. for an authenticating proxy. |
| `screenshotOnFailure` | `true` | Attach a device screenshot to a failed test. |

Values support the usual placeholders (`${MOBILE_DEVICE_ID:-}`); see [Configuration](../configuration).

## Device selection

`deviceId` must match exactly one online device of the platform; otherwise the `deviceName` regex must; otherwise exactly one online device of the platform must exist. Zero or several matches fail with `DeviceSelectionException`, listing the devices mobilecli reported.

## Device pool (parallel runs)

List the devices in `devices: [id1, id2]`, or set `pool: true` to use every online device of the platform that matches `deviceName`. Each test gets a free device; when all are in use it waits up to `reserveMs`, then fails with `MobileTimeoutException`.

- Run at most as many parallel mobile workers as there are devices.
- `devices` cannot be combined with `deviceId` or `pool`; `pool` cannot be combined with `deviceId`.
- Offline ids in `devices` are skipped with a warning.
- The reservation is per JVM: separate JVMs or machines sharing devices are not coordinated.

## App reset

`relaunch` terminates and launches `bundleId` before each test; `clearData` clears its data and launches it; `none` leaves it alone. Except with `none`, the app is terminated after the test. Use `none` when tests continue from each other's state (for example a login that should survive).

## Server auto-start {#server-auto-start}

When `serverUrl` is a loopback address (`localhost`, `127.*`, `::1`) and nothing answers, TestFly runs `<mobilecliPath> server start --listen <host:port>`:

- once per endpoint and JVM; parallel tests wait for the same server, and a failed start makes later tests fail fast with the same error;
- output goes to `target/mobilecli-autostart.log`;
- it waits up to `timeouts.serverStartMs` for the server to answer;
- the server is stopped when the JVM exits.

A server that already answers is used as is and never stopped. Remote servers are never started. Set `autoStartServer: false` to require a running server. The mobilecli **agent** is not installed automatically; install it once per device (`mobilecli agent install --device <id>`).

## Profiles

Keep mobile settings in a profile, `src/test/resources/testfly-<name>.yml`, and select it with `-Dtestfly.profile=<name>`. One profile per app and platform keeps the same tests running on iOS and Android:

```bash
mvn test -Dtestfly.profile=myapp-ios     -Dtest=LoginTest
mvn test -Dtestfly.profile=myapp-android -Dtest=LoginTest
```

A profile with a `mobile:` block needs no `browser`, `execution` or `timeouts` block: missing web values get inert defaults (headless local Chrome, `http://localhost`, 10 s / 30 s) and values you set are kept. The examples `testfly-mobile-ios.yml`, `testfly-mobile-android.yml` and `testfly-mobile-ios-login.yml` show a complete profile.

## Security

mobilecli has no authentication, so only loopback addresses are accepted. A remote server needs `allowRemote: true` and an `https://` URL, for example mobilecli behind a TLS-terminating, authenticating proxy or an SSH tunnel; send its credentials with `headers` and an environment placeholder. Never commit credentials: test accounts belong in environment variables.
