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
        devices: null, busy: {}, // device id -> 'Booting' | 'Shutting down' | 'Disconnecting'
    };
    const POLL_MS = 10000;

    window.addEventListener('message', e => {
        if (e.origin !== window.location.origin || !e.data || typeof e.data !== 'object') return;
        const m = e.data;
        if (m.type === 'browsers') {
            state.browsers = m.browsers || [];
            if (!state.browsers.some(b => b.id === state.browser)) state.browser = state.browsers.length ? state.browsers[0].id : '';
            renderBrowsers();
        } else if (m.type === 'recent') {
            renderRecent(m.urls || []);
        } else if (m.type === 'deviceList') {
            state.devices = m.devices || [];
            state.listError = m.error || '';
            $('srv').className = 'srv ' + (m.error && m.unreachable ? 'off' : 'on');
            $('srv').title = m.error && m.unreachable ? 'mobilecli server is not running' : `mobilecli server${m.version ? ' ' + m.version : ''} is running`;
            renderDevices();
        } else if (m.type === 'deviceBusy') {
            if (m.action) state.busy[m.id] = m.action; else delete state.busy[m.id];
            renderDevices();
        } else if (m.type === 'wifiResult') {
            const out = $('wifiOut');
            out.hidden = false;
            out.className = 'small wifi-out ' + (m.ok ? 'ok' : 'err');
            out.textContent = m.message;
            $('wifiGo').disabled = false;
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
        if (side === 'mobile' && !state.devices) listDevices(false);
    }

    // ── devices ─────────────────────────────────────────────────────────
    const ICONS = {
        android: '<svg viewBox="0 0 16 16"><path d="M4 6.5h8v5.2a1 1 0 0 1-1 1h-.6V15h-1.6v-2.3H7.2V15H5.6v-2.3H5a1 1 0 0 1-1-1zM2.2 6.6a.8.8 0 0 1 1.6 0v3.6a.8.8 0 0 1-1.6 0zm10 0a.8.8 0 0 1 1.6 0v3.6a.8.8 0 0 1-1.6 0zM4.1 5.8a3.9 3.4 0 0 1 7.8 0zM5.3 2.2l.9 1.4M10.7 2.2l-.9 1.4"/><circle cx="6.4" cy="4.4" r=".45" fill="var(--bg)"/><circle cx="9.6" cy="4.4" r=".45" fill="var(--bg)"/></svg>',
        sim: '<svg viewBox="0 0 16 16"><rect x="1.5" y="2.5" width="13" height="8.5" rx="1.2"/><path d="M6 14h4M8 11v3"/></svg>',
        phone: '<svg viewBox="0 0 16 16"><rect x="4.5" y="1.5" width="7" height="13" rx="1.8"/><path d="M7 12h2"/></svg>',
        stop: '<svg viewBox="0 0 16 16"><path d="M8 2v6"/><path d="M4.5 4.2a5 5 0 1 0 7 0"/></svg>',
        unplug: '<svg viewBox="0 0 16 16"><path d="M3 13l3-3M13 3l-3 3M5.5 7.5l3 3M7 6l3 3-1.5 1.5a2.1 2.1 0 0 1-3-3z"/></svg>',
    };
    const GROUPS = [
        { title: 'Android', test: d => d.platform === 'android' },
        { title: 'iOS simulators', test: d => d.platform === 'ios' && d.type === 'simulator' },
        { title: 'iOS devices', test: d => d.platform === 'ios' && d.type !== 'simulator' },
    ];
    const isWifi = d => d.platform === 'android' && /:\d+$|_adb-tls-connect/.test(d.id);

    function renderDevices() {
        const box = $('deviceList');
        box.innerHTML = '';
        if (state.listError) {
            const p = document.createElement('p');
            p.className = 'dev-error';
            p.textContent = state.listError;
            box.append(p);
        }
        const list = state.devices || [];
        if (state.devices && !list.length && !state.listError) {
            box.insertAdjacentHTML('beforeend', '<p class="muted small dev-empty">No device, emulator or simulator found. See <i>A device is missing?</i> below.</p>');
        }
        for (const g of GROUPS) {
            const items = list.filter(g.test).sort((a, b) => rank(a) - rank(b) || a.name.localeCompare(b.name));
            if (!items.length) continue;
            const h = document.createElement('div');
            h.className = 'dev-group';
            h.textContent = g.title;
            box.append(h);
            items.forEach(d => box.append(deviceRow(d)));
        }
    }

    function rank(d) {
        return d.state === 'online' ? 0 : d.state === 'offline' ? 2 : 1;
    }

    function deviceRow(d) {
        const row = document.createElement('div');
        const online = d.state === 'online';
        const busy = state.busy[d.id];
        const virtual = d.type === 'emulator' || d.type === 'simulator';
        row.className = 'dev' + (online ? '' : ' offline');
        const kind = d.type === 'emulator' ? 'Emulator' : d.type === 'simulator' ? 'Simulator' : 'Device';
        const os = (d.platform === 'ios' ? 'iOS' : 'Android') + (d.version ? ' ' + d.version : '');
        const icon = d.platform === 'android' ? 'android' : d.type === 'simulator' ? 'sim' : 'phone';
        row.innerHTML = `<span class="ico ${icon === 'android' ? 'android' : ''}">${ICONS[icon]}</span>
            <div class="dev-text"><div class="dev-name"></div><div class="dev-sub"><span class="dev-meta"></span></div></div>`;
        row.querySelector('.dev-name').textContent = d.name || d.id;
        row.querySelector('.dev-meta').textContent = `${os} · ${kind} · ${d.id}`;
        row.title = `${d.name}\n${d.model ? d.model + '\n' : ''}${d.id}`;
        const badge = text => {
            const b = document.createElement('span');
            b.className = 'badge ' + text[1];
            b.textContent = text[0];
            // the status goes under the name, so a narrow sidebar keeps the name readable
            row.querySelector('.dev-meta').before(b);
            return b;
        };
        if (isWifi(d) && online) badge(['Wi‑Fi', 'wifi']);
        if (busy) badge([busy.toUpperCase() + '…', 'busy']);
        else if (online) badge(['READY', 'ready']);
        else if (d.state !== 'offline') {
            const b = badge([d.state.toUpperCase(), 'warn']);
            b.title = d.state === 'unauthorized' ? 'Accept "Allow USB debugging" on the phone' : 'adb reports ' + d.state;
        }
        const actions = document.createElement('div');
        actions.className = 'dev-actions';
        const button = (label, title, action, iconOnly) => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.title = title;
            btn.disabled = !!busy;
            if (iconOnly) { btn.className = 'icon-only'; btn.innerHTML = ICONS[label]; } else btn.textContent = label;
            btn.addEventListener('click', () => vscode.postMessage({ type: action, id: d.id }));
            actions.append(btn);
        };
        if (online) {
            button('Inspect', 'Open the Mobile Inspector on ' + d.name, 'inspect');
            if (virtual) button('stop', 'Shut down ' + d.name, 'shutdown', true);
            if (isWifi(d)) button('unplug', 'Disconnect ' + d.id + ' (adb disconnect)', 'disconnect', true);
        } else if (d.state === 'offline' && virtual) {
            button('Boot', 'Start ' + d.name, 'boot');
        }
        row.append(actions);
        return row;
    }

    function listDevices(quiet) {
        vscode.postMessage({ type: 'devices', quiet: !!quiet });
    }

    // the list follows devices that start, stop or get plugged in while the Mobile side is shown
    setInterval(() => {
        if (state.side === 'mobile' && !document.hidden && !Object.keys(state.busy).length) listDevices(true);
    }, POLL_MS);

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
    $('reloadDevices').addEventListener('click', () => listDevices(false));
    $('wifiForm').addEventListener('submit', e => {
        e.preventDefault();
        const address = $('wifiAddr').value.trim();
        if (!address) return $('wifiAddr').focus();
        $('wifiGo').disabled = true;
        $('wifiOut').hidden = false;
        $('wifiOut').className = 'small wifi-out';
        $('wifiOut').textContent = $('wifiCode').value.trim() ? 'Pairing…' : 'Connecting…';
        vscode.postMessage({ type: 'wifi', address, code: $('wifiCode').value.trim() });
    });
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
