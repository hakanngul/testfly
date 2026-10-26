const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { parseAdbDevices, findAdb, adbWifi } = require('../out/adb');

test('parses adb devices -l, unauthorized and wireless devices too', () => {
    const out = `List of devices attached
emulator-5554          device product:sdk_gphone64_arm64 model:sdk_gphone64_arm64 device:emu64a transport_id:1
R5CT1234ABC            unauthorized usb:1-1 transport_id:3
192.168.1.20:37099     device product:oriole model:Pixel_6 device:oriole transport_id:4
adb-2A111FDH-x._adb-tls-connect._tcp offline transport_id:5
* daemon started successfully
`;
    assert.deepStrictEqual(parseAdbDevices(out), [
        { serial: 'emulator-5554', state: 'device', model: 'sdk gphone64 arm64' },
        { serial: 'R5CT1234ABC', state: 'unauthorized', model: undefined },
        { serial: '192.168.1.20:37099', state: 'device', model: 'Pixel 6' },
        { serial: 'adb-2A111FDH-x._adb-tls-connect._tcp', state: 'offline', model: undefined },
    ]);
});

test('finds adb in ANDROID_HOME before PATH', () => {
    const sdk = fs.mkdtempSync(path.join(os.tmpdir(), 'sdk-'));
    fs.mkdirSync(path.join(sdk, 'platform-tools'));
    fs.writeFileSync(path.join(sdk, 'platform-tools', 'adb'), '');
    assert.strictEqual(findAdb({ ANDROID_HOME: sdk }, 'linux'), path.join(sdk, 'platform-tools', 'adb'));
    assert.strictEqual(findAdb({ ANDROID_HOME: '/nope' }, 'win32'), 'adb.exe');
    fs.rmSync(sdk, { recursive: true });
});

test('Wi-Fi pairing checks its input before running adb', async () => {
    await assert.rejects(adbWifi('adb', '192.168.1.20:37099; rm -rf /'), /not a host:port/);
    await assert.rejects(adbWifi('adb', '192.168.1.20:37099', '12ab'), /6 digits/);
    await assert.rejects(adbWifi('/nonexistent/adb', '192.168.1.20:5555'), /adb was not found/);
});
