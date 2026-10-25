import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import { BrowserPanel } from './browserPanel';
import { findBrowsers } from './browsers';
import { DeviceInfo, MobileCli, RpcError, ScreenSize } from './mobilecli';
import { recentUrls, rememberUrl, SidebarProvider } from './sidebar';
import { webviewHtml } from './webview';

let panel: InspectorPanel | undefined;

export function activate(context: vscode.ExtensionContext) {
    context.subscriptions.push(vscode.commands.registerCommand('testflyInspector.open', () => {
        if (panel) {
            panel.reveal();
        } else {
            panel = new InspectorPanel(context, () => (panel = undefined));
        }
    }));
    context.subscriptions.push(vscode.window.registerWebviewViewProvider(SidebarProvider.viewId, new SidebarProvider(context)));
    context.subscriptions.push(vscode.commands.registerCommand('testflyInspector.openBrowser', () => openBrowser(context)));
}

/** Command palette entry: asks for a URL and opens it in the first browser found, with a window and a kept session. */
async function openBrowser(context: vscode.ExtensionContext) {
    const browser = findBrowsers(vscode.workspace.getConfiguration('testflyInspector').get('browserPath', ''))[0];
    if (!browser) {
        void vscode.window.showErrorMessage('No Chrome, Edge or Chromium found. Install one or set testflyInspector.browserPath.');
        return;
    }
    const url = await vscode.window.showInputBox({ title: 'Browser Inspector', prompt: `Page to open in ${browser.name}`, value: recentUrls(context)[0] ?? 'https://' });
    if (!url) {
        return;
    }
    await rememberUrl(context, url);
    BrowserPanel.show(context, { browser, url, width: 1440, height: 900, headless: false, keepSession: true });
}

export function deactivate() {
    panel?.dispose();
    BrowserPanel.disposeCurrent();
}

/** Messages the webview sends; see media/inspector.js. */
type Inbound =
    | { type: 'ready' }
    | { type: 'devices' }
    | { type: 'selectDevice'; id: string }
    | { type: 'server'; action: 'start' | 'stop' }
    | { type: 'refresh' }
    | { type: 'snapshotText' }
    | { type: 'tap' | 'longPress'; x: number; y: number }
    | { type: 'text'; text: string }
    | { type: 'tapType'; x: number; y: number; text: string }
    | { type: 'swipe'; direction: 'up' | 'down' | 'left' | 'right' }
    | { type: 'button'; button: string }
    | { type: 'copy'; text: string; label: string };

class InspectorPanel {
    private readonly webview: vscode.WebviewPanel;
    private readonly cli: MobileCli;
    private device?: DeviceInfo;
    private screen?: ScreenSize;
    private busy = false;
    private warm = false; // mobilecli's agent is up for this device: tree and screenshot can run together
    private serverStopped = false; // stopped from the panel: no auto-start until it is started from the panel again

    constructor(private readonly context: vscode.ExtensionContext, private readonly onDispose: () => void) {
        const cfg = vscode.workspace.getConfiguration('testflyInspector');
        this.cli = new MobileCli(cfg.get('serverUrl', 'http://localhost:12000'), cfg.get('mobilecliPath', 'mobilecli'),
            cfg.get('autoStartServer', true));
        this.webview = vscode.window.createWebviewPanel('testflyInspector', 'Mobile Inspector', vscode.ViewColumn.Active, {
            enableScripts: true,
            retainContextWhenHidden: true,
            localResourceRoots: [vscode.Uri.joinPath(context.extensionUri, 'media')],
        });
        this.webview.iconPath = vscode.Uri.joinPath(context.extensionUri, 'media', 'icon.png');
        this.webview.webview.html = this.html();
        this.webview.webview.onDidReceiveMessage(m => this.handle(m as Inbound), undefined, context.subscriptions);
        this.webview.onDidDispose(() => this.dispose(), undefined, context.subscriptions);
    }

    reveal() {
        this.webview.reveal();
    }

    dispose() {
        this.cli.dispose();
        this.onDispose();
    }

    private post(message: unknown) {
        void this.webview.webview.postMessage(message);
    }

    private async handle(m: Inbound) {
        try {
            switch (m.type) {
                case 'ready':
                case 'devices':
                    return await this.loadDevices();
                case 'selectDevice':
                    return await this.selectDevice(m.id);
                case 'server':
                    return m.action === 'start' ? await this.startServer() : await this.stopServer();
                case 'refresh':
                    return await this.refresh();
                case 'snapshotText':
                    return this.post({ type: 'snapshotText', text: await this.cli.snapshotText(this.requireDevice().id) });
                case 'tap':
                    return await this.act(id => this.cli.tap(id, m.x, m.y));
                case 'longPress':
                    return await this.act(id => this.cli.longPress(id, m.x, m.y));
                case 'text':
                    return await this.act(id => this.cli.typeText(id, m.text));
                case 'tapType':
                    return await this.act(async id => {
                        await this.cli.tap(id, m.x, m.y);
                        await new Promise(r => setTimeout(r, 300)); // let the field take focus
                        await this.cli.typeText(id, m.text);
                    });
                case 'button':
                    return await this.act(id => this.cli.pressButton(id, m.button));
                case 'swipe':
                    return await this.act(id => this.swipe(id, m.direction));
                case 'copy':
                    await vscode.env.clipboard.writeText(m.text);
                    vscode.window.setStatusBarMessage(`$(check) Copied ${m.label}`, 2500);
                    return;
            }
        } catch (e) {
            const unreachable = e instanceof RpcError && e.unreachable;
            if (unreachable) {
                this.post({ type: 'server', running: false });
            }
            this.post({ type: 'error', message: (e as Error).message, unreachable });
        }
    }

    private async loadDevices() {
        this.post({ type: 'status', state: 'connecting', message: 'Connecting to mobilecli…' });
        const version = await this.cli.ensureServer(undefined, this.serverStopped ? false : undefined);
        this.post({ type: 'server', running: true, version });
        const devices = (await this.cli.devices()).filter(d => d.state === 'online');
        this.post({ type: 'devices', devices, version });
        const keep = devices.find(d => d.id === this.device?.id) ?? (devices.length === 1 ? devices[0] : undefined);
        if (keep) {
            await this.selectDevice(keep.id, devices);
        } else if (!devices.length) {
            this.post({ type: 'status', state: 'idle', message: 'No online device. Boot a simulator or emulator, then press refresh.' });
        } else {
            this.post({ type: 'status', state: 'idle', message: 'Choose a device.' });
        }
    }

    private async startServer() {
        this.serverStopped = false;
        this.post({ type: 'status', state: 'connecting', message: 'Starting mobilecli server…' });
        await this.cli.startServer();
        await this.loadDevices();
    }

    private async stopServer() {
        this.serverStopped = true;
        this.post({ type: 'status', state: 'loading', message: 'Stopping mobilecli server…' });
        await this.cli.stopServer();
        this.device = undefined;
        this.screen = undefined;
        this.warm = false;
        this.post({ type: 'server', running: false });
        this.post({ type: 'devices', devices: [] });
        this.post({ type: 'status', state: 'idle', message: 'mobilecli server stopped. Start it from the Server button.' });
    }

    private async selectDevice(id: string, known?: DeviceInfo[]) {
        const devices = known ?? (await this.cli.devices());
        this.device = devices.find(d => d.id === id);
        this.screen = undefined;
        this.warm = false;
        if (!this.device) {
            throw new Error(`Device ${id} is not online any more.`);
        }
        this.post({ type: 'device', device: this.device });
        await this.refresh();
    }

    private async refresh() {
        const device = this.requireDevice();
        if (this.busy) {
            return;
        }
        this.busy = true;
        this.post({ type: 'status', state: 'loading', message: `Reading ${device.name}…` });
        try {
            const started = Date.now();
            this.screen ??= await this.cli.screenSize(device.id);
            let elements, screenshot;
            if (this.warm) {
                // Both at once; the picture is shown as soon as it arrives, the tree follows.
                const shot = this.cli.screenshot(device.id).then(s => (this.post({ type: 'shot', screenshot: s }), s));
                [elements, screenshot] = await Promise.all([this.cli.dump(device.id), shot]);
            } else {
                // The tree first: on iOS it also starts mobilecli's agent, which the screenshot then reuses.
                elements = await this.cli.dump(device.id);
                screenshot = await this.cli.screenshot(device.id);
                this.warm = true;
            }
            this.post({ type: 'screen', device, screen: this.screen, elements, screenshot, ms: Date.now() - started });
        } finally {
            this.busy = false;
        }
    }

    private async act(action: (deviceId: string) => Promise<unknown>) {
        const device = this.requireDevice();
        await action(device.id);
        if (vscode.workspace.getConfiguration('testflyInspector').get('autoRefreshAfterAction', true)) {
            await new Promise(r => setTimeout(r, 300)); // let the UI settle (animations, navigation)
            await this.refresh();
        }
    }

    private async swipe(deviceId: string, direction: string) {
        const s = this.screen ?? (await this.cli.screenSize(deviceId));
        const cx = s.width / 2, cy = s.height / 2, dx = s.width / 4, dy = s.height / 4;
        const to: Record<string, [number, number, number, number]> = {
            up: [cx, cy + dy, cx, cy - dy],
            down: [cx, cy - dy, cx, cy + dy],
            left: [cx + dx, cy, cx - dx, cy],
            right: [cx - dx, cy, cx + dx, cy],
        };
        const [x1, y1, x2, y2] = to[direction];
        await this.cli.swipe(deviceId, x1, y1, x2, y2);
    }

    private requireDevice(): DeviceInfo {
        if (!this.device) {
            throw new Error('Choose a device first.');
        }
        return this.device;
    }

    private html(): string {
        return webviewHtml(this.webview.webview, this.context.extensionUri, {
            title: 'Mobile Inspector', styles: ['inspector.css'], scripts: ['selectors.js', 'inspector.js'], body: INSPECTOR_BODY,
        });
    }
}

// Kept in sync with media/preview.html, which renders the same body outside VS Code.
const INSPECTOR_BODY = fs.readFileSync(path.join(__dirname, '..', 'media', 'body.html'), 'utf8');
