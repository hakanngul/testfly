const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { findBrowsers, normaliseUrl } = require('../out/browsers.js');
const { attachFirstPage, closeBrowser, createIsolatedWorld, launchBrowser } = require('../out/cdp.js');

const AGENT = fs.readFileSync(path.join(__dirname, '..', 'media', 'page-agent.js'), 'utf8');
const REPO = path.join(__dirname, '..', '..', '..');

test('finds Chromium browsers on PATH and puts the configured one first', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'tf-browsers-'));
    for (const name of ['google-chrome', 'chromium-browser', 'my-chrome']) fs.writeFileSync(path.join(dir, name), '');
    try {
        const found = findBrowsers(path.join(dir, 'my-chrome'), 'linux', { PATH: dir });
        assert.deepStrictEqual(found.map(b => b.id), ['custom', 'chrome', 'chromium']);
        assert.strictEqual(found[1].path, path.join(dir, 'google-chrome'));
        assert.deepStrictEqual(findBrowsers('/nope', 'linux', { PATH: '' }), []);
    } finally {
        fs.rmSync(dir, { recursive: true, force: true });
    }
});

test('completes typed addresses', () => {
    assert.strictEqual(normaliseUrl('example.com/login'), 'https://example.com/login');
    assert.strictEqual(normaliseUrl('localhost:3000/a'), 'http://localhost:3000/a');
    assert.strictEqual(normaliseUrl('127.0.0.1:8080'), 'http://127.0.0.1:8080');
    assert.strictEqual(normaliseUrl('http://x.test'), 'http://x.test');
    assert.strictEqual(normaliseUrl('file:///tmp/a.html'), 'file:///tmp/a.html');
    assert.strictEqual(normaliseUrl('  '), 'about:blank');
});

test('the page agent mirrors Role.java and Locator.java', { skip: !fs.existsSync(path.join(REPO, 'src/main/java/io/testfly/locator/Role.java')) }, () => {
    const role = fs.readFileSync(path.join(REPO, 'src/main/java/io/testfly/locator/Role.java'), 'utf8');
    const java = [...role.matchAll(/^\s{4}([A-Z]+)\("([a-z]+)",\s*((?:"[^"]*"\s*\+?\s*)+)\)/gm)]
        .map(m => [m[1], m[2], [...m[3].matchAll(/"([^"]*)"/g)].map(x => x[1]).join('')]);
    const agent = [...AGENT.matchAll(/\['([A-Z]+)', '([a-z]+)', "([^"]*)"\]/g)].map(m => [m[1], m[2], m[3]]);
    assert.ok(java.length > 30, 'parsed Role.java');
    assert.deepStrictEqual(agent, java);

    const locator = fs.readFileSync(path.join(REPO, 'src/main/java/io/testfly/locator/Locator.java'), 'utf8');
    const cssOf = src => [...src.match(/FORM_CONTROL_CSS\s*=\s*((?:"[^"]*"\s*\+?\s*)+);/)[1].matchAll(/"([^"]*)"/g)].map(x => x[1]).join('');
    assert.strictEqual(cssOf(AGENT.replace(/var FORM_CONTROL_CSS/, 'FORM_CONTROL_CSS')), cssOf(locator));
});

const browser = process.env.TESTFLY_TEST_BROWSER || (findBrowsers()[0] || {}).path;

test('drives a real browser over the pipe and ranks TestFly locators', { skip: !browser && 'no Chrome, Edge or Chromium here' }, async () => {
    const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'tf-profile-'));
    const b = await launchBrowser({ executable: browser, userDataDir: profile, headless: true, width: 1000, height: 700 });
    try {
        assert.match(b.product, /\//);
        const sessionId = await attachFirstPage(b.cdp);
        const html = `<header><nav><a href="#p">Products</a><a href="#d">Deals</a></nav></header>
            <form id="login"><h2>Sign in</h2>
            <label for="em">Email</label><input id="em" type="email" data-testid="email" placeholder="you@example.com">
            <label><input type="checkbox" id="remember"> Remember me</label>
            <button type="submit" data-testid="sign-in">Sign in</button></form>
            <script>window.__testflyAgent = { version: 1, snapshot: () => ({ tree: [] }), details: () => ({ locators: [{ kind: 'CSS', code: 'evil()' }] }) };</script>`;
        const loaded = b.cdp.waitFor('Page.loadEventFired', 10_000, sessionId);
        await b.cdp.send('Page.navigate', { url: 'data:text/html,' + encodeURIComponent(html) }, sessionId);
        await loaded;
        // the page plants a fake agent; the inspector's isolated world never sees it
        const world = await createIsolatedWorld(b.cdp, sessionId);
        const evaluate = async call => (await b.cdp.send('Runtime.evaluate', { expression: `(${AGENT}).${call}`, returnByValue: true, contextId: world }, sessionId)).result.value;
        assert.ok((await b.cdp.send('Runtime.evaluate', { expression: 'typeof window.__testflyAgent.details', returnByValue: true }, sessionId)).result.value === 'function');

        const snap = await evaluate('snapshot({})');
        const flat = [];
        (function walk(list) { for (const n of list) { flat.push(n); walk(n.children || []); } })(snap.tree);
        const button = flat.find(n => n.testId === 'sign-in');
        assert.strictEqual(button.role, 'BUTTON');
        assert.strictEqual(button.name, 'Sign in');
        assert.ok(button.rect.width > 0 && button.visible);

        const byKind = async n => Object.fromEntries((await evaluate(`details(${n.i}, {})`)).locators.map(l => [l.kind + ' ' + l.code, l.count]));
        assert.deepStrictEqual(await byKind(button), {
            'TEST ID getByTestId("sign-in")': 1,
            'ROLE getByRole(Role.BUTTON, "Sign in")': 1,
            'TEXT getByText("Sign in")': 2, // the heading says "Sign in" too
            'CSS find("button")': 1,
        });
        const email = await byKind(flat.find(n => n.testId === 'email'));
        assert.strictEqual(email['LABEL getByLabel("Email")'], 1);
        assert.strictEqual(email['PLACEHOLDER getByPlaceholder("you@example.com")'], 1);
        assert.strictEqual(email['ID findById("em")'], 1);
        const remember = await byKind(flat.find(n => n.id === 'remember'));
        assert.strictEqual(remember['ROLE getByRole(Role.CHECKBOX, "Remember me")'], 1);

        // a custom test id attribute
        const custom = await evaluate('snapshot({ testIdAttribute: "placeholder" })');
        assert.ok(JSON.stringify(custom).includes('"testId":"you@example.com"'));
    } finally {
        await closeBrowser(b);
        fs.rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
    }
});
