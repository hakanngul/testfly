// Renders the inspector webview outside VS Code with a fake device, for design checks:
//   node test/preview.js [out-dir]   -> preview-dark.png, preview-light.png (needs Playwright + Chromium)
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const media = f => fs.readFileSync(path.join(__dirname, '..', 'media', f), 'utf8');
const W = 1080, H = 2400;

const el = (type, rect, extra = {}, children) => ({ type, rect: { x: rect[0], y: rect[1], width: rect[2], height: rect[3] }, ...extra, ...(children ? { children } : {}) });
const tree = [el('android.widget.FrameLayout', [0, 0, W, H], {}, [
    el('android.widget.LinearLayout', [0, 0, W, 300], { identifier: 'com.demo:id/header' }, [
        el('android.widget.ImageView', [48, 110, 300, 90], { identifier: 'com.demo:id/logo', label: 'Demo' }),
        el('android.widget.Button', [700, 110, 330, 100], { identifier: 'com.demo:id/btnLogin', text: 'Giriş Yap' }),
    ]),
    el('android.widget.TextView', [60, 420, 960, 90], { identifier: 'com.demo:id/tvTitle', text: 'Hoş geldiniz' }),
    el('android.widget.EditText', [60, 600, 960, 150], { identifier: 'com.demo:id/etUserName', placeholder: 'Üye numarası' }),
    el('android.widget.EditText', [60, 800, 960, 150], { identifier: 'com.demo:id/etPassword', placeholder: 'Şifre' }),
    el('android.widget.CheckBox', [60, 1000, 500, 100], { identifier: 'com.demo:id/cbRemember', text: 'Beni hatırla', checked: true }),
    el('android.widget.Button', [60, 1180, 960, 160], { identifier: 'com.demo:id/btnSubmit', text: 'Giriş', enabled: false }),
    el('androidx.recyclerview.widget.RecyclerView', [0, 1450, W, 800], { identifier: 'com.demo:id/rvSports' }, [
        el('android.widget.TextView', [60, 1500, 300, 90], { identifier: 'com.demo:id/tvTitle', text: 'Futbol' }),
        el('android.widget.TextView', [60, 1640, 300, 90], { identifier: 'com.demo:id/tvTitle', text: 'Basketbol' }),
        el('android.widget.TextView', [60, 1780, 300, 90], { identifier: 'com.demo:id/tvTitle', text: 'Tenis' }),
    ]),
])];
let n = 0;
(function refs(list) { for (const e of list) { e.ref = '@e' + ++n; refs(e.children || []); } })(tree);

const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" font-family="Roboto, Arial" >
<rect width="${W}" height="${H}" fill="#f4f6f8"/>
<rect width="${W}" height="300" fill="#0b5d4f"/>
<text x="60" y="180" font-size="72" font-weight="700" fill="#fff">Demo<tspan fill="#f2c94c">Bet</tspan></text>
<rect x="700" y="110" width="330" height="100" rx="18" fill="#f2c94c"/><text x="865" y="178" font-size="40" font-weight="700" text-anchor="middle">Giriş Yap</text>
<text x="60" y="490" font-size="60" font-weight="700" fill="#1d2b36">Hoş geldiniz</text>
<rect x="60" y="600" width="960" height="150" rx="20" fill="#fff" stroke="#d5dbe1" stroke-width="3"/><text x="100" y="695" font-size="44" fill="#9aa5b1">Üye numarası</text>
<rect x="60" y="800" width="960" height="150" rx="20" fill="#fff" stroke="#d5dbe1" stroke-width="3"/><text x="100" y="895" font-size="44" fill="#9aa5b1">Şifre</text>
<rect x="60" y="1020" width="60" height="60" rx="10" fill="#0b5d4f"/><text x="150" y="1068" font-size="44" fill="#1d2b36">Beni hatırla</text>
<rect x="60" y="1180" width="960" height="160" rx="24" fill="#b8c4cf"/><text x="540" y="1282" font-size="52" font-weight="700" fill="#fff" text-anchor="middle">Giriş</text>
<rect x="0" y="1450" width="${W}" height="800" fill="#fff"/>
<text x="60" y="1560" font-size="48" fill="#1d2b36">⚽  Futbol</text><text x="60" y="1700" font-size="48" fill="#1d2b36">🏀  Basketbol</text><text x="60" y="1840" font-size="48" fill="#1d2b36">🎾  Tenis</text>
</svg>`;
const screenshot = 'data:image/svg+xml;base64,' + Buffer.from(svg).toString('base64');

const light = `:root{--vscode-editor-background:#ffffff;--vscode-sideBar-background:#f6f7f9;--vscode-foreground:#1f2328;--vscode-descriptionForeground:#646b75;
--vscode-panel-border:rgba(0,0,0,.12);--vscode-input-background:#fff;--vscode-input-border:rgba(0,0,0,.18);--vscode-list-hoverBackground:rgba(0,0,0,.05);
--vscode-list-activeSelectionBackground:#dbe9fb;--vscode-badge-background:#e6e8eb;--vscode-badge-foreground:#30363d;--vscode-button-secondaryBackground:rgba(0,0,0,.06);
--vscode-notifications-background:#fff;}`;

const sha = text => `'sha256-${require('crypto').createHash('sha256').update(text).digest('base64')}'`;

function page(theme) {
    const css = media('inspector.css') + (theme === 'light' ? light : '');
    const scripts = [`
  window.acquireVsCodeApi = () => ({ getState: () => null, setState() {}, postMessage(m) {
    if (m.type === 'ready') setTimeout(() => {
      const device = { id: 'Pixel_8_API_34', name: 'Pixel 8', platform: 'android', type: 'emulator', state: 'online', version: '14' };
      window.postMessage({ type: 'devices', devices: [device] });
      window.postMessage({ type: 'device', device });
      window.postMessage({ type: 'screen', device, screen: { width: ${W}, height: ${H}, scale: 1 }, elements: ${JSON.stringify(tree)}, screenshot: '${screenshot}', ms: 412 });
    });
  }});
`, media('selectors.js'), media('inspector.js')];
    // same rules as the extension's webview CSP: no inline style="" attributes, no unlisted scripts
    const csp = `default-src 'none'; img-src data:; style-src ${sha(css)}; script-src ${scripts.map(sha).join(' ')};`;
    return `<!DOCTYPE html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="${csp}"><style>${css}</style></head>
<body>${media('body.html')}
${scripts.map(s => `<script>${s}</script>`).join('')}</body></html>`;
}

(async () => {
    const out = process.argv[2] || '.';
    const browser = await chromium.launch();
    for (const theme of ['dark', 'light']) {
        const p = await browser.newPage({ viewport: { width: 1440, height: 860 }, deviceScaleFactor: 2 });
        await p.setContent(page(theme));
        await p.waitForTimeout(400);
        // hover one element and select another, like a user would
        const shot = await p.locator('#shot').boundingBox();
        await p.mouse.click(shot.x + shot.width * 0.5, shot.y + shot.height * 0.29); // username field
        await p.mouse.move(shot.x + shot.width * 0.84, shot.y + shot.height * 0.066); // header login button
        await p.waitForTimeout(250);
        await p.screenshot({ path: path.join(out, `preview-${theme}.png`) });
        await p.close();
    }
    await browser.close();
})();
