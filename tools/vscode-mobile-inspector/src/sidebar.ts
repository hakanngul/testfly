import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import { adbDevices, adbDisconnect, adbWifi, findAdb } from './adb';
import { BrowserPanel } from './browserPanel';
import { findBrowsers } from './browsers';
import { DeviceInfo, MobileCli, RpcError } from './mobilecli';
import { webviewHtml } from './webview';

const RECENT_KEY = 'testflyInspector.recentUrls';

/** Messages the sidebar sends; see media/sidebar.js. */
type Inbound =
    | { type: 'ready' | 'rescan' | 'openMobile' | 'settings' | 'clearSession' }
    | { type: 'devices'; quiet?: boolean }
    | { type: 'inspect' | 'boot' | 'shutdown' | 'disconnect'; id: string }
    | { type: 'wifi'; address: string; code?: string }
    | { type: 'launch'; browser: string; url: string; width: number; height: number; headless: boolean; keepSession: boolean };

/** The activity-bar view: a Mobile / Browser switch that opens either inspector. */
export class SidebarProvider implements vscode.WebviewViewProvider {
    static readonly viewId = 'testflyInspector.home';
    private view?: vscode.WebviewView;
    private cli?: MobileCli;
    private listing?: Promise<void>;

    constructor(private readonly context: vscode.ExtensionContext) {}

    dispose() {
        this.cli?.dispose();
    }

    private mobilecli(): MobileCli {
        const cfg = vscode.workspace.getConfiguration('testflyInspector');
        const url = cfg.get('serverUrl', 'http://localhost:12000');
        if (!this.cli || this.cli.endpoint !== url.replace(/\/+$/, '') + '/rpc') {
            this.cli?.dispose();
            this.cli = new MobileCli(url, cfg.get('mobilecliPath', 'mobilecli'), cfg.get('autoStartServer', true));
        }
        return this.cli;
    }

    /**
     * Every device mobilecli knows, offline emulators and simulators too, plus the Android devices adb lists but
     * mobilecli leaves out (unauthorized, offline), so a phone waiting for "Allow USB debugging" is not invisible.
     * quiet: a poll, which never starts the server.
     */
    private listDevices(quiet: boolean): Promise<void> {
        this.listing ??= (async () => {
            const cli = this.mobilecli();
            let devices: DeviceInfo[] = [];
            let version = '';
            try {
                version = await cli.ensureServer(undefined, quiet ? false : undefined);
                devices = await cli.devices(true);
            } catch (e) {
                this.post({ type: 'deviceList', devices: [], error: (e as Error).message, unreachable: e instanceof RpcError && e.unreachable });
                return;
            }
            try {
                for (const a of await adbDevices(findAdb())) {
                    if (a.state !== 'device' && !devices.some(d => d.id === a.serial)) {
                        devices.push({ id: a.serial, name: a.model ?? a.serial, platform: 'android', type: a.serial.startsWith('emulator-') ? 'emulator' : 'real', state: a.state });
                    }
                }
            } catch {
                // no adb here: mobilecli's list is all there is
            }
            this.post({ type: 'deviceList', devices, version });
        })().finally(() => (this.listing = undefined));
        return this.listing;
    }

    private async deviceAction(id: string, label: string, action: () => Promise<unknown>) {
        this.post({ type: 'deviceBusy', id, action: label });
        try {
            await action();
        } catch (e) {
            void vscode.window.showErrorMessage(`${label} ${id} failed: ${(e as Error).message}`);
        } finally {
            this.post({ type: 'deviceBusy', id });
            await this.listDevices(true);
        }
    }

    resolveWebviewView(view: vscode.WebviewView) {
        this.view = view;
        view.webview.options = { enableScripts: true, localResourceRoots: [vscode.Uri.joinPath(this.context.extensionUri, 'media')] };
        view.webview.html = webviewHtml(view.webview, this.context.extensionUri, {
            title: 'TestFly',
            styles: ['inspector.css', 'sidebar.css'],
            scripts: ['sidebar.js'],
            body: fs.readFileSync(path.join(this.context.extensionPath, 'media', 'sidebar-body.html'), 'utf8'),
        });
        view.webview.onDidReceiveMessage(m => this.handle(m as Inbound), undefined, this.context.subscriptions);
    }

    private post(message: unknown) {
        void this.view?.webview.postMessage(message);
    }

    private browsers() {
        return findBrowsers(vscode.workspace.getConfiguration('testflyInspector').get('browserPath', ''));
    }

    private async clearSession() {
        if (BrowserPanel.usesKeptSession()) {
            void vscode.window.showWarningMessage('Close the browser first (Close browser in the Browser Inspector), then clear the session.');
            return;
        }
        await fs.promises.rm(BrowserPanel.profileRoot(this.context), { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
        void vscode.window.showInformationMessage('Saved browser session cleared: the next launch starts without cookies or logins.');
    }

    private async handle(m: Inbound) {
        switch (m.type) {
            case 'ready':
            case 'rescan':
                this.post({ type: 'browsers', browsers: this.browsers() });
                this.post({ type: 'recent', urls: this.context.globalState.get<string[]>(RECENT_KEY, []) });
                return;
            case 'openMobile':
                return vscode.commands.executeCommand('testflyInspector.open');
            case 'devices':
                return this.listDevices(!!m.quiet);
            case 'inspect':
                return vscode.commands.executeCommand('testflyInspector.open', m.id);
            case 'boot':
                return this.deviceAction(m.id, 'Booting', () => this.mobilecli().boot(m.id));
            case 'shutdown':
                return this.deviceAction(m.id, 'Shutting down', () => this.mobilecli().shutdown(m.id));
            case 'disconnect':
                return this.deviceAction(m.id, 'Disconnecting', () => adbDisconnect(findAdb(), m.id));
            case 'wifi':
                try {
                    const out = await adbWifi(findAdb(), m.address, m.code);
                    this.post({ type: 'wifiResult', ok: true, message: m.code ? `${out} Now connect with the address on the Wireless debugging screen (without a code).` : out });
                } catch (e) {
                    this.post({ type: 'wifiResult', ok: false, message: (e as Error).message });
                }
                return this.listDevices(true);
            case 'settings':
                return vscode.commands.executeCommand('workbench.action.openSettings', 'testflyInspector');
            case 'clearSession':
                return this.clearSession();
            case 'launch': {
                const browser = this.browsers().find(b => b.id === m.browser) ?? this.browsers()[0];
                if (!browser) {
                    void vscode.window.showErrorMessage('No Chrome, Edge or Chromium found. Install one or set testflyInspector.browserPath.');
                    return;
                }
                await rememberUrl(this.context, m.url);
                this.post({ type: 'recent', urls: this.context.globalState.get<string[]>(RECENT_KEY, []) });
                BrowserPanel.show(this.context, { browser, url: m.url, width: m.width, height: m.height, headless: m.headless, keepSession: m.keepSession });
                return;
            }
        }
    }
}

export async function rememberUrl(context: vscode.ExtensionContext, url: string) {
    const list = context.globalState.get<string[]>(RECENT_KEY, []).filter(u => u !== url);
    await context.globalState.update(RECENT_KEY, [url, ...list].slice(0, 6));
}

export function recentUrls(context: vscode.ExtensionContext): string[] {
    return context.globalState.get<string[]>(RECENT_KEY, []);
}
