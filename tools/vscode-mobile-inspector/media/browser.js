// TestFly Browser Inspector webview. Talks to the extension host with postMessage; the host drives the browser over CDP.
(function () {
    'use strict';
    const vscode = acquireVsCodeApi();
    const $ = id => document.getElementById(id);

    const ICON = {
        copy: '<svg viewBox="0 0 16 16"><rect x="5" y="5" width="8.5" height="8.5" rx="1.5"/><path d="M10.5 5V3.5A1.5 1.5 0 0 0 9 2H3.5A1.5 1.5 0 0 0 2 3.5V9A1.5 1.5 0 0 0 3.5 10.5H5"/></svg>',
        java: '<svg viewBox="0 0 16 16"><path d="M5.5 4L2 8l3.5 4"/><path d="M10.5 4L14 8l-3.5 4"/></svg>',
        chev: '<svg viewBox="0 0 16 16" class="chev"><path d="M6 4l4 4-4 4"/></svg>',
        click: '<svg viewBox="0 0 16 16"><circle cx="8" cy="8" r="2.2"/><circle cx="8" cy="8" r="5.5"/></svg>',
        type: '<svg viewBox="0 0 16 16"><path d="M3 4h10M8 4v9"/></svg>',
    };
    // Badge per TestFly Role; containers get the layout colour, lists the list colour (as in the Mobile Inspector).
    const KIND = {
        BUTTON: ['button', 'B'], LINK: ['link', 'L'], CHECKBOX: ['checkbox', 'C'], RADIO: ['checkbox', 'R'], SWITCH: ['switch', 'S'],
        TEXTBOX: ['textfield', 'T'], SEARCHBOX: ['textfield', 'T'], COMBOBOX: ['textfield', 'Cb'], SPINBUTTON: ['textfield', 'T'], SLIDER: ['switch', 'Sl'],
        HEADING: ['heading', 'H'], PARAGRAPH: ['text', 'P'], IMG: ['image', 'I'], LIST: ['list', 'Ul'], LISTITEM: ['other', 'Li'],
        TABLE: ['list', 'Tb'], ROW: ['other', 'Tr'], CELL: ['other', 'Td'], COLUMNHEADER: ['other', 'Th'], OPTION: ['other', 'O'],
        TAB: ['button', 'Tab'], MENUITEM: ['button', 'Mi'],
    };
    const LAYOUT_TAGS = ['div', 'section', 'main', 'header', 'footer', 'nav', 'aside', 'article', 'form', 'body', 'fieldset', 'dialog'];
    const INDENT = 16;
    const KIND_ORDER = ['TEST ID', 'ROLE', 'LABEL', 'PLACEHOLDER', 'ID', 'TEXT', 'CSS'];

    const saved = vscode.getState() || {};
    const state = {
        page: null, tree: [], flat: [], byIndex: new Map(), paths: new Map(), parents: new Map(),
        selected: null, hovered: null, details: null, expanded: new Set(saved.expanded || []),
        mode: saved.mode || 'inspect', tab: 'locators',
        find: { query: '', matches: [], set: new Set(), current: -1, kinds: [] },
    };

    // ── host messages ───────────────────────────────────────────────────
    window.addEventListener('message', e => {
        // VS Code relays extension messages from its own webview origin; e.source matches no window here
        if (e.origin !== window.location.origin || !e.data || typeof e.data !== 'object') return;
        const m = e.data;
        switch (m.type) {
            case 'browser': return onBrowser(m);
            case 'page': return onPage(m);
            case 'details': return onDetails(m);
            case 'found': return onFound(m);
            case 'closed': return onClosed();
            case 'history': $('back').disabled = !m.canBack; $('forward').disabled = !m.canForward; return;
            case 'status': return setStatus(m.state, m.message);
            case 'error': return onError(m.message);
        }
    });

    function send(message) {
        vscode.postMessage(message);
    }

    function persist() {
        vscode.setState({ mode: state.mode, expanded: [...state.expanded], row: saved.row, col: saved.col });
    }

    // ── status & toasts ─────────────────────────────────────────────────
    function setStatus(kind, text) {
        $('status').dataset.state = kind;
        $('statusText').textContent = text;
        $('loadingBar').hidden = kind !== 'loading' && kind !== 'connecting';
        $('stage').classList.toggle('stale', kind === 'loading');
        if (!state.page) $('emptyHint').textContent = text;
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

    function onError(message) {
        setStatus('error', 'Error');
        toast(message, true);
        if (!state.page) $('emptyHint').textContent = message;
    }

    function copy(text, label) {
        send({ type: 'copy', text, label });
        toast('Copied ' + label);
    }

    // ── browser & page ──────────────────────────────────────────────────
    function onBrowser(m) {
        const badge = $('product');
        badge.hidden = !m.product;
        badge.textContent = m.product ? m.product.replace('/', ' ').replace(/\.\d+\.\d+\.\d+$/, '') : '';
        badge.title = m.path || '';
        if (m.viewport) $('viewport').value = `${m.viewport.width}x${m.viewport.height}`;
    }

    function onPage(m) {
        const previous = state.selected;
        state.page = m;
        state.tree = m.tree || [];
        state.flat = [];
        state.byIndex = new Map();
        state.paths = new Map();
        state.parents = new Map();
        const walk = (list, prefix, parent) => list.forEach((n, i) => {
            const key = prefix ? prefix + '/' + i : String(i);
            state.flat.push(n);
            state.byIndex.set(n.i, n);
            state.paths.set(n, key);
            if (parent) state.parents.set(n, parent);
            walk(n.children || [], key, n);
        });
        walk(state.tree, '', null);
        state.hovered = null;
        state.selected = previous ? findSame(previous) : null;
        state.details = null;

        if (document.activeElement !== $('url')) $('url').value = m.url || '';
        const img = $('shot');
        img.onload = () => {
            $('page').style.setProperty('--page-max', m.screen.width + 'px');
            renderHover();
            renderSelection();
        };
        img.src = imageSrc(m.screenshot);
        $('page').hidden = false;
        $('emptyScreen').hidden = true;
        $('treeCount').textContent = `${m.count} elements${m.truncated ? ' (first ' + m.count + ')' : ''}`;
        const host = hostOf(m.url);
        setStatus('ok', `${host ? host + ' · ' : ''}${m.count} elements · ${m.ms} ms`);
        state.find.matches = [];
        state.find.set = new Set();
        renderTree();
        renderDetails();
        if (state.selected) send({ type: 'details', index: state.selected.i });
        if (state.find.query) send({ type: 'find', query: state.find.query, keep: true });
        renderFindCount();
    }

    function onClosed() {
        $('page').classList.add('closed');
        $('closeBrowser').disabled = true;
    }

    function hostOf(url) {
        try { return new URL(url).host; } catch (e) { return ''; }
    }

    /** After a refresh, the element that most likely is the one selected before. */
    function findSame(old) {
        const same = n => n.tag === old.tag && (n.testId || '') === (old.testId || '') && (n.id || '') === (old.id || '');
        const exact = state.flat.filter(n => same(n) && (n.text || '') === (old.text || '') && (n.name || '') === (old.name || ''));
        if (exact.length === 1) return exact[0];
        const loose = (old.testId || old.id) ? state.flat.filter(same) : [];
        return loose.length === 1 ? loose[0] : null;
    }

    // ── screenshot overlay ──────────────────────────────────────────────
    function pct(v, total) {
        return (100 * v / (total || 1)) + '%';
    }

    function place(box, n) {
        const r = n.rect;
        const s = state.page.screen;
        box.style.left = pct(r.x, s.width);
        box.style.top = pct(r.y, s.height);
        box.style.width = pct(r.width, s.width);
        box.style.height = pct(r.height, s.height);
    }

    function label(n) {
        // like Chrome's breadcrumb: tag#id or tag.class, then the test id or name when there is one
        const base = n.tag + (n.id ? '#' + n.id : n.cls ? '.' + n.cls[0] : '');
        return base + (n.testId ? ' · ' + n.testId : !n.id && n.name ? ' “' + n.name + '”' : '');
    }

    function renderHover() {
        const box = $('hoverBox');
        const n = state.hovered;
        document.querySelectorAll('.row.hovered').forEach(r => r.classList.remove('hovered'));
        if (!n || !state.page || !n.rect.width || n === state.selected) {
            box.hidden = true;
        } else {
            box.hidden = false;
            place(box, n);
            box.classList.toggle('below', n.rect.y < 24);
            box.classList.toggle('right', n.rect.x + n.rect.width > state.page.screen.width * 0.7);
            box.innerHTML = `<span class="tag">${esc(label(n))}</span>`;
        }
        const row = n && rowFor(n);
        if (row) row.classList.add('hovered');
    }

    function renderSelection() {
        const box = $('selectBox');
        const n = state.selected;
        if (!n || !state.page || !n.rect.width) {
            box.hidden = true;
        } else {
            box.hidden = false;
            place(box, n);
        }
        $('selRef').hidden = !n;
        $('selRef').textContent = n ? label(n) : '';
    }

    function toPage(event) {
        const rect = $('shot').getBoundingClientRect();
        const s = state.page.screen;
        return { x: (event.clientX - rect.left) / rect.width * s.width, y: (event.clientY - rect.top) / rect.height * s.height };
    }

    /** The smallest visible element whose box holds the point. */
    function hitTest(x, y) {
        let best = null;
        let area = Infinity;
        for (const n of state.flat) {
            const r = n.rect;
            if (!n.visible || !r.width || !r.height) continue;
            if (x < r.x || y < r.y || x > r.x + r.width || y > r.y + r.height) continue;
            const a = r.width * r.height;
            if (a <= area) { area = a; best = n; }
        }
        return best;
    }

    const pageEl = $('page');
    pageEl.addEventListener('mousemove', e => {
        if (!state.page) return;
        const hit = state.mode === 'inspect' ? hitTest(toPage(e).x, toPage(e).y) : null;
        if (hit !== state.hovered) {
            state.hovered = hit;
            renderHover();
        }
    });
    pageEl.addEventListener('mouseleave', () => {
        state.hovered = null;
        renderHover();
    });
    pageEl.addEventListener('click', e => {
        if (!state.page) return;
        const p = toPage(e);
        if (state.mode === 'click') {
            ripple(e);
            send({ type: 'clickAt', x: p.x, y: p.y });
            setStatus('loading', 'Clicking…');
        } else {
            select(hitTest(p.x, p.y), true);
        }
    });

    function ripple(e) {
        const r = $('ripple');
        const rect = pageEl.getBoundingClientRect();
        r.hidden = false;
        r.style.left = (e.clientX - rect.left) + 'px';
        r.style.top = (e.clientY - rect.top) + 'px';
        r.style.animation = 'none';
        void r.offsetWidth;
        r.style.animation = '';
    }

    function select(n, reveal) {
        state.selected = n;
        state.hovered = null;
        state.details = null;
        if (n && reveal) {
            for (let p = state.parents.get(n); p; p = state.parents.get(p)) state.expanded.add(state.paths.get(p));
            persist();
        }
        renderTree();
        renderHover();
        renderSelection();
        renderDetails();
        if (n) send({ type: 'details', index: n.i });
        if (n && reveal) {
            const row = rowFor(n);
            if (row) row.scrollIntoView({ block: 'nearest' });
        }
        if (n && !reveal) scrollPageTo(n);
    }

    function scrollPageTo(n) {
        const box = $('selectBox');
        if (!box.hidden) box.scrollIntoView({ block: 'nearest' });
    }

    // ── tree ────────────────────────────────────────────────────────────
    function kindOf(n) {
        const k = n.role && KIND[n.role];
        if (k) return { cls: k[0], label: k[1] };
        if (n.role === 'NAVIGATION' || n.role === 'BANNER' || n.role === 'MAIN' || n.role === 'CONTENTINFO' || LAYOUT_TAGS.includes(n.tag)) {
            return { cls: 'layout', label: n.tag.charAt(0).toUpperCase() };
        }
        return { cls: 'other', label: n.tag.charAt(0).toUpperCase() };
    }

    function rowFor(n) {
        const key = state.paths.get(n);
        return key == null ? null : $('tree').querySelector(`.row[data-key="${key}"]`);
    }

    function renderTree() {
        const tree = $('tree');
        const frag = document.createDocumentFragment();
        const walk = (list, depth) => {
            for (const n of list) {
                const key = state.paths.get(n);
                const kids = n.children || [];
                const open = state.expanded.has(key) || depth < 3;
                frag.append(row(n, key, depth, kids.length > 0, open));
                if (kids.length && open) walk(kids, depth + 1);
            }
        };
        walk(state.tree, 0);
        tree.innerHTML = '';
        if (!frag.childNodes.length) {
            tree.innerHTML = '<div class="empty-state small"><p>The DOM tree appears when the page is read.</p></div>';
            return;
        }
        tree.append(frag);
    }

    function row(n, key, depth, hasKids, open) {
        const r = document.createElement('div');
        const f = state.find;
        r.className = 'row' + (n === state.selected ? ' selected' : '') + (n.visible ? '' : ' hidden-el')
            + (f.set.has(n.i) ? ' match' + (f.matches[f.current] === n.i ? ' current' : '') : '');
        r.dataset.key = key;
        r.setAttribute('role', 'treeitem');
        if (hasKids) r.setAttribute('aria-expanded', String(open));
        const k = kindOf(n);
        const text = n.text || (n.role && n.name && !n.children ? n.name : '');
        const attrs = (n.id ? '#' + n.id : '') + (n.cls ? '.' + n.cls.join('.') : '') + (n.type ? ` type=${n.type}` : '');
        r.innerHTML =
            '<span class="indent"></span>' +
            ICON.chev.replace('class="chev"', `class="chev${hasKids ? (open ? ' open' : '') : ' none'}"`) +
            `<span class="kind ${k.cls}">${esc(k.label)}</span>` +
            `<span class="tag-name">${esc(n.tag)}</span>` +
            (text ? `<span class="txt">“<b>${esc(text)}</b>”</span>` : '') +
            (n.testId ? `<span class="tid">${esc(n.testId)}</span>` : '') +
            (attrs ? `<span class="attr">${esc(attrs.trim())}</span>` : '');
        // set through the CSSOM: the webview CSP drops inline style="" attributes
        r.querySelector('.indent').style.width = `${depth * INDENT + 4}px`;
        r.addEventListener('click', ev => {
            if (hasKids && ev.target.closest('.chev')) return toggle(key, !open);
            select(n, false);
        });
        r.addEventListener('dblclick', () => hasKids && toggle(key, !open));
        r.addEventListener('mouseenter', () => { state.hovered = n; renderHover(); });
        r.addEventListener('mouseleave', () => { state.hovered = null; renderHover(); });
        return r;
    }

    function toggle(key, open) {
        if (open) state.expanded.add(key); else state.expanded.delete(key);
        persist();
        renderTree();
    }

    // ── find (string, CSS selector or XPath, evaluated in the page) ─────
    let findTimer;
    $('search').addEventListener('input', e => {
        clearTimeout(findTimer);
        const query = e.target.value.trim();
        state.find.query = query;
        if (!query) {
            state.find = { query: '', matches: [], set: new Set(), current: -1, kinds: [] };
            renderTree();
            renderFindCount();
            return;
        }
        findTimer = setTimeout(() => send({ type: 'find', query }), 200);
    });
    $('search').addEventListener('keydown', e => {
        if (e.key === 'Enter') {
            e.preventDefault();
            step(e.shiftKey ? -1 : 1);
        } else if (e.key === 'Escape' && $('search').value) {
            e.stopPropagation();
            $('search').value = '';
            $('search').dispatchEvent(new Event('input'));
        }
    });
    $('findNext').addEventListener('click', () => step(1));
    $('findPrev').addEventListener('click', () => step(-1));

    function onFound(m) {
        if (m.query !== state.find.query) return; // an older answer
        const keep = m.keep && state.selected ? m.indices.indexOf(state.selected.i) : -1;
        state.find.matches = m.indices;
        state.find.set = new Set(m.indices);
        state.find.kinds = m.kinds || [];
        state.find.current = keep;
        if (!m.keep && m.indices.length) return step(1);
        renderTree();
        renderFindCount();
    }

    /** Moves to the next or previous match, like Enter / Shift+Enter in Chrome DevTools. */
    function step(dir) {
        const f = state.find;
        if (!f.matches.length) return renderFindCount();
        f.current = (f.current + dir + f.matches.length) % f.matches.length;
        renderFindCount();
        select(state.byIndex.get(f.matches[f.current]), true);
    }

    function renderFindCount() {
        const f = state.find;
        const el = $('findCount');
        el.textContent = !f.query ? '' : f.matches.length ? `${f.current >= 0 ? f.current + 1 : '–'} of ${f.matches.length}` : 'No match';
        el.classList.toggle('none', !!f.query && !f.matches.length);
        el.title = f.kinds.length ? 'Matched as ' + f.kinds.join(', ') : '';
        $('findPrev').disabled = $('findNext').disabled = f.matches.length < 2;
    }

    $('tree').addEventListener('keydown', e => {
        const rows = [...$('tree').querySelectorAll('.row')];
        if (!rows.length) return;
        const current = rows.findIndex(r => r.classList.contains('selected'));
        const byKey = key => state.flat.find(n => state.paths.get(n) === key);
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

    // ── details ─────────────────────────────────────────────────────────
    function onDetails(m) {
        if (!state.selected || state.selected.i !== m.index) return;
        state.details = m.data;
        renderDetails();
    }

    document.querySelectorAll('.details-pane .tab').forEach(tab => tab.addEventListener('click', () => {
        state.tab = tab.dataset.tab;
        document.querySelectorAll('.details-pane .tab').forEach(t => t.classList.toggle('active', t === tab));
        renderDetails();
    }));

    /** Unique locators first, then TestFly's preference: test id, role, label, placeholder, id, text, css. */
    function ranked(list) {
        return list.filter(l => l.self).sort((a, b) =>
            (a.count === 1 ? 0 : 1) - (b.count === 1 ? 0 : 1) || KIND_ORDER.indexOf(a.kind) - KIND_ORDER.indexOf(b.kind));
    }

    function fieldName(n, best) {
        const arg = best ? (best.code.match(/"((?:[^"\\]|\\.)*)"\)$/) || [])[1] : '';
        const base = n.testId || (best && best.kind !== 'CSS' ? arg : '') || n.name || n.id || n.placeholder || n.text || n.tag;
        const words = String(base).replace(/\\./g, '').normalize('NFKD').replace(/[^\p{L}\p{N}]+/gu, ' ').trim().split(/\s+/).slice(0, 4);
        let name = words.map((w, i) => i ? w.charAt(0).toUpperCase() + w.slice(1).toLowerCase() : w.toLowerCase()).join('') || n.tag;
        if (/^\d/.test(name)) name = n.tag + name;
        return name;
    }

    function renderDetails() {
        const box = $('details');
        const n = state.selected;
        if (!n) {
            box.innerHTML = `<div class="empty-state small">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 3l14 7-6 2-2 6z"/></svg>
                <p class="title">Nothing selected</p>
                <p>Click an element on the page or in the tree to see its TestFly locators and attributes.</p></div>`;
            return;
        }
        const d = state.details;
        const k = kindOf(n);
        let html = `
            <div class="el-head">
                <span class="kind ${k.cls}">${esc(k.label)}</span>
                <div><div class="name">${esc(n.tag)}${n.name || n.text ? ' <span class="muted">“' + esc(n.name || n.text) + '”</span>' : ''}</div>
                <div class="full html">${d ? esc(d.outer) : ''}</div></div>
            </div>`;
        if (!d) {
            box.innerHTML = html + '<p class="note">Reading the element…</p>';
            return;
        }
        if (state.tab === 'attributes') {
            html += `<div class="section-title">Attributes</div><div class="attrs">`;
            const rows = d.attributes.length ? d.attributes : [];
            for (const [key, value] of rows) {
                html += `<div class="key">${esc(key)}</div><div class="val" data-copy="${esc(value)}" data-label="${esc(key)}" title="Click to copy">${esc(value) || '<span class="muted">""</span>'}</div>`;
            }
            if (!rows.length) html += `<div class="key">—</div><div class="val none">no attributes</div>`;
            html += `</div><div class="section-title">Accessibility</div><div class="attrs">
                <div class="key">role</div><div class="val">${esc(d.role) || '<span class="muted">none</span>'}</div>
                <div class="key">accessible name</div><div class="val">${esc(d.name) || '<span class="muted">none</span>'}</div>
                <div class="key">text</div><div class="val">${esc(d.text) || '<span class="muted">none</span>'}</div>
                ${d.value ? `<div class="key">value</div><div class="val">${esc(d.value)}</div>` : ''}
                <div class="key">bounds</div><div class="val">x ${n.rect.x}, y ${n.rect.y}, ${n.rect.width} × ${n.rect.height}</div>
            </div><div class="section-title">State</div><div class="flags">
                <span class="flag ${d.visible ? 'on' : 'off'}">${d.visible ? '●' : '○'} visible</span>
                <span class="flag ${d.disabled ? 'off' : 'on'}">${d.disabled ? '○' : '●'} enabled</span></div>`;
            box.innerHTML = html;
            return;
        }
        const list = ranked(d.locators);
        const best = list[0];
        html += `<div class="loc-grid"><div>
            <div class="section-title">TestFly locators</div>`;
        if (!list.length) html += `<p class="note">No locator resolves to this element on its own. Select a parent and narrow it with <code>within(...)</code> or <code>nth(...)</code>.</p>`;
        for (const l of list) {
            const count = l.count === 1 ? '<span class="count unique">✓ unique</span>' : `<span class="count many">${l.count} matches</span>`;
            html += `<div class="sel${l === best && l.count === 1 ? ' best' : ''}">
                <span class="k">${esc(l.kind)}</span><code>${javaHl(l.code)}</code>
                <span class="meta">${count}
                    <button data-copy="${esc(l.code)}" data-label="locator" title="Copy locator">${ICON.copy}</button>
                    <button data-field="${esc(l.code)}" title="Copy as page object field">${ICON.java}</button>
                </span></div>`;
        }
        if (best && best.count !== 1) {
            html += `<p class="note">No locator is unique on this page. Narrow it in code: <code>.within(...)</code>, <code>.withText(...)</code> or <code>.nth(i)</code>.</p>`;
        }
        html += `</div><div>`;
        if (best) {
            const field = fieldCode(n, best);
            html += `<div class="section-title">Page object field</div>
                <pre class="snippet">${javaHl(field)}<button data-copy="${esc(field)}" data-label="page object field">${ICON.copy}Copy</button></pre>`;
        }
        html += `<div class="section-title">Act in the browser</div>
            <div class="actions">
                <button data-act="click" title="Click the element centre">${ICON.click}Click</button>
                <div class="type-row">
                    <input id="typeText" placeholder="Text to type into this element…" spellcheck="false">
                    <button data-act="type" title="Click the element, then type">${ICON.type}Type</button>
                </div>
            </div></div></div>`;
        box.innerHTML = html;
    }

    function fieldCode(n, l) {
        return `private final Locator ${fieldName(n, l)} = ${l.code};`;
    }

    function javaHl(code) {
        return esc(code)
            .replace(/(&quot;(?:\\&quot;|(?!&quot;).)*?&quot;)/g, '<span class="str">$1</span>')
            .replace(/\b(private|final)\b/g, '<span class="kw">$1</span>')
            .replace(/\b(getBy\w+|find\w*)\(/g, '<span class="ann">$1</span>(');
    }

    $('details').addEventListener('click', e => {
        const t = e.target.closest('[data-copy],[data-field],[data-act]');
        const n = state.selected;
        if (!t || !n) return;
        if (t.dataset.copy != null) return copy(t.dataset.copy, t.dataset.label || 'locator');
        if (t.dataset.field != null) {
            const l = (state.details && state.details.locators.find(x => x.code === t.dataset.field)) || { code: t.dataset.field, kind: '' };
            return copy(fieldCode(n, l), 'page object field');
        }
        if (t.dataset.act === 'click') {
            send({ type: 'click', index: n.i });
            return setStatus('loading', 'Clicking…');
        }
        if (t.dataset.act === 'type') return typeInto(n);
    });
    $('details').addEventListener('keydown', e => {
        if (e.key === 'Enter' && e.target.id === 'typeText' && state.selected) typeInto(state.selected);
    });

    function typeInto(n) {
        const text = $('typeText').value;
        if (!text) return toast('Enter the text to type first');
        send({ type: 'type', index: n.i, text });
        setStatus('loading', 'Typing…');
    }

    // ── toolbar ─────────────────────────────────────────────────────────
    $('urlForm').addEventListener('submit', e => {
        e.preventDefault();
        const url = $('url').value.trim();
        if (!url) return;
        send({ type: 'navigate', url });
        setStatus('loading', 'Opening ' + url + '…');
        $('url').blur();
    });
    $('back').addEventListener('click', () => { send({ type: 'back' }); setStatus('loading', 'Going back…'); });
    $('forward').addEventListener('click', () => { send({ type: 'forward' }); setStatus('loading', 'Going forward…'); });
    $('reload').addEventListener('click', () => { send({ type: 'reload' }); setStatus('loading', 'Reloading…'); });
    $('refresh').addEventListener('click', refresh);
    $('closeBrowser').addEventListener('click', () => { send({ type: 'close' }); setStatus('loading', 'Closing the browser…'); });
    $('viewport').addEventListener('change', e => {
        const [width, height] = e.target.value.split('x').map(Number);
        send({ type: 'viewport', width, height });
        setStatus('loading', `Resizing to ${width} × ${height}…`);
    });
    document.querySelectorAll('#mode button').forEach(b => b.addEventListener('click', () => setMode(b.dataset.mode)));

    function refresh() {
        $('page').classList.remove('closed');
        $('closeBrowser').disabled = false;
        send({ type: 'refresh' });
        setStatus('loading', 'Reading the page…');
    }

    function setMode(mode) {
        state.mode = mode;
        document.querySelectorAll('#mode button').forEach(b => b.classList.toggle('active', b.dataset.mode === mode));
        pageEl.classList.toggle('click-mode', mode === 'click');
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
        else if (e.key === 'c') setMode('click');
    });

    // ── resizable drawer ────────────────────────────────────────────────
    const panes = $('panes');
    const drawer = $('drawer');
    if (saved.row) panes.style.setProperty('--row-0', saved.row + 'px');
    if (saved.col) drawer.style.setProperty('--dcol-0', saved.col + 'px');
    function drag(handle, onMove, onDone) {
        handle.addEventListener('mousedown', down => {
            down.preventDefault();
            handle.classList.add('dragging');
            const move = e => onMove(e, down);
            const up = () => {
                handle.classList.remove('dragging');
                window.removeEventListener('mousemove', move);
                window.removeEventListener('mouseup', up);
                onDone();
                persist();
            };
            window.addEventListener('mousemove', move);
            window.addEventListener('mouseup', up);
        });
    }
    let rowStart = 0, colStart = 0;
    $('drawerResizer').addEventListener('mousedown', () => { rowStart = document.querySelector('.page-pane').getBoundingClientRect().height; }, true);
    $('colResizer').addEventListener('mousedown', () => { colStart = document.querySelector('.drawer .tree-pane').getBoundingClientRect().width; }, true);
    drag($('drawerResizer'), (e, down) => {
        const h = Math.max(140, Math.min(panes.clientHeight - 160, rowStart + e.clientY - down.clientY));
        panes.style.setProperty('--row-0', h + 'px');
    }, () => { saved.row = document.querySelector('.page-pane').getBoundingClientRect().height; });
    drag($('colResizer'), (e, down) => {
        const w = Math.max(220, Math.min(drawer.clientWidth - 300, colStart + e.clientX - down.clientX));
        drawer.style.setProperty('--dcol-0', w + 'px');
    }, () => { saved.col = document.querySelector('.drawer .tree-pane').getBoundingClientRect().width; });

    // ── helpers ─────────────────────────────────────────────────────────
    function esc(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    }

    // Only an inline JPEG or PNG data URL may become an image source.
    function imageSrc(s) {
        return /^data:image\/(jpeg|png);base64,[A-Za-z0-9+/=]+$/.test(String(s || '')) ? s : '';
    }

    setMode(state.mode);
    renderTree();
    renderDetails();
    send({ type: 'ready' });
})();
