import * as fs from 'fs';
import * as path from 'path';

/** A Chromium-based browser the inspector can drive over the DevTools protocol. */
export interface BrowserInfo {
    id: string;
    name: string;
    path: string;
}

interface Candidate {
    id: string;
    name: string;
    paths: string[];
}

/** Installed Chrome, Edge and Chromium builds, plus the configured path first when it exists. */
export function findBrowsers(customPath = '', platform: NodeJS.Platform = process.platform, env: NodeJS.ProcessEnv = process.env): BrowserInfo[] {
    const found: BrowserInfo[] = [];
    if (customPath && isFile(customPath)) {
        found.push({ id: 'custom', name: displayName(customPath), path: customPath });
    }
    for (const c of candidates(platform, env)) {
        const hit = c.paths.find(isFile);
        if (hit && !found.some(b => b.path === hit)) {
            found.push({ id: c.id, name: c.name, path: hit });
        }
    }
    return found;
}

function candidates(platform: NodeJS.Platform, env: NodeJS.ProcessEnv): Candidate[] {
    if (platform === 'darwin') {
        const app = (bundle: string, exe: string) => [`/Applications/${bundle}.app/Contents/MacOS/${exe}`,
            path.join(env.HOME ?? '', `Applications/${bundle}.app/Contents/MacOS/${exe}`)];
        return [
            { id: 'chrome', name: 'Chrome', paths: app('Google Chrome', 'Google Chrome') },
            { id: 'edge', name: 'Edge', paths: app('Microsoft Edge', 'Microsoft Edge') },
            { id: 'chromium', name: 'Chromium', paths: app('Chromium', 'Chromium') },
        ];
    }
    if (platform === 'win32') {
        const roots = [env['PROGRAMFILES'], env['PROGRAMFILES(X86)'], env['LOCALAPPDATA']].filter((r): r is string => !!r);
        const under = (rel: string) => roots.map(r => path.win32.join(r, rel));
        return [
            { id: 'chrome', name: 'Chrome', paths: under('Google\\Chrome\\Application\\chrome.exe') },
            { id: 'edge', name: 'Edge', paths: under('Microsoft\\Edge\\Application\\msedge.exe') },
            { id: 'chromium', name: 'Chromium', paths: under('Chromium\\Application\\chrome.exe') },
        ];
    }
    const onPath = (...names: string[]) => names.flatMap(n => (env.PATH ?? '').split(path.delimiter).filter(Boolean).map(d => path.join(d, n)));
    return [
        { id: 'chrome', name: 'Chrome', paths: onPath('google-chrome', 'google-chrome-stable') },
        { id: 'edge', name: 'Edge', paths: onPath('microsoft-edge', 'microsoft-edge-stable') },
        { id: 'chromium', name: 'Chromium', paths: onPath('chromium', 'chromium-browser') },
    ];
}

function displayName(executable: string): string {
    const base = path.basename(executable).replace(/\.(exe|app)$/i, '');
    if (/edge/i.test(base)) {
        return 'Edge';
    }
    if (/chromium/i.test(base)) {
        return 'Chromium';
    }
    return /chrome/i.test(base) ? 'Chrome' : base;
}

function isFile(p: string): boolean {
    try {
        return fs.statSync(p).isFile();
    } catch {
        return false;
    }
}

/** "example.com" → "https://example.com"; local paths and other schemes stay as typed. */
export function normaliseUrl(url: string): string {
    const u = url.trim();
    if (!u) {
        return 'about:blank';
    }
    if (/^[a-z][a-z0-9+.-]*:\/\//i.test(u) || /^(about|data|file|chrome|edge):/i.test(u)) {
        return u;
    }
    if (/^(localhost|127\.|\[::1\]|0\.0\.0\.0)/i.test(u)) {
        return 'http://' + u;
    }
    return 'https://' + u;
}
