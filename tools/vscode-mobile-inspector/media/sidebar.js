// TestFly sidebar: Mobile / Browser switch. Starts the Mobile Inspector or launches a browser for the Browser Inspector.
(function () {
    'use strict';
    const vscode = acquireVsCodeApi();
    const $ = id => document.getElementById(id);
    const saved = vscode.getState() || {};
    const state = {
        side: saved.side || 'mobile',
        url: saved.url || '',
        browser: saved.browser || '',
        viewport: saved.viewport || '1440x900',
        showWindow: !!saved.showWindow, // default: no window, the page shows only in the inspector
        keepSession: saved.keepSession !== false,
        browsers: [],
    };

    window.addEventListener('message', e => {
        if (e.origin !== window.location.origin || !e.data || typeof e.data !== 'object') return;
        const m = e.data;
        if (m.type === 'browsers') {
            state.browsers = m.browsers || [];
            if (!state.browsers.some(b => b.id === state.browser)) state.browser = state.browsers.length ? state.browsers[0].id : '';
            renderBrowsers();
        } else if (m.type === 'recent') {
            renderRecent(m.urls || []);
        }
    });

    function persist() {
        vscode.setState({ side: state.side, url: state.url, browser: state.browser, viewport: state.viewport, showWindow: state.showWindow, keepSession: state.keepSession });
    }

    function setSide(side) {
        state.side = side;
        document.querySelectorAll('[data-side]').forEach(b => {
            b.classList.toggle('active', b.dataset.side === side);
            b.setAttribute('aria-selected', String(b.dataset.side === side));
        });
        $('sideMobile').hidden = side !== 'mobile';
        $('sideBrowser').hidden = side !== 'browser';
        persist();
    }

    function renderBrowsers() {
        const box = $('browsers');
        box.innerHTML = '';
        if (!state.browsers.length) {
            box.innerHTML = '<p class="none-found">No Chrome, Edge or Chromium found. Install one, or set <button type="button" class="link" data-settings>testflyInspector.browserPath</button>.</p>';
        }
        for (const b of state.browsers) {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'pick' + (b.id === state.browser ? ' active' : '');
            btn.title = b.path;
            const logo = b.name.toLowerCase();
            btn.innerHTML = `<i class="logo ${['chrome', 'edge', 'chromium'].includes(logo) ? logo : ''}"></i><span></span>`;
            btn.querySelector('span').textContent = b.name;
            btn.addEventListener('click', () => {
                state.browser = b.id;
                persist();
                renderBrowsers();
            });
            box.append(btn);
        }
        $('launch').disabled = !state.browsers.length;
    }

    function renderRecent(urls) {
        $('recentBox').hidden = !urls.length;
        const box = $('recent');
        box.innerHTML = '';
        for (const url of urls) {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.title = 'Open ' + url;
            btn.innerHTML = '<svg viewBox="0 0 16 16"><circle cx="8" cy="8" r="5.5"/><path d="M8 5v3l2 1.5"/></svg><span></span>';
            btn.querySelector('span').textContent = url.replace(/^https?:\/\//, '');
            btn.addEventListener('click', () => {
                $('pageUrl').value = state.url = url;
                persist();
                launch();
            });
            box.append(btn);
        }
    }

    function launch() {
        const url = $('pageUrl').value.trim();
        if (!url) {
            $('pageUrl').focus();
            return;
        }
        const [width, height] = state.viewport.split('x').map(Number);
        vscode.postMessage({ type: 'launch', browser: state.browser, url, width, height, headless: !state.showWindow, keepSession: state.keepSession });
    }

    document.querySelectorAll('[data-side]').forEach(b => b.addEventListener('click', () => setSide(b.dataset.side)));
    $('openMobile').addEventListener('click', () => vscode.postMessage({ type: 'openMobile' }));
    document.addEventListener('click', e => {
        if (e.target.closest('[data-settings]')) vscode.postMessage({ type: 'settings' });
    });
    $('rescan').addEventListener('click', () => vscode.postMessage({ type: 'rescan' }));
    $('pageUrl').value = state.url;
    $('pageUrl').addEventListener('input', e => { state.url = e.target.value; persist(); });
    $('viewportSel').value = state.viewport;
    $('viewportSel').addEventListener('change', e => { state.viewport = e.target.value; persist(); });
    $('showWindow').checked = state.showWindow;
    $('showWindow').addEventListener('change', e => { state.showWindow = e.target.checked; persist(); });
    $('clearSession').addEventListener('click', () => vscode.postMessage({ type: 'clearSession' }));
    $('keepSession').checked = state.keepSession;
    $('keepSession').addEventListener('change', e => { state.keepSession = e.target.checked; persist(); });
    $('launchForm').addEventListener('submit', e => { e.preventDefault(); launch(); });

    setSide(state.side);
    renderBrowsers();
    vscode.postMessage({ type: 'ready' });
})();
