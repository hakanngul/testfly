import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';
import * as vscode from 'vscode';
import { BrowserInfo, normaliseUrl } from './browsers';
import { attachFirstPage, closeBrowser, createIsolatedWorld, LaunchedBrowser, launchBrowser } from './cdp';
import { webviewHtml } from './webview';

export interface BrowserLaunch {
    browser: BrowserInfo;
    url: string;
    width: number;
    height: number;
    headless: boolean;
    /** Keep cookies and logins between runs in a TestFly profile; otherwise a throw-away profile. */
    keepSession: boolean;
}

/** Messages the webview sends; see media/browser.js. */
type Inbound =
    | { type: 'ready' | 'refresh' | 'back' | 'forward' | 'reload' }
    | { type: 'navigate'; url: string }
    | { type: 'viewport'; width: number; height: number }
    | { type: 'details'; index: number }
    | { type: 'clickAt'; x: number; y: number }
    | { type: 'click'; index: number }
    | { type: 'type'; index: number; text: string }
    | { type: 'copy'; text: string; label: string };

const MAX_SHOT_HEIGHT = 12_000; // CSS px of a long page that still go into one screenshot

export class BrowserPanel {
    private static current?: BrowserPanel;

    private readonly panel: vscode.WebviewPanel;
    private readonly agent: string;
    private launch: BrowserLaunch;
    private browser?: LaunchedBrowser;
    private session?: string;
    private world?: number; // isolated world the page agent runs in; a navigation destroys it
    private tempProfile?: string;
    private starting?: Promise<void>;
    private busy = false;

    /** Closes the panel and its browser; used when the extension deactivates. */
    static disposeCurrent() {
        BrowserPanel.current?.panel.dispose();
    }

    /** Opens the panel, or points the open one at the new launch settings. */
    static show(context: vscode.ExtensionContext, launch: BrowserLaunch) {
        if (BrowserPanel.current) {
            BrowserPanel.current.panel.reveal();
            void BrowserPanel.current.relaunch(launch);
        } else {
            BrowserPanel.current = new BrowserPanel(context, launch);
        }
    }

    private constructor(private readonly context: vscode.ExtensionContext, launch: BrowserLaunch) {
        this.launch = launch;
        this.agent = fs.readFileSync(path.join(context.extensionPath, 'media', 'page-agent.js'), 'utf8');
        this.panel = vscode.window.createWebviewPanel('testflyBrowserInspector', 'Browser Inspector', vscode.ViewColumn.Active, {
            enableScripts: true,
            retainContextWhenHidden: true,
            localResourceRoots: [vscode.Uri.joinPath(context.extensionUri, 'media')],
        });
        this.panel.iconPath = vscode.Uri.joinPath(context.extensionUri, 'media', 'icon.png');
        this.panel.webview.html = webviewHtml(this.panel.webview, context.extensionUri, {
            title: 'Browser Inspector',
            styles: ['inspector.css', 'browser.css'],
            scripts: ['browser.js'],
            body: fs.readFileSync(path.join(context.extensionPath, 'media', 'browser-body.html'), 'utf8'),
        });
        this.panel.webview.onDidReceiveMessage(m => this.handle(m as Inbound), undefined, context.subscriptions);
        this.panel.onDidDispose(() => this.dispose(), undefined, context.subscriptions);
    }

    private post(message: unknown) {
        void this.panel.webview.postMessage(message);
    }

    private get testIdAttribute(): string {
        return vscode.workspace.getConfiguration('testflyInspector').get('testIdAttribute', 'data-testid') || 'data-testid';
    }

    private async handle(m: Inbound) {
        try {
            switch (m.type) {
                case 'ready':
                    return await this.start();
                case 'refresh':
                    return await this.withPage(() => this.read());
                case 'navigate':
                    return await this.withPage(() => this.navigate(m.url));
                case 'back':
                case 'forward':
                    return await this.withPage(() => this.history(m.type === 'back' ? -1 : 1));
                case 'reload':
                    return await this.withPage(async () => {
                        await this.afterAction(() => this.send('Page.reload'));
                    });
                case 'viewport':
                    return await this.withPage(async () => {
                        this.launch = { ...this.launch, width: m.width, height: m.height };
                        await this.applyViewport();
                        await this.read();
                    });
                case 'details':
                    return this.post({ type: 'details', index: m.index, data: await this.agentCall(`details(${Number(m.index)}, ${this.agentOptions()})`) });
                case 'clickAt':
                    return await this.withPage(() => this.afterAction(() => this.clickAt(m.x, m.y)));
                case 'click':
                    return await this.withPage(() => this.afterAction(() => this.clickElement(m.index)));
                case 'type':
                    return await this.withPage(() => this.afterAction(async () => {
                        await this.clickElement(m.index);
                        await this.agentCall(`focus(${Number(m.index)})`);
                        await this.send('Input.insertText', { text: m.text });
                    }));
                case 'copy':
                    await vscode.env.clipboard.writeText(m.text);
                    vscode.window.setStatusBarMessage(`$(check) Copied ${m.label}`, 2500);
                    return;
            }
        } catch (e) {
            this.post({ type: 'error', message: (e as Error).message });
        }
    }

    // ── browser lifecycle ───────────────────────────────────────────────
    private async start() {
        if (!this.starting) {
            this.starting = this.open().finally(() => (this.starting = undefined));
        }
        await this.starting;
    }

    private async open() {
        const l = this.launch;
        this.post({ type: 'status', state: 'connecting', message: `Starting ${l.browser.name}…` });
        const profile = l.keepSession ? path.join(this.context.globalStorageUri.fsPath, 'browser-profile', l.browser.id) : this.newTempProfile();
        fs.mkdirSync(profile, { recursive: true });
        const browser = await launchBrowser({ executable: l.browser.path, userDataDir: profile, headless: l.headless, width: l.width, height: l.height });
        this.browser = browser;
        browser.cdp.on('closed', ({ reason }) => {
            if (this.browser === browser) {
                this.browser = undefined;
                this.session = undefined;
                this.post({ type: 'status', state: 'error', message: `Browser closed (${reason}). Refresh starts it again.` });
            }
        });
        this.post({ type: 'browser', product: browser.product, path: l.browser.path, viewport: { width: l.width, height: l.height } });
        this.session = await attachFirstPage(browser.cdp);
        this.world = undefined;
        await this.applyViewport();
        await this.navigate(l.url);
    }

    async relaunch(launch: BrowserLaunch) {
        await this.stopBrowser();
        this.launch = launch;
        await this.handle({ type: 'ready' });
    }

    private async stopBrowser() {
        const b = this.browser;
        this.browser = undefined;
        this.session = undefined;
        if (b) {
            await closeBrowser(b);
        }
        this.removeTempProfile();
    }

    private newTempProfile(): string {
        this.removeTempProfile();
        this.tempProfile = fs.mkdtempSync(path.join(os.tmpdir(), 'testfly-browser-'));
        return this.tempProfile;
    }

    private removeTempProfile() {
        if (this.tempProfile) {
            fs.rm(this.tempProfile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 }, () => undefined);
            this.tempProfile = undefined;
        }
    }

    dispose() {
        BrowserPanel.current = undefined;
        void this.stopBrowser();
    }

    // ── page work ───────────────────────────────────────────────────────
    /** Runs page work one at a time; starts the browser again when it was closed. */
    private async withPage(work: () => Promise<void>) {
        if (!this.browser || this.browser.cdp.closed) {
            return this.start();
        }
        if (this.busy) {
            return;
        }
        this.busy = true;
        try {
            await work();
        } finally {
            this.busy = false;
        }
    }

    private send<T = any>(method: string, params: Record<string, unknown> = {}, timeoutMs?: number): Promise<T> {
        if (!this.browser || !this.session) {
            return Promise.reject(new Error('The browser is not running. Press Refresh to start it.'));
        }
        return this.browser.cdp.send<T>(method, params, this.session, timeoutMs);
    }

    private agentOptions(): string {
        return JSON.stringify({ testIdAttribute: this.testIdAttribute });
    }

    /** Runs the page agent in an isolated world, so the page cannot read or replace it. */
    private async agentCall(call: string): Promise<any> {
        let r: any;
        for (let attempt = 0; ; attempt++) {
            this.world ??= await createIsolatedWorld(this.browser!.cdp, this.session!);
            try {
                r = await this.send<any>('Runtime.evaluate', { expression: `(${this.agent}).${call}`, returnByValue: true, contextId: this.world });
                break;
            } catch (e) {
                this.world = undefined; // destroyed by a navigation: make a new one once
                if (attempt > 0 || !/context/i.test((e as Error).message)) {
                    throw e;
                }
            }
        }
        if (r.exceptionDetails) {
            throw new Error(`Page script failed: ${r.exceptionDetails.exception?.description ?? r.exceptionDetails.text}`);
        }
        return r.result?.value;
    }

    private async navigate(url: string) {
        const target = normaliseUrl(url);
        this.post({ type: 'status', state: 'loading', message: `Opening ${target}…` });
        const loaded = this.browser!.cdp.waitFor('Page.loadEventFired', 20_000, this.session);
        const r = await this.send<any>('Page.navigate', { url: target });
        if (r.errorText) {
            throw new Error(`Could not open ${target}: ${r.errorText}`);
        }
        await loaded;
        await this.read();
    }

    private async history(step: number) {
        const h = await this.send<any>('Page.getNavigationHistory');
        const entry = h.entries[h.currentIndex + step];
        if (entry) {
            await this.afterAction(() => this.send('Page.navigateToHistoryEntry', { entryId: entry.id }));
        } else {
            await this.read();
        }
    }

    /** Runs an action, waits for a navigation it starts, then reads the page again. */
    private async afterAction(action: () => Promise<unknown>) {
        const cdp = this.browser!.cdp;
        // A page restored from the back/forward cache navigates without loading.
        const navigation = cdp.waitForAny(['Page.frameStartedLoading', 'Page.frameNavigated'], 600, this.session);
        await action();
        const nav = await navigation;
        if (nav?.event === 'Page.frameStartedLoading') {
            // the frame that started loading, not an iframe that happens to finish first
            const deadline = Date.now() + 15_000;
            while (Date.now() < deadline) {
                const stopped = await cdp.waitFor('Page.frameStoppedLoading', deadline - Date.now(), this.session);
                if (!stopped || stopped.frameId === nav.params.frameId) {
                    break;
                }
            }
        }
        await new Promise(r => setTimeout(r, 250)); // let scripts and transitions settle
        await this.read();
    }

    private async applyViewport() {
        const { width, height } = this.launch;
        await this.send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width < 600 });
        this.post({ type: 'browser', product: this.browser?.product, path: this.launch.browser.path, viewport: { width, height } });
    }

    private async read() {
        const started = Date.now();
        this.post({ type: 'status', state: 'loading', message: 'Reading the page…' });
        const snap = await this.agentCall(`snapshot(${this.agentOptions()})`);
        const width = Math.max(1, Math.min(snap.width, 4_000));
        const height = Math.max(1, Math.min(snap.height, MAX_SHOT_HEIGHT));
        const shot = await this.send<any>('Page.captureScreenshot', {
            format: 'jpeg', quality: 80, captureBeyondViewport: true,
            clip: { x: 0, y: 0, width, height, scale: width > 1600 ? 1600 / width : 1 },
        }, 60_000);
        const h = await this.send<any>('Page.getNavigationHistory');
        this.post({ type: 'history', canBack: h.currentIndex > 0, canForward: h.currentIndex < h.entries.length - 1 });
        this.post({
            type: 'page', url: snap.url, title: snap.title, tree: snap.tree, count: snap.count, truncated: snap.truncated,
            screen: { width, height }, screenshot: 'data:image/jpeg;base64,' + shot.data, ms: Date.now() - started,
        });
    }

    private async clickAt(x: number, y: number) {
        // Bring the point into the viewport, then click there like a user would.
        const pos = await this.send<any>('Runtime.evaluate', {
            expression: `(() => { window.scrollTo(${Math.round(x)} - innerWidth / 2, ${Math.round(y)} - innerHeight / 2);
                return { x: ${Math.round(x)} - scrollX, y: ${Math.round(y)} - scrollY }; })()`,
            returnByValue: true,
        });
        await this.mouseClick(pos.result.value.x, pos.result.value.y);
    }

    private async clickElement(index: number) {
        const c = await this.agentCall(`center(${Number(index)})`);
        if (!c) {
            throw new Error('The element is no longer on the page. Press Refresh.');
        }
        await this.mouseClick(c.x, c.y);
    }

    private async mouseClick(x: number, y: number) {
        await this.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x, y });
        await this.send('Input.dispatchMouseEvent', { type: 'mousePressed', x, y, button: 'left', clickCount: 1 });
        await this.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x, y, button: 'left', clickCount: 1 });
    }
}
