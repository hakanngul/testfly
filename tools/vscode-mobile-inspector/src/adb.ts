import { execFile } from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';

/** A device as `adb devices` reports it, including the ones mobilecli leaves out (unauthorized, offline). */
export interface AdbDevice {
    serial: string;
    state: string;
    model?: string;
}

/** adb from ANDROID_HOME / ANDROID_SDK_ROOT, the SDK's default location, or PATH. */
export function findAdb(env: NodeJS.ProcessEnv = process.env, platform: NodeJS.Platform = process.platform): string {
    const exe = platform === 'win32' ? 'adb.exe' : 'adb';
    const home = os.homedir();
    const sdks = [env.ANDROID_HOME, env.ANDROID_SDK_ROOT,
        platform === 'darwin' ? path.join(home, 'Library', 'Android', 'sdk') : undefined,
        platform === 'win32' && env.LOCALAPPDATA ? path.join(env.LOCALAPPDATA, 'Android', 'Sdk') : undefined,
        platform === 'linux' ? path.join(home, 'Android', 'Sdk') : undefined];
    for (const sdk of sdks) {
        const candidate = sdk && path.join(sdk, 'platform-tools', exe);
        if (candidate && fs.existsSync(candidate)) {
            return candidate;
        }
    }
    return exe; // from PATH
}

function run(adb: string, args: string[], timeoutMs = 15_000): Promise<string> {
    return new Promise((resolve, reject) => execFile(adb, args, { timeout: timeoutMs }, (err, stdout, stderr) => {
        const out = `${stdout}${stderr}`.trim();
        if (err) {
            reject(new Error((err as NodeJS.ErrnoException).code === 'ENOENT'
                ? 'adb was not found: install the Android SDK platform-tools or set ANDROID_HOME'
                : out || err.message));
        } else {
            resolve(out);
        }
    }));
}

/** Parses `adb devices -l`. */
export function parseAdbDevices(output: string): AdbDevice[] {
    return output.split('\n').slice(1).map(l => l.trim()).filter(Boolean).flatMap(line => {
        const [serial, state, ...rest] = line.split(/\s+/);
        if (!serial || !state || serial.startsWith('*')) {
            return [];
        }
        const model = rest.find(r => r.startsWith('model:'))?.slice(6).replace(/_/g, ' ');
        return [{ serial, state, model }];
    });
}

export async function adbDevices(adb: string): Promise<AdbDevice[]> {
    return parseAdbDevices(await run(adb, ['devices', '-l']));
}

/**
 * Android 11+ wireless debugging: with a pairing code, `adb pair host:pairPort code` (the address shown under
 * "Pair device with pairing code"); without one, `adb connect host:port` (the address on the Wireless debugging
 * screen, or port 5555 after `adb tcpip 5555`).
 */
export async function adbWifi(adb: string, address: string, code?: string): Promise<string> {
    const addr = address.trim();
    if (!/^[\w.:\-[\]%]+$/.test(addr)) {
        throw new Error(`'${address}' is not a host:port address`);
    }
    if (code?.trim()) {
        if (!/^\d{6}$/.test(code.trim())) {
            throw new Error('The pairing code has 6 digits');
        }
        const out = await run(adb, ['pair', addr, code.trim()], 30_000);
        if (!/success/i.test(out)) {
            throw new Error(out || 'adb pair failed');
        }
        return out;
    }
    const out = await run(adb, ['connect', addr.includes(':') ? addr : addr + ':5555'], 20_000);
    if (/cannot|failed|refused|unable/i.test(out)) {
        throw new Error(out);
    }
    return out;
}

export function adbDisconnect(adb: string, serial: string): Promise<string> {
    return run(adb, ['disconnect', serial]);
}
