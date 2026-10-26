import { ChildProcess, spawn } from 'child_process';
import { Readable, Writable } from 'stream';

export class CdpError extends Error {}

type Listener = (params: any, sessionId?: string) => void;

/**
 * Chrome DevTools Protocol over `--remote-debugging-pipe`: the browser reads NUL-terminated JSON
 * messages on fd 3 and writes them on fd 4, so no WebSocket client is needed.
 */
export class CdpConnection {
    private nextId = 1;
    private readonly pending = new Map<number, { resolve: (v: any) => void; reject: (e: Error) => void; method: string }>();
    private readonly listeners = new Map<string, Set<Listener>>();
    private parts: Buffer[] = [];
    private closedReason?: string;

    constructor(private readonly input: Writable, output: Readable) {
        output.on('data', (chunk: Buffer) => this.receive(chunk));
        output.on('close', () => this.close('the browser closed'));
        output.on('error', err => this.close(err.message));
        input.on('error', err => this.close(err.message));
    }

    get closed(): boolean {
        return this.closedReason !== undefined;
    }

    send<T = any>(method: string, params: Record<string, unknown> = {}, sessionId?: string, timeoutMs = 30_000): Promise<T> {
        if (this.closedReason !== undefined) {
            return Promise.reject(new CdpError(`${method}: ${this.closedReason}`));
        }
        const id = this.nextId++;
        return new Promise<T>((resolve, reject) => {
            const timer = setTimeout(() => {
                this.pending.delete(id);
                reject(new CdpError(`${method} did not answer within ${timeoutMs} ms`));
            }, timeoutMs);
            this.pending.set(id, {
                method,
                resolve: v => (clearTimeout(timer), resolve(v)),
                reject: e => (clearTimeout(timer), reject(e)),
            });
            this.input.write(JSON.stringify(sessionId ? { id, method, params, sessionId } : { id, method, params }) + '\0');
        });
    }

    /** Subscribes to a protocol event; returns the unsubscribe function. */
    on(event: string, listener: Listener): () => void {
        let set = this.listeners.get(event);
        if (!set) {
            this.listeners.set(event, (set = new Set()));
        }
        set.add(listener);
        return () => set!.delete(listener);
    }

    /** Resolves with the next matching event's params, or undefined after timeoutMs. */
    async waitFor(event: string, timeoutMs: number, sessionId?: string): Promise<any | undefined> {
        return (await this.waitForAny([event], timeoutMs, sessionId))?.params;
    }

    /** Resolves with the first of the events to arrive, or undefined after timeoutMs. */
    waitForAny(events: string[], timeoutMs: number, sessionId?: string): Promise<{ event: string; params: any } | undefined> {
        return new Promise(resolve => {
            const offs = events.map(event => this.on(event, (params, sid) => {
                if (!sessionId || sid === sessionId) {
                    done();
                    resolve({ event, params });
                }
            }));
            const done = () => (offs.forEach(off => off()), clearTimeout(timer));
            const timer = setTimeout(() => (done(), resolve(undefined)), timeoutMs);
        });
    }

    close(reason = 'the connection was closed') {
        if (this.closedReason !== undefined) {
            return;
        }
        this.closedReason = reason;
        for (const p of this.pending.values()) {
            p.reject(new CdpError(`${p.method}: ${reason}`));
        }
        this.pending.clear();
        this.listeners.get('closed')?.forEach(l => l({ reason }));
    }

    private receive(chunk: Buffer) {
        let start = 0;
        let end: number;
        while ((end = chunk.indexOf(0, start)) >= 0) {
            this.parts.push(chunk.subarray(start, end));
            const text = Buffer.concat(this.parts).toString('utf8');
            this.parts = [];
            start = end + 1;
            this.dispatch(text);
        }
        if (start < chunk.length) {
            this.parts.push(chunk.subarray(start));
        }
    }

    private dispatch(text: string) {
        let msg: any;
        try {
            msg = JSON.parse(text);
        } catch {
            return;
        }
        if (typeof msg.id === 'number') {
            const p = this.pending.get(msg.id);
            if (!p) {
                return;
            }
            this.pending.delete(msg.id);
            if (msg.error) {
                p.reject(new CdpError(`${p.method}: ${msg.error.message}${msg.error.data ? ` (${msg.error.data})` : ''}`));
            } else {
                p.resolve(msg.result);
            }
        } else if (msg.method) {
            this.listeners.get(msg.method)?.forEach(l => l(msg.params, msg.sessionId));
        }
    }
}

export interface LaunchOptions {
    executable: string;
    userDataDir: string;
    headless: boolean;
    width: number;
    height: number;
}

export interface LaunchedBrowser {
    process: ChildProcess;
    cdp: CdpConnection;
    /** e.g. "Chrome/141.0.7390.54" */
    product: string;
}

/** Starts a Chromium-based browser with the DevTools protocol on a pipe. */
export async function launchBrowser(o: LaunchOptions, timeoutMs = 20_000): Promise<LaunchedBrowser> {
    const args = [
        '--remote-debugging-pipe',
        `--user-data-dir=${o.userDataDir}`,
        '--no-first-run',
        '--no-default-browser-check',
        '--disable-search-engine-choice-screen',
        `--window-size=${o.width},${o.height}`,
        ...(o.headless ? ['--headless=new'] : []),
        ...(process.platform === 'linux' && process.getuid?.() === 0 ? ['--no-sandbox'] : []), // Chromium refuses to run as root otherwise
        'about:blank',
    ];
    const proc = spawn(o.executable, args, { stdio: ['ignore', 'ignore', 'pipe', 'pipe', 'pipe'] });
    let stderr = '';
    proc.stderr!.on('data', d => (stderr = (stderr + d).slice(-2000)));
    const cdp = new CdpConnection(proc.stdio[3] as Writable, proc.stdio[4] as Readable);
    proc.on('exit', code => cdp.close(`the browser exited (code ${code})`));
    const failed = new Promise<never>((_, reject) => {
        proc.once('error', err => reject(new CdpError(`Could not start ${o.executable}: ${err.message}`)));
        proc.once('exit', code => reject(new CdpError(`${o.executable} exited with code ${code} before it answered${stderr ? `: ${lastLine(stderr)}` : ''}`)));
    });
    failed.catch(() => undefined); // only matters until the first answer; a later exit is reported through cdp
    try {
        const version = await Promise.race([cdp.send<any>('Browser.getVersion', {}, undefined, timeoutMs), failed]);
        return { process: proc, cdp, product: version.product ?? '' };
    } catch (e) {
        proc.kill();
        throw e;
    }
}

/**
 * Attaches to the browser's first tab and enables page events; returns the session id. Right after launch the
 * start-up tab can be missing from the target list for a moment, so it is awaited before a new tab is opened.
 */
export async function attachFirstPage(cdp: CdpConnection, waitMs = 3_000): Promise<string> {
    const deadline = Date.now() + waitMs;
    let page: any;
    for (;;) {
        const { targetInfos } = await cdp.send<any>('Target.getTargets');
        page = (targetInfos as any[]).find(t => t.type === 'page');
        if (page || Date.now() >= deadline) {
            break;
        }
        await new Promise(r => setTimeout(r, 100));
    }
    const targetId = page ? page.targetId : (await cdp.send<any>('Target.createTarget', { url: 'about:blank' })).targetId;
    const { sessionId } = await cdp.send<any>('Target.attachToTarget', { targetId, flatten: true });
    await cdp.send('Page.enable', {}, sessionId);
    return sessionId;
}

/**
 * An isolated JavaScript world in the page's main frame: it shares the DOM but not the page's globals, so page
 * scripts can neither see nor replace what the inspector runs there. A navigation destroys it.
 */
export async function createIsolatedWorld(cdp: CdpConnection, sessionId: string): Promise<number> {
    const { frameTree } = await cdp.send<any>('Page.getFrameTree', {}, sessionId);
    const { executionContextId } = await cdp.send<any>('Page.createIsolatedWorld', { frameId: frameTree.frame.id, worldName: 'testfly-inspector' }, sessionId);
    return executionContextId;
}

/**
 * Closes the browser gracefully (a kept session flushes its cookies, helper processes stop writing the profile),
 * and kills it only when it does not exit in time.
 */
export async function closeBrowser(b: LaunchedBrowser, graceMs = 5_000): Promise<void> {
    await b.cdp.send('Browser.close', {}, undefined, 3_000).catch(() => undefined);
    b.cdp.close();
    await waitForExit(b.process, graceMs);
    if (b.process.exitCode === null && b.process.signalCode === null) {
        b.process.kill();
        await waitForExit(b.process);
    }
}

/** Resolves once the process has exited, or after timeoutMs; the browser keeps writing its profile until then. */
export function waitForExit(proc: ChildProcess, timeoutMs = 5_000): Promise<void> {
    if (proc.exitCode !== null || proc.signalCode !== null) {
        return Promise.resolve();
    }
    return new Promise(resolve => {
        const timer = setTimeout(resolve, timeoutMs);
        proc.once('exit', () => (clearTimeout(timer), resolve()));
    });
}

function lastLine(text: string): string {
    return text.trim().split('\n').filter(Boolean).pop() ?? '';
}
