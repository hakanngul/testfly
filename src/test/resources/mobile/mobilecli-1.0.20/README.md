# mobilecli 1.0.20 responses

Recorded on 2026-10-22 from `mobilecli server start` (1.0.20, linux-amd64) against an Android 14
emulator (`system-images;android-34;google_apis;x86_64`, AVD `tf34`). The emulator ran without
hardware acceleration (`-accel off`), so it was very slow.

| File | Request | Observed |
| --- | --- | --- |
| `server.info.json` | `server.info` | `{name, version}` |
| `devices.list.json` | `devices.list` `{}` | `{devices: [...]}` (an object, not a bare array). The Android emulator id is the AVD name (`tf34`), not the adb serial (`emulator-5554`). |
| `device.info.json` | `device.info` `{deviceId}` | `{device: {..., screenSize: {width, height, scale}}}` |
| `device.dump.ui.json` | `device.dump.ui` `{deviceId}` | `-32000`, `data` carries the reason (here: empty `uiautomator dump` on the overloaded emulator) |
| `error.unknown-device.json` | `device.info` `{deviceId: "nope"}` | `-32000` with `data: "error finding device: device not found: nope"` (not `-32010`) |
| `error.unknown-method.json` | `device.bogus` | `-32601` |
| `error.bad-json.json` | truncated body | `-32700`, `id: null` |

Not recorded (the slow emulator could not serve them):

- `device.dump.ui` success and `device.screenshot`. On the first attempts both ended after 60 s with
  the connection closed and **no JSON-RPC response** (`curl: (52) Empty reply from server`); the server
  log shows `read from daemon: ... i/o timeout`. A client must treat an empty reply as a transport error.
- `device.info` with no `deviceId` behaved the same way (empty reply after 60 s).

Record the success cases on a hardware-accelerated emulator or a real device.
