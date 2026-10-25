import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import { BrowserPanel } from './browserPanel';
import { findBrowsers } from './browsers';
import { webviewHtml } from './webview';

const RECENT_KEY = 'testflyInspector.recentUrls';

/** Messages the sidebar sends; see media/sidebar.js. */
type Inbound =
    | { type: 'ready' | 'rescan' | 'openMobile' | 'settings' }
    | { type: 'launch'; browser: string; url: string; width: number; height: number; headless: boolean; keepSession: boolean };

/** The activity-bar view: a Mobile / Browser switch that opens either inspector. */
export class SidebarProvider implements vscode.WebviewViewProvider {
    static readonly viewId = 'testflyInspector.home';
    private view?: vscode.WebviewView;

    constructor(private readonly context: vscode.ExtensionContext) {}

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

    private async handle(m: Inbound) {
        switch (m.type) {
            case 'ready':
            case 'rescan':
                this.post({ type: 'browsers', browsers: this.browsers() });
                this.post({ type: 'recent', urls: this.context.globalState.get<string[]>(RECENT_KEY, []) });
                return;
            case 'openMobile':
                return vscode.commands.executeCommand('testflyInspector.open');
            case 'settings':
                return vscode.commands.executeCommand('workbench.action.openSettings', 'testflyInspector');
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
