import * as vscode from 'vscode';
import { findLocators, JavaLocator } from './javaLocators';

/** An open inspector the editor can check locators against. */
export interface LocatorTarget {
    readonly domain: 'mobile' | 'web';
    /** e.g. "Pixel 8" or "shop.example.com" */
    readonly label: string;
    /** "android" or "ios" for a mobile target. */
    readonly platform?: string;
    /** Match counts for the locators, in order; undefined while the target has nothing read yet. */
    count(locators: JavaLocator[]): Promise<LensCount[] | undefined>;
    /** Highlights the locator's matches in the inspector. */
    reveal(locator: JavaLocator): void;
}

export interface LensCount {
    count?: number;
    error?: string;
}

/**
 * Code lenses over TestFly locators in Java files: "✓ 1 match on Pixel 8", "⚠ 3 matches", "✗ no match".
 * They appear only while a Mobile or Browser Inspector is open and refresh after each read of its screen or page.
 */
export class LocatorLensProvider implements vscode.CodeLensProvider {
    static readonly command = 'testflyInspector.revealLocator';
    private static instance?: LocatorLensProvider;

    private readonly targets = new Map<'mobile' | 'web', LocatorTarget>();
    private readonly changed = new vscode.EventEmitter<void>();
    readonly onDidChangeCodeLenses = this.changed.event;

    static register(context: vscode.ExtensionContext) {
        const provider = (LocatorLensProvider.instance = new LocatorLensProvider());
        context.subscriptions.push(
            vscode.languages.registerCodeLensProvider({ language: 'java' }, provider),
            vscode.commands.registerCommand(LocatorLensProvider.command, (domain: 'mobile' | 'web', locator: JavaLocator) =>
                provider.targets.get(domain)?.reveal(locator)),
            vscode.workspace.onDidChangeConfiguration(e => e.affectsConfiguration('testflyInspector.codeLens') && provider.changed.fire()),
            provider.changed,
        );
    }

    /** An inspector opened, read a new screen or page, or closed (target undefined). */
    static update(domain: 'mobile' | 'web', target: LocatorTarget | undefined) {
        const p = LocatorLensProvider.instance;
        if (!p) {
            return;
        }
        if (target) {
            p.targets.set(domain, target);
        } else {
            p.targets.delete(domain);
        }
        p.changed.fire();
    }

    async provideCodeLenses(document: vscode.TextDocument, token: vscode.CancellationToken): Promise<vscode.CodeLens[]> {
        if (!this.targets.size || !vscode.workspace.getConfiguration('testflyInspector').get('codeLens', true)) {
            return [];
        }
        const found = findLocators(document.getText());
        const lenses: vscode.CodeLens[] = [];
        for (const [domain, target] of this.targets) {
            const mine = found.filter(l => (domain === 'mobile' ? !!l.mobile : !!l.web));
            if (!mine.length || token.isCancellationRequested) {
                continue;
            }
            const counts = await target.count(mine).catch(e => mine.map(() => ({ error: (e as Error).message })));
            if (!counts) {
                continue;
            }
            mine.forEach((l, i) => {
                const range = document.lineAt(document.positionAt(l.offset).line).range;
                lenses.push(new vscode.CodeLens(range, {
                    title: lensTitle(counts[i], target, l),
                    tooltip: `${l.source}\nClick to show the matches in the ${domain === 'mobile' ? 'Mobile' : 'Browser'} Inspector`,
                    command: LocatorLensProvider.command,
                    arguments: [domain, l],
                }));
            });
        }
        return lenses;
    }
}

function lensTitle(c: LensCount, target: LocatorTarget, l: JavaLocator): string {
    const on = ` on ${target.label}`;
    if (l.mobile && target.platform && !selectorFor(l, target.platform)) {
        return `$(circle-slash) no ${target.platform === 'ios' ? 'iOS' : 'Android'} selector`;
    }
    if (c.error) {
        return `$(error) ${c.error}`;
    }
    if (c.count === undefined || c.count < 0) {
        return `$(sync) not read yet${on}`;
    }
    if (c.count === 1) {
        return `$(pass) 1 match${on}`;
    }
    return c.count === 0 ? `$(error) no match${on}` : `$(warning) ${c.count} matches${on}`;
}

/** The selector a @MobileFind field uses on the platform. */
export function selectorFor(l: JavaLocator, platform: string): string | undefined {
    return platform === 'ios' ? l.mobile?.ios : l.mobile?.android;
}
