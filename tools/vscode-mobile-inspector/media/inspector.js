// TestFly Mobile Inspector webview. Talks to the extension host with postMessage; the host talks to mobilecli.
(function () {
    'use strict';
    const vscode = acquireVsCodeApi();
    const S = window.TestFlySelectors;
    const $ = id => document.getElementById(id);

    const ICON = {
        copy: '<svg viewBox="0 0 16 16"><rect x="5" y="5" width="8.5" height="8.5" rx="1.5"/><path d="M10.5 5V3.5A1.5 1.5 0 0 0 9 2H3.5A1.5 1.5 0 0 0 2 3.5V9A1.5 1.5 0 0 0 3.5 10.5H5"/></svg>',
        java: '<svg viewBox="0 0 16 16"><path d="M5.5 4L2 8l3.5 4"/><path d="M10.5 4L14 8l-3.5 4"/></svg>',
        chev: '<svg viewBox="0 0 16 16" class="chev"><path d="M6 4l4 4-4 4"/></svg>',
        tap: '<svg viewBox="0 0 16 16"><circle cx="8" cy="8" r="2.2"/><circle cx="8" cy="8" r="5.5"/></svg>',
        hold: '<svg viewBox="0 0 16 16"><circle cx="8" cy="8" r="5.5"/><path d="M8 5v3l2 1.5"/></svg>',
        term: '<svg viewBox="0 0 16 16"><path d="M3 4.5l3.5 3.5L3 11.5"/><path d="M8.5 12h4.5"/></svg>',
        type: '<svg viewBox="0 0 16 16"><path d="M3 4h10M8 4v9"/></svg>',
    };
    // Badges for interactive roles; other roles (list, listitem, header, …) show as containers.
    const KIND_LABEL = { button: 'B', textfield: 'T', text: 'A', image: 'I', switch: 'S', checkbox: 'C', radio: 'R', slider: 'Sl', combobox: 'Cb', link: 'L', tab: 'Tb' };
    // Appium Inspector's strategies as TestFly selectors (MobileScreen.locate / @MobileFind syntax).
    const STRATEGY = {
        testfly: { placeholder: 'loginButton, text=Sign in, role=button;name=Go, //…', selector: q => (/^[/(]/.test(q) ? 'xpath=' + q : q) },
        id: { placeholder: 'com.app:id/login or login', selector: q => 'id=' + q },
        accessibility: { placeholder: 'accessibility id, e.g. Login', selector: q => 'accessibility=' + q },
        xpath: { placeholder: "//android.widget.Button[@text='Go']", selector: q => 'xpath=' + q },
        type: { placeholder: 'android.widget.EditText or XCUIElementTypeButton', selector: q => 'type=' + q },
        text: { placeholder: 'exact text, e.g. Sign in', selector: q => 'text=' + q },
        contains: { placeholder: 'any part of a text, id, type or @ref', selector: null },
    };
    const INDENT = 16; // px per tree level; .row .indent guide lines in inspector.css use the same step

    const saved = vscode.getState() || {};
    const state = {
        devices: [], device: null, screen: null, elements: [], flat: [], paths: new Map(),
        selected: null, hovered: null, expanded: new Set(saved.expanded || []), collapsedAll: false,
        mode: saved.mode || 'inspect', tab: 'tree', xml: null, xmlNodes: new Map(), snapshot: null, ms: 0, steps: [],
        find: { strategy: STRATEGY[saved.strategy] ? saved.strategy : 'testfly', query: '', selector: '', matches: [], set: new Set(), current: -1, error: '' },
    };

    // ── host messages ───────────────────────────────────────────────────
    window.addEventListener('message', e => {
        // VS Code relays extension messages from its own webview origin; e.source matches no window here
        if (e.origin !== window.location.origin || !e.data || typeof e.data !== 'object') return;
        const m = e.data;
        switch (m.type) {
            case 'server': return onServer(m.running, m.version);
            case 'devices': return onDevices(m.devices);
            case 'device': return onDevice(m.device);
            case 'screen': return onScreen(m);
            case 'shot': $('shot').src = imageSrc(m.screenshot); return; // early picture; the tree comes with 'screen'
            case 'snapshotText': state.snapshot = m.text; return renderSnapshot();
            case 'status': return setStatus(m.state, m.message);
            case 'error': return onError(m.message, m.unreachable);
        }
    });

    function send(message) {
        vscode.postMessage(message);
    }

    function persist() {
        vscode.setState({ mode: state.mode, expanded: [...state.expanded], cols: saved.cols, strategy: state.find.strategy });
    }

    // ── status & toasts ─────────────────────────────────────────────────
    function setStatus(kind, text) {
        $('status').dataset.state = kind;
        $('statusText').textContent = text;
        $('loadingBar').hidden = kind !== 'loading' && kind !== 'connecting';
        $('stage').classList.toggle('stale', kind === 'loading');
        if (kind === 'idle' || kind === 'connecting') {
            $('emptyHint').textContent = text;
        }
    }

    let toastTimer;
    function toast(text, error) {
        const t = $('toast');
        t.textContent = text;
        t.classList.toggle('error', !!error);
        t.classList.add('show');
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => t.classList.remove('show'), error ? 5000 : 1800);
    }

    function onError(message, unreachable) {
        $('server').classList.remove('busy');
        setStatus('error', unreachable ? 'mobilecli not reachable' : 'Error');
        toast(message, true);
        if (!state.elements.length) {
            $('emptyHint').textContent = message;
        }
    }

    function copy(text, label) {
        send({ type: 'copy', text, label });
        toast('Copied ' + label);
    }

    // ── server ──────────────────────────────────────────────────────────
    function onServer(running, version) {
        const b = $('server');
        b.classList.remove('busy');
        b.dataset.running = String(running);
        $('serverText').textContent = running ? 'Server on' : 'Server off';
        b.title = running ? `mobilecli server${version ? ' ' + version : ''} is running. Click to stop it.`
            : 'mobilecli server is not running. Click to start it.';
        if (!running) {
            state.device = null;
            $('platform').hidden = true;
        }
    }

    $('server').addEventListener('click', () => {
        const b = $('server');
        if (b.classList.contains('busy')) return;
        b.classList.add('busy');
        send({ type: 'server', action: b.dataset.running === 'true' ? 'stop' : 'start' });
    });

    // ── devices ─────────────────────────────────────────────────────────
    function onDevices(devices) {
        state.devices = devices;
        const sel = $('device');
        sel.innerHTML = '';
        if (!devices.length) {
            sel.append(new Option('No online device', ''));
        } else {
            if (!state.device) sel.append(new Option('Choose a device…', ''));
            for (const d of devices) {
                const label = `${d.name}  ·  ${d.platform === 'ios' ? 'iOS' : 'Android'}${d.version ? ' ' + d.version : ''}  ·  ${d.type}`;
                sel.append(new Option(label, d.id));
            }
        }
        if (state.device) sel.value = state.device.id;
    }

    function onDevice(device) {
        state.device = device;
        state.selected = null;
        $('device').value = device.id;
        const badge = $('platform');
        badge.hidden = false;
        badge.dataset.platform = device.platform;
        badge.textContent = device.platform === 'ios' ? 'iOS' : 'Android';
    }

    // ── screen ──────────────────────────────────────────────────────────
    function onScreen(m) {
        const previous = state.selected;
        state.device = m.device;
        state.elements = m.elements || [];
        state.flat = S.flatten(state.elements);
        state.paths = indexPaths(state.elements);
        state.ms = m.ms;
        state.snapshot = null;
        state.xml = buildXml(state.elements);
        state.hovered = null;
        state.selected = previous ? findSame(previous) : null;

        const img = $('shot');
        img.onload = () => {
            const W = m.screen && m.screen.width || img.naturalWidth;
            const H = m.screen && m.screen.height || img.naturalHeight;
            state.screen = { width: W, height: H };
            $('screen').style.setProperty('--ratio', `${img.naturalWidth} / ${img.naturalHeight}`);
            renderBounds();
            renderSelection();
        };
        img.src = imageSrc(m.screenshot);
        $('deviceFrame').hidden = false;
        $('emptyScreen').hidden = true;
        $('screenMeta').textContent = m.screen && m.screen.width ? `${m.screen.width}×${m.screen.height}` : '';
        $('treeCount').textContent = `${state.flat.length} elements`;
        setStatus('ok', `${m.device.name} · ${state.flat.length} elements · ${m.ms} ms`);
        if (state.find.query) runFind(true);
        renderTree();
        renderDetails();
        if (state.tab === 'snapshot') send({ type: 'snapshotText' });
    }

    /** Path keys ("0/2/1") per element, so tree state survives a refresh even though @eN refs do not. */
    function indexPaths(elements) {
        const map = new Map();
        const walk = (list, prefix) => list.forEach((e, i) => {
            const key = prefix ? prefix + '/' + i : String(i);
            map.set(e, key);
            walk(e.children || [], key);
        });
        walk(elements, '');
        return map;
    }

    /** The element in the new dump that is most likely the one selected before the refresh. */
    function findSame(old) {
        const same = e => e.type === old.type && (e.identifier || '') === (old.identifier || '');
        const byText = state.flat.filter(e => same(e) && S.displayText(e) === S.displayText(old));
        if (byText.length === 1) return byText[0];
        const byId = old.identifier ? state.flat.filter(same) : [];
        return byId.length === 1 ? byId[0] : null;
    }

    function buildXml(elements) {
        try {
            state.xmlNodes = new Map();
            return S.toXml(elements, document.implementation.createDocument(null, null, null), state.xmlNodes);
        } catch (e) {
            return null;
        }
    }

    function countXpath(expr) {
        if (!state.xml) return -1;
        try {
            return state.xml.evaluate('count(' + expr + ')', state.xml, null, XPathResult.NUMBER_TYPE, null).numberValue;
        } catch (e) {
            return -1;
        }
    }

    function pct(v, total) {
        return (100 * v / (total || 1)) + '%';
    }

    function place(box, el) {
        const r = el.rect || {};
        const s = state.screen || { width: 1, height: 1 };
        box.style.left = pct(r.x, s.width);
        box.style.top = pct(r.y, s.height);
        box.style.width = pct(r.width, s.width);
        box.style.height = pct(r.height, s.height);
    }

    function renderBounds() {
        const overlay = $('overlay');
        overlay.innerHTML = '';
        if (!$('showBounds').checked || !state.screen) return;
        const frag = document.createDocumentFragment();
        for (const el of state.flat) {
            if (!el.rect || !el.rect.width) continue;
            const b = document.createElement('div');
            b.className = 'b';
            place(b, el);
            frag.append(b);
        }
        overlay.append(frag);
    }

    function renderHover() {
        const box = $('hoverBox');
        const el = state.hovered;
        document.querySelectorAll('.row.hovered').forEach(r => r.classList.remove('hovered'));
        if (!el || !el.rect || !state.screen || el === state.selected) {
            box.hidden = true;
            return;
        }
        box.hidden = false;
        place(box, el);
        box.classList.toggle('below', el.rect.y < state.screen.height * 0.06);
        box.classList.toggle('right', el.rect.x + el.rect.width > state.screen.width * 0.7);
        const id = el.identifier ? ' #' + el.identifier.replace(/^.*:id\//, '') : '';
        box.innerHTML = `<span class="tag">${esc(el.ref || '')} ${esc(S.bareType(el.type))}${esc(id)}</span>`;
        const row = rowFor(el);
        if (row) row.classList.add('hovered');
    }

    function renderSelection() {
        const box = $('selectBox');
        const el = state.selected;
        if (!el || !el.rect || !state.screen) {
            box.hidden = true;
        } else {
            box.hidden = false;
            place(box, el);
        }
        $('selRef').hidden = !el;
        $('selRef').textContent = el ? el.ref || '' : '';
    }

    function toDevice(event) {
        const rect = $('shot').getBoundingClientRect();
        const s = state.screen;
        return {
            x: (event.clientX - rect.left) / rect.width * s.width,
            y: (event.clientY - rect.top) / rect.height * s.height,
        };
    }

    const screenEl = $('screen');
    screenEl.addEventListener('mousemove', e => {
        if (!state.screen) return;
        const p = toDevice(e);
        const hit = S.hitTest(state.elements, p.x, p.y);
        if (hit !== state.hovered) {
            state.hovered = state.mode !== 'tap' ? hit : null;
            renderHover();
        }
    });
    screenEl.addEventListener('mouseleave', () => {
        state.hovered = null;
        renderHover();
    });
    screenEl.addEventListener('click', e => {
        if (!state.screen) return;
        const p = toDevice(e);
        if (state.mode === 'record') {
            ripple(e);
            const hit = S.hitTest(state.elements, p.x, p.y);
            const r = hit && hit.rect;
            const x = r ? r.x + r.width / 2 : p.x, y = r ? r.y + r.height / 2 : p.y;
            record(hit, 'tap', { x: p.x, y: p.y });
            send({ type: 'tap', x, y });
            setStatus('loading', 'Tapping…');
        } else if (state.mode === 'tap') {
            ripple(e);
            send({ type: 'tap', x: p.x, y: p.y });
            setStatus('loading', `Tapping ${Math.round(p.x)}, ${Math.round(p.y)}…`);
        } else {
            select(S.hitTest(state.elements, p.x, p.y), true);
        }
    });

    function ripple(e) {
        const r = $('ripple');
        const rect = screenEl.getBoundingClientRect();
        r.hidden = false;
        r.style.left = (e.clientX - rect.left) + 'px';
        r.style.top = (e.clientY - rect.top) + 'px';
        r.style.animation = 'none';
        void r.offsetWidth;
        r.style.animation = '';
    }

    function select(el, reveal) {
        state.selected = el;
        state.hovered = null;
        if (el && reveal) {
            // open the ancestors so the row is visible
            const key = state.paths.get(el) || '';
            const parts = key.split('/');
            for (let i = 1; i < parts.length; i++) state.expanded.add(parts.slice(0, i).join('/'));
            persist();
        }
        renderTree();
        renderHover();
        renderSelection();
        renderDetails();
        if (el && reveal) {
            const row = rowFor(el);
            if (row) row.scrollIntoView({ block: 'nearest' });
        }
    }

    // ── tree ────────────────────────────────────────────────────────────
    // Containers have no selector role; they get a colour of their own so the tree's structure stands out.
    const LIST_TYPES = ['recyclerview', 'listview', 'gridview', 'scrollview', 'horizontalscrollview', 'nestedscrollview',
        'viewpager', 'viewpager2', 'table', 'collectionview'];
    const LAYOUT_TYPES = ['viewgroup', 'group', 'window', 'toolbar', 'navigationbar', 'tabbar', 'cardview'];

    function containerOf(el) {
        const bare = S.bareType(el.type);
        if (LIST_TYPES.includes(bare)) return 'list';
        if (bare.endsWith('layout') || LAYOUT_TYPES.includes(bare)) return 'layout';
        return 'other';
    }

    function kindOf(el) {
        const role = S.roleOf(el);
        if (role && KIND_LABEL[role]) return { role, label: KIND_LABEL[role] };
        return { role: containerOf(el), label: (S.bareType(el.type).charAt(0) || '?').toUpperCase() };
    }

    function containsText(el, q) {
        const hay = [el.ref, el.type, el.identifier, el.text, el.label, el.name, el.value, el.placeholder].filter(Boolean).join(' ').toLowerCase();
        return hay.includes(q);
    }

    function rowFor(el) {
        const key = state.paths.get(el);
        return key == null ? null : $('tree').querySelector(`.row[data-key="${key}"]`);
    }

    function renderTree() {
        const tree = $('tree');
        const frag = document.createDocumentFragment();
        const walk = (list, depth) => {
            for (const el of list) {
                const key = state.paths.get(el);
                const kids = el.children || [];
                const open = state.expanded.has(key) || depth < 2;
                frag.append(row(el, key, depth, kids.length > 0, open));
                if (kids.length && open) walk(kids, depth + 1);
            }
        };
        walk(state.elements, 0);
        tree.innerHTML = '';
        if (!frag.childNodes.length) {
            tree.innerHTML = '<div class="empty-state small"><p>The tree appears after the first refresh.</p></div>';
            return;
        }
        tree.append(frag);
    }

    function row(el, key, depth, hasKids, open) {
        const r = document.createElement('div');
        const f = state.find;
        r.className = 'row' + (el === state.selected ? ' selected' : '')
            + (f.set.has(el) ? ' match' + (f.matches[f.current] === el ? ' current' : '') : '');
        r.dataset.key = key;
        r.setAttribute('role', 'treeitem');
        if (hasKids) r.setAttribute('aria-expanded', String(open));
        const k = kindOf(el);
        const text = S.displayText(el);
        const id = el.identifier ? el.identifier.replace(/^.*:id\//, '') : '';
        r.innerHTML =
            '<span class="indent"></span>' +
            ICON.chev.replace('class="chev"', `class="chev${hasKids ? (open ? ' open' : '') : ' none'}"`) +
            `<span class="kind ${k.role}">${k.label}</span>` +
            `<span class="type">${esc(shortType(el.type))}</span>` +
            (text ? `<span class="txt">“<b>${esc(text)}</b>”</span>` : '') +
            (id ? `<span class="id">#${esc(id)}</span>` : '') +
            `<span class="ref">${esc(el.ref || '')}</span>`;
        // set through the CSSOM: the webview CSP drops inline style="" attributes
        r.querySelector('.indent').style.width = `${depth * INDENT + 4}px`;
        r.addEventListener('click', ev => {
            if (hasKids && ev.target.closest('.chev')) {
                toggle(key, !open);
                return;
            }
            select(el, false);
        });
        r.addEventListener('dblclick', () => hasKids && toggle(key, !open));
        r.addEventListener('mouseenter', () => {
            state.hovered = el;
            renderHover();
        });
        r.addEventListener('mouseleave', () => {
            state.hovered = null;
            renderHover();
        });
        return r;
    }

    function shortType(type) {
        const t = String(type || '');
        return t.includes('.') ? t.substring(t.lastIndexOf('.') + 1) : t;
    }

    function toggle(key, open) {
        if (open) state.expanded.add(key); else state.expanded.delete(key);
        persist();
        renderTree();
    }

    // ── finder (Appium Inspector's "Search for element", TestFly's matching rules) ──
    function evalXpath(expr) {
        if (!state.xml) return [];
        const res = state.xml.evaluate(expr, state.xml, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
        const out = [];
        for (let i = 0; i < res.snapshotLength; i++) {
            let n = res.snapshotItem(i);
            if (n.nodeType === 2) n = n.ownerElement; // @attribute
            else if (n.nodeType !== 1) n = n.parentNode;
            const el = state.xmlNodes.get(n);
            if (el && !out.includes(el)) out.push(el);
        }
        return out;
    }

    /** Runs the query; keep=true (after a refresh) keeps the selection instead of jumping to the first match. */
    function runFind(keep) {
        const f = state.find;
        const s = STRATEGY[f.strategy];
        f.selector = f.query && s.selector ? s.selector(f.query) : '';
        let res = { matches: [] };
        if (f.query) {
            res = s.selector ? S.resolve(f.selector, state.elements, evalXpath) : { matches: state.flat.filter(el => containsText(el, f.query.toLowerCase())) };
        }
        f.error = res.error || '';
        f.matches = res.matches || [];
        f.set = new Set(f.matches);
        f.current = keep && state.selected ? f.matches.indexOf(state.selected) : -1;
        if (!keep && f.matches.length) return step(1);
        renderTree();
        renderFindCount();
    }

    function step(dir) {
        const f = state.find;
        if (!f.matches.length) return renderFindCount();
        f.current = (f.current + dir + f.matches.length) % f.matches.length;
        renderFindCount();
        select(f.matches[f.current], true);
    }

    function renderFindCount() {
        const f = state.find;
        const el = $('findCount');
        el.textContent = !f.query ? '' : f.error ? 'Invalid' : f.matches.length ? `${f.current >= 0 ? f.current + 1 : '–'} of ${f.matches.length}` : 'No match';
        el.classList.toggle('none', !!f.query && (!!f.error || !f.matches.length));
        el.title = f.error || (f.selector ? 'TestFly selector: ' + f.selector : '');
        $('findPrev').disabled = $('findNext').disabled = f.matches.length < 2;
        $('findCopy').disabled = !f.selector || !!f.error;
    }

    let findTimer;
    $('search').addEventListener('input', e => {
        clearTimeout(findTimer);
        state.find.query = e.target.value.trim();
        findTimer = setTimeout(() => runFind(false), 150);
    });
    $('search').addEventListener('keydown', e => {
        if (e.key === 'Enter') {
            e.preventDefault();
            clearTimeout(findTimer);
            if (state.find.query !== e.target.value.trim()) {
                state.find.query = e.target.value.trim();
                return runFind(false);
            }
            step(e.shiftKey ? -1 : 1);
        } else if (e.key === 'Escape' && e.target.value) {
            e.stopPropagation();
            e.target.value = '';
            state.find.query = '';
            runFind(false);
        }
    });
    $('strategy').addEventListener('change', e => {
        state.find.strategy = e.target.value;
        $('search').placeholder = STRATEGY[state.find.strategy].placeholder + '  ( / )';
        persist();
        runFind(false);
    });
    $('findNext').addEventListener('click', () => step(1));
    $('findPrev').addEventListener('click', () => step(-1));
    $('findCopy').addEventListener('click', () => state.find.selector && copy(state.find.selector, 'TestFly selector'));
    $('strategy').value = state.find.strategy;
    $('search').placeholder = STRATEGY[state.find.strategy].placeholder + '  ( / )';
    renderFindCount();

    // keyboard navigation in the tree
    $('tree').addEventListener('keydown', e => {
        const rows = [...$('tree').querySelectorAll('.row')];
        if (!rows.length) return;
        const current = state.selected ? rows.findIndex(r => r.classList.contains('selected')) : -1;
        const byKey = key => state.flat.find(el => state.paths.get(el) === key);
        if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
            e.preventDefault();
            const next = rows[Math.max(0, Math.min(rows.length - 1, current + (e.key === 'ArrowDown' ? 1 : -1)))];
            select(byKey(next.dataset.key), false);
            next.scrollIntoView({ block: 'nearest' });
        } else if ((e.key === 'ArrowRight' || e.key === 'ArrowLeft') && state.selected) {
            e.preventDefault();
            toggle(state.paths.get(state.selected), e.key === 'ArrowRight');
        }
    });

    // ── recording ───────────────────────────────────────────────────────
    /** Adds a step in Record mode; an element without a selector falls back to a coordinate tap. */
    function record(el, action, extra) {
        if (state.mode !== 'record') return;
        const suggestions = el ? S.suggest(el, state.elements, countXpath) : [];
        const best = suggestions.find(x => x.count === 1) || suggestions[0];
        if (best) state.steps.push({ action, el, selector: best.selector, ...extra });
        else if (action === 'tap') state.steps.push({ action: 'tapAt', x: extra.x, y: extra.y });
        else return toast('This element has no selector; the step was not recorded', true);
        renderRecorded();
    }

    function recordDevice(step) {
        if (state.mode !== 'record') return;
        state.steps.push(step);
        renderRecorded();
    }

    function recordedCode() {
        return S.codegen(state.steps, state.device ? state.device.platform : 'android');
    }

    function renderRecorded() {
        const n = state.steps.length;
        $('recordedCount').hidden = !n;
        $('recordedCount').textContent = n;
        $('recorded').innerHTML = n ? esc(recordedCode()).replace(
            /(@MobileFind)|(&quot;(?:\\&quot;|(?!&quot;).)*?&quot;)|\b(public|private|class|extends|void)\b/g,
            (m, ann, str) => `<span class="${ann ? 'ann' : str ? 'str' : 'kw'}">${m}</span>`)
            : '<span class="r">Switch to Record and use the device: taps, typing, swipes and buttons appear here as Java.</span>';
    }
    $('copyRecorded').addEventListener('click', () => state.steps.length && copy(recordedCode(), 'recorded page object'));
    $('clearRecorded').addEventListener('click', () => { state.steps = []; renderRecorded(); });

    // ── snapshot tab ────────────────────────────────────────────────────
    function renderSnapshot() {
        const pre = $('snapshot');
        if (state.snapshot == null) {
            pre.textContent = state.device ? 'Loading…' : '';
            return;
        }
        pre.innerHTML = esc(state.snapshot).replace(/(@e\d+)/g, '<span class="r">$1</span>');
    }

    document.querySelectorAll('.tab').forEach(tab => tab.addEventListener('click', () => {
        state.tab = tab.dataset.tab;
        document.querySelectorAll('.tab').forEach(t => t.classList.toggle('active', t === tab));
        const snap = state.tab === 'snapshot';
        $('tree').hidden = state.tab !== 'tree';
        $('searchBox').hidden = state.tab !== 'tree';
        $('snapshotWrap').hidden = !snap;
        $('recordedWrap').hidden = state.tab !== 'recorded';
        renderRecorded();
        if (snap && state.snapshot == null && state.device) send({ type: 'snapshotText' });
        renderSnapshot();
    }));
    $('copySnapshot').addEventListener('click', () => state.snapshot && copy(state.snapshot, 'snapshot'));

    // ── details ─────────────────────────────────────────────────────────
    function renderDetails() {
        const box = $('details');
        const el = state.selected;
        if (!el) {
            box.innerHTML = `<div class="empty-state small">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 3l14 7-6 2-2 6z"/></svg>
                <p class="title">Nothing selected</p>
                <p>Click an element on the screen or in the tree to see its attributes and TestFly selectors.</p></div>`;
            return;
        }
        const k = kindOf(el);
        const suggestions = S.suggest(el, state.elements, countXpath);
        const best = suggestions.find(s => s.count === 1) || suggestions[0];
        const platform = state.device ? state.device.platform : 'android';
        const text = S.displayText(el);
        const r = el.rect || { x: 0, y: 0, width: 0, height: 0 };
        const cx = Math.round(r.x + r.width / 2), cy = Math.round(r.y + r.height / 2);
        const cli = `mobilecli io tap --device ${state.device ? state.device.id : '<device-id>'} ${el.ref || ''}`.trim();

        let html = `
            <div class="el-head">
                <span class="kind ${k.role}">${k.label}</span>
                <div><div class="name">${esc(shortType(el.type))}${text ? ' <span class="muted">“' + esc(text) + '”</span>' : ''}</div>
                <div class="full">${esc(el.type)}</div></div>
            </div>
            <div class="actions">
                <button data-act="tap" title="Tap the element centre (${cx}, ${cy})">${ICON.tap}Tap</button>
                <button data-act="longPress" title="Long-press the element centre">${ICON.hold}Long press</button>
                <button data-act="cli" title="${esc(cli)}">${ICON.term}Copy CLI tap</button>
                <div class="type-row">
                    <input id="typeText" placeholder="Text to type into this element…" spellcheck="false">
                    <button data-act="type" title="Tap the element, then type">${ICON.type}Type</button>
                </div>
            </div>`;

        html += `<div class="section-title">TestFly selectors</div>`;
        if (!suggestions.length) {
            html += `<p class="note">This element has no id, text or label. Select a parent with an id and narrow it with <code>within(...)</code> or <code>nth(...)</code>.</p>`;
        }
        for (const s of suggestions) {
            const count = s.count === 1 ? '<span class="count unique">✓ unique</span>'
                : s.count > 1 ? `<span class="count many">${s.count} matches</span>`
                    : s.count === 0 ? '<span class="count many">no match</span>' : '<span class="count unknown">?</span>';
            html += `<div class="sel${s === best && s.count === 1 ? ' best' : ''}">
                <span class="k">${s.kind}</span><code>${esc(s.selector)}</code>
                <span class="meta">${count}
                    <button data-copy="${attr(s.selector)}" title="Copy selector">${ICON.copy}</button>
                    <button data-field="${attr(s.selector)}" title="Copy as @MobileFind field">${ICON.java}</button>
                </span></div>`;
        }
        if (best) {
            if (best.count !== 1) {
                html += `<p class="note">No selector is unique on this screen. Chain it in code: <code>…within(container)</code> or <code>.nth(i)</code>.</p>`;
            }
            const snippet = S.mobileFind(el, best.selector, platform);
            html += `<div class="section-title">Page object field</div>
                <pre class="snippet">${javaHl(snippet)}<button data-copy="${attr(snippet)}" data-label="@MobileFind field">${ICON.copy}Copy</button></pre>`;
        }

        html += `<div class="section-title">Attributes</div><div class="attrs">`;
        for (const key of ['ref', 'type', 'identifier', 'text', 'label', 'name', 'value', 'placeholder', 'key']) {
            const v = el[key];
            html += `<div class="key">${key}</div>` + (v != null && v !== ''
                ? `<div class="val" data-copy="${attr(String(v))}" data-label="${key}" title="Click to copy">${esc(String(v))}</div>`
                : `<div class="val none">—</div>`);
        }
        html += `<div class="key">bounds</div><div class="val" data-copy="${esc(r.x)},${esc(r.y)},${esc(r.width)},${esc(r.height)}" data-label="bounds">x ${esc(r.x)}, y ${esc(r.y)}, ${esc(r.width)} × ${esc(r.height)}</div>`;
        html += `<div class="key">center</div><div class="val" data-copy="${esc(cx)},${esc(cy)}" data-label="center">${esc(cx)}, ${esc(cy)}</div></div>`;

        html += `<div class="section-title">State</div><div class="flags">` +
            flag('enabled', el.enabled !== false, true) + flag('selected', !!el.selected) + flag('checked', !!el.checked) +
            flag('focused', !!el.focused) + `</div>`;
        box.innerHTML = html;
    }

    function flag(name, on, offIsBad) {
        return `<span class="flag ${on ? 'on' : offIsBad ? 'off' : ''}">${on ? '●' : '○'} ${name}</span>`;
    }

    function javaHl(code) {
        return esc(code)
            .replace(/(@MobileFind)/, '<span class="ann">$1</span>')
            .replace(/(&quot;.*?&quot;)(\))/, '<span class="str">$1</span>$2')
            .replace(/\b(private)\b/, '<span class="kw">$1</span>');
    }

    $('details').addEventListener('click', e => {
        const t = e.target.closest('[data-copy],[data-field],[data-act]');
        const el = state.selected;
        if (!t || !el) return;
        if (t.dataset.copy != null) {
            return copy(t.dataset.copy, t.dataset.label || 'selector');
        }
        if (t.dataset.field != null) {
            return copy(S.mobileFind(el, t.dataset.field, state.device ? state.device.platform : 'android'), '@MobileFind field');
        }
        const r = el.rect || { x: 0, y: 0, width: 0, height: 0 };
        const x = r.x + r.width / 2, y = r.y + r.height / 2;
        switch (t.dataset.act) {
            case 'tap': record(el, 'tap', { x, y }); send({ type: 'tap', x, y }); return setStatus('loading', 'Tapping…');
            case 'longPress': record(el, 'longPress', {}); send({ type: 'longPress', x, y }); return setStatus('loading', 'Long-pressing…');
            case 'cli': return copy(`mobilecli io tap --device ${state.device ? state.device.id : '<device-id>'} ${el.ref || ''}`.trim(), 'CLI command');
            case 'type': return typeInto(el, x, y);
        }
    });
    $('details').addEventListener('keydown', e => {
        if (e.key === 'Enter' && e.target.id === 'typeText' && state.selected) {
            const r = state.selected.rect || { x: 0, y: 0, width: 0, height: 0 };
            typeInto(state.selected, r.x + r.width / 2, r.y + r.height / 2);
        }
    });

    function typeInto(el, x, y) {
        const text = $('typeText').value;
        if (!text) return toast('Enter the text to type first');
        record(el, 'fill', { text });
        send({ type: 'tapType', x, y, text });
        setStatus('loading', 'Typing…');
    }

    // ── toolbar ─────────────────────────────────────────────────────────
    $('device').addEventListener('change', e => e.target.value && send({ type: 'selectDevice', id: e.target.value }));
    $('reloadDevices').addEventListener('click', () => send({ type: 'devices' }));
    $('refresh').addEventListener('click', refresh);
    $('showBounds').addEventListener('change', renderBounds);
    document.querySelectorAll('[data-button]').forEach(b => b.addEventListener('click', () => {
        recordDevice({ action: 'button', button: b.dataset.button });
        send({ type: 'button', button: b.dataset.button });
        setStatus('loading', `Pressing ${b.dataset.button}…`);
    }));
    document.querySelectorAll('[data-swipe]').forEach(b => b.addEventListener('click', () => {
        recordDevice({ action: 'swipe', direction: b.dataset.swipe });
        send({ type: 'swipe', direction: b.dataset.swipe });
        setStatus('loading', `Swiping ${b.dataset.swipe}…`);
    }));
    document.querySelectorAll('#mode button').forEach(b => b.addEventListener('click', () => setMode(b.dataset.mode)));

    function refresh() {
        if (!state.device) return send({ type: 'devices' });
        send({ type: 'refresh' });
    }

    function setMode(mode) {
        state.mode = mode;
        document.querySelectorAll('#mode button').forEach(b => b.classList.toggle('active', b.dataset.mode === mode));
        screenEl.classList.toggle('tap-mode', mode === 'tap' || mode === 'record');
        screenEl.classList.toggle('inspect-mode', mode === 'inspect');
        document.body.classList.toggle('recording', mode === 'record');
        state.hovered = null;
        renderHover();
        persist();
    }

    document.addEventListener('keydown', e => {
        const typing = /INPUT|SELECT|TEXTAREA/.test(document.activeElement && document.activeElement.tagName);
        if (e.key === 'Escape') {
            if (typing) document.activeElement.blur(); else select(null, false);
            return;
        }
        if (typing || e.metaKey || e.ctrlKey || e.altKey) return;
        if (e.key === 'r') refresh();
        else if (e.key === '/') { e.preventDefault(); $('search').focus(); }
        else if (e.key === 'i') setMode('inspect');
        else if (e.key === 't') setMode('tap');
        else if (e.key === 'e') setMode('record');
    });

    // ── resizable panes ─────────────────────────────────────────────────
    const panes = $('panes');
    if (saved.cols) saved.cols.forEach((w, i) => panes.style.setProperty('--col-' + i, w + 'px'));
    document.querySelectorAll('.resizer').forEach(handle => handle.addEventListener('mousedown', down => {
        const i = Number(handle.dataset.resize);
        const pane = handle.previousElementSibling;
        const start = pane.getBoundingClientRect().width;
        handle.classList.add('dragging');
        const move = e => {
            const w = Math.max(220, Math.min(panes.clientWidth - 420, start + e.clientX - down.clientX));
            panes.style.setProperty('--col-' + i, w + 'px');
        };
        const up = () => {
            handle.classList.remove('dragging');
            window.removeEventListener('mousemove', move);
            window.removeEventListener('mouseup', up);
            saved.cols = [0, 1].map(n => document.querySelectorAll('.pane')[n].getBoundingClientRect().width);
            persist();
        };
        window.addEventListener('mousemove', move);
        window.addEventListener('mouseup', up);
    }));

    // ── helpers ─────────────────────────────────────────────────────────
    function esc(s) {
        return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    }

    function attr(s) {
        return esc(s);
    }

    // Only an inline JPEG or PNG data URL may become an image source; anything else
    // would let a message point the picture at an arbitrary URL.
    function imageSrc(s) {
        return /^data:image\/(jpeg|png);base64,[A-Za-z0-9+/=]+$/.test(String(s || '')) ? s : '';
    }

    setMode(state.mode);
    renderTree();
    send({ type: 'ready' });
})();
