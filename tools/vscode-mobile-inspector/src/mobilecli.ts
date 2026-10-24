import { ChildProcess, spawn } from 'child_process';

/** One element of mobilecli's `device.dump.ui` tree (mobilecli 1.0.20 `ScreenElement`). */
export interface ScreenElement {
    ref?: string;
    type: string;
    label?: string;
    text?: string;
    name?: string;
    value?: string;
    placeholder?: string;
    identifier?: string;
    key?: string;
    rect?: { x: number; y: number; width: number; height: number };
    focused?: boolean;
    enabled?: boolean;
    checked?: boolean;
    selected?: boolean;
    children?: ScreenElement[];
}

export interface DeviceInfo {
    id: string;
    name: string;
    platform: string;
    type: string;
    state: string;
    version?: string;
}

export interface ScreenSize {
    width: number;
    height: number;
    scale: number;
}

export class RpcError extends Error {
    constructor(message: string, readonly code?: number, readonly unreachable = false) {
        super(message);
    }
}

/** JSON-RPC client for a mobilecli server (`POST <serverUrl>/rpc`). */
export class MobileCli {
    private nextId = 1;
    private server?: ChildProcess;

    constructor(private serverUrl: string, private readonly mobilecliPath = 'mobilecli', private readonly autoStart = true) {}

    get endpoint(): string {
        return this.serverUrl.replace(/\/+$/, '') + '/rpc';
    }

    async call<T = any>(method: string, params: Record<string, unknown> = {}, timeoutMs = 60_000): Promise<T> {
        let response: Response;
        try {
            response = await fetch(this.endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ jsonrpc: '2.0', id: this.nextId++, method, params }),
                signal: AbortSignal.timeout(timeoutMs),
            });
        } catch (e) {
            throw new RpcError(`mobilecli server at ${this.serverUrl} is not reachable (${(e as Error).message})`, undefined, true);
        }
        const body: any = await response.json().catch(() => undefined);
        if (!body) {
            throw new RpcError(`mobilecli ${method}: HTTP ${response.status} without a JSON-RPC body`);
        }
        if (body.error) {
            const data = typeof body.error.data === 'string' && body.error.data ? `: ${body.error.data}` : '';
            throw new RpcError(`mobilecli ${method} failed (${body.error.code} ${body.error.message})${data}`, body.error.code);
        }
        return body.result as T;
    }

    /** Checks the server; starts `mobilecli server start` once when a local server does not answer. */
    async ensureServer(startTimeoutMs = 20_000): Promise<string> {
        try {
            return await this.version();
        } catch (e) {
            if (!(e instanceof RpcError) || !e.unreachable || !this.autoStart || !isLoopback(this.serverUrl) || this.server) {
                throw e;
            }
        }
        const port = new URL(this.serverUrl).port || '12000';
        this.server = spawn(this.mobilecliPath, ['server', 'start', '--listen', `localhost:${port}`], { stdio: 'ignore' });
        const spawnError = new Promise<never>((_, reject) => this.server!.once('error', err =>
            reject(new RpcError(`Could not run '${this.mobilecliPath} server start': ${err.message}. Install mobilecli or set testflyInspector.mobilecliPath.`))));
        const deadline = Date.now() + startTimeoutMs;
        while (Date.now() < deadline) {
            try {
                return await Promise.race([this.version(), spawnError]);
            } catch (e) {
                if (!(e instanceof RpcError) || !e.unreachable) {
                    throw e;
                }
                await new Promise(r => setTimeout(r, 300));
            }
        }
        throw new RpcError(`mobilecli server did not answer at ${this.serverUrl} within ${startTimeoutMs} ms`, undefined, true);
    }

    async version(): Promise<string> {
        const info = await this.call<any>('server.info', {}, 3_000);
        return info?.version ?? '';
    }

    async devices(): Promise<DeviceInfo[]> {
        const result = await this.call<any>('devices.list', {});
        const list: any[] = Array.isArray(result) ? result : result?.devices ?? [];
        return list.map(d => ({ id: d.id, name: d.name, platform: d.platform, type: d.type, state: d.state, version: d.version }));
    }

    async screenSize(deviceId: string): Promise<ScreenSize> {
        const s = (await this.call<any>('device.info', { deviceId }))?.device?.screenSize ?? {};
        return { width: s.width ?? 0, height: s.height ?? 0, scale: s.scale ?? 1 };
    }

    async dump(deviceId: string): Promise<ScreenElement[]> {
        const result = await this.call<any>('device.dump.ui', { deviceId });
        return Array.isArray(result) ? result : result?.elements ?? [];
    }

    /** The tree as mobilecli's text snapshot (`mobilecli snapshot`), one element per line with its @eN ref. */
    async snapshotText(deviceId: string): Promise<string> {
        return (await this.call<any>('device.dump.ui', { deviceId, format: 'text' }))?.text ?? '';
    }

    /** PNG/JPEG as a data URL. */
    async screenshot(deviceId: string): Promise<string> {
        // A small JPEG is several times lighter than a full PNG and plenty for the inspector pane.
        return (await this.call<any>('device.screenshot', { deviceId, format: 'jpeg', quality: 80, maxSize: 1400 }))?.data ?? '';
    }

    tap(deviceId: string, x: number, y: number) {
        return this.call('device.io.tap', { deviceId, x: Math.round(x), y: Math.round(y) });
    }

    longPress(deviceId: string, x: number, y: number) {
        return this.call('device.io.longpress', { deviceId, x: Math.round(x), y: Math.round(y) });
    }

    typeText(deviceId: string, text: string) {
        return this.call('device.io.text', { deviceId, text });
    }

    swipe(deviceId: string, x1: number, y1: number, x2: number, y2: number) {
        return this.call('device.io.swipe', { deviceId, x1: Math.round(x1), y1: Math.round(y1), x2: Math.round(x2), y2: Math.round(y2) });
    }

    pressButton(deviceId: string, button: string) {
        return this.call('device.io.button', { deviceId, button });
    }

    /** Stops a server this client started; a server that was already running is left alone. */
    dispose() {
        this.server?.kill();
        this.server = undefined;
    }
}

export function isLoopback(url: string): boolean {
    try {
        const host = new URL(url).hostname;
        return host === 'localhost' || host === '::1' || host === '[::1]' || host.startsWith('127.');
    } catch {
        return false;
    }
}
