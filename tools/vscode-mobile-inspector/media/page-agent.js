// Runs inside the inspected page through Runtime.evaluate; the host calls (source).snapshot(...) etc.
// Locator counts follow io.testfly.locator.Locator: Role css selectors, ACCESSIBLE_NAME_JS,
// FORM_CONTROL_CSS, case-insensitive substring matching by default and innermost-only text matching.
(function () {
    'use strict';
    var VERSION = 2;
    if (window.__testflyAgent && window.__testflyAgent.version === VERSION) return window.__testflyAgent;

    // Role.java, in declaration order; the first css match wins for implicit roles.
    var ROLES = [
        ['BUTTON', 'button', "button, [role='button'], input[type='button'], input[type='submit'], input[type='reset'], input[type='image'], summary"],
        ['LINK', 'link', "a[href], area[href], [role='link']"],
        ['CHECKBOX', 'checkbox', "input[type='checkbox'], [role='checkbox']"],
        ['RADIO', 'radio', "input[type='radio'], [role='radio']"],
        ['SWITCH', 'switch', "[role='switch']"],
        ['TEXTBOX', 'textbox', "input:not([type]), input[type='text'], input[type='email'], input[type='password'], input[type='tel'], input[type='url'], input[type='number'], textarea, [role='textbox'], [contenteditable='true']"],
        ['SEARCHBOX', 'searchbox', "input[type='search'], [role='searchbox']"],
        ['COMBOBOX', 'combobox', "select, [role='combobox']"],
        ['OPTION', 'option', "option, [role='option']"],
        ['HEADING', 'heading', "h1, h2, h3, h4, h5, h6, [role='heading']"],
        ['IMG', 'img', "img, [role='img']"],
        ['LIST', 'list', "ul, ol, [role='list']"],
        ['LISTITEM', 'listitem', "li, [role='listitem']"],
        ['TABLE', 'table', "table, [role='table']"],
        ['ROW', 'row', "tr, [role='row']"],
        ['CELL', 'cell', "td, [role='cell'], [role='gridcell']"],
        ['COLUMNHEADER', 'columnheader', "th, [role='columnheader']"],
        ['NAVIGATION', 'navigation', "nav, [role='navigation']"],
        ['BANNER', 'banner', "header, [role='banner']"],
        ['CONTENTINFO', 'contentinfo', "footer, [role='contentinfo']"],
        ['MAIN', 'main', "main, [role='main']"],
        ['REGION', 'region', "section, [role='region']"],
        ['ARTICLE', 'article', "article, [role='article']"],
        ['DIALOG', 'dialog', "dialog, [role='dialog']"],
        ['ALERT', 'alert', "[role='alert']"],
        ['ALERTDIALOG', 'alertdialog', "[role='alertdialog']"],
        ['TAB', 'tab', "[role='tab']"],
        ['TABLIST', 'tablist', "[role='tablist']"],
        ['TABPANEL', 'tabpanel', "[role='tabpanel']"],
        ['MENU', 'menu', "menu, [role='menu']"],
        ['MENUITEM', 'menuitem', "[role='menuitem']"],
        ['PROGRESSBAR', 'progressbar', "progress, [role='progressbar']"],
        ['SPINBUTTON', 'spinbutton', "input[type='number'], [role='spinbutton']"],
        ['SLIDER', 'slider', "input[type='range'], [role='slider']"],
        ['PARAGRAPH', 'paragraph', "p, [role='paragraph']"],
        ['SEPARATOR', 'separator', "hr, [role='separator']"],
    ];
    var FORM_CONTROL_CSS = "input:not([type='hidden']):not([type='button']):not([type='submit']):not([type='reset']), "
        + "textarea, select, [contenteditable='true'], "
        + "[role='textbox'], [role='searchbox'], [role='combobox'], [role='checkbox'], "
        + "[role='radio'], [role='switch'], [role='slider'], [role='spinbutton']";
    var SKIP = { SCRIPT: 1, STYLE: 1, NOSCRIPT: 1, TEMPLATE: 1, META: 1, LINK: 1, HEAD: 1, BR: 1, WBR: 1 };
    var MAX_NODES = 5000;
    var els = [];

    // Locator.ACCESSIBLE_NAME_JS
    function accessibleName(e) {
        if (!e) return '';
        var al = e.getAttribute && e.getAttribute('aria-label');
        if (al && al.trim()) return al.trim();
        var lb = e.getAttribute && e.getAttribute('aria-labelledby');
        if (lb) {
            var t = lb.split(/\s+/).map(function (id) { var r = document.getElementById(id); return r ? r.textContent : ''; }).join(' ').trim();
            if (t) return t;
        }
        if (e.labels && e.labels.length) return Array.prototype.map.call(e.labels, function (l) { return l.textContent; }).join(' ').trim();
        var id = e.getAttribute && e.getAttribute('id');
        if (id) { try { var lab = document.querySelector('label[for="' + id + '"]'); if (lab) return lab.textContent.trim(); } catch (x) { /* odd id */ } }
        var wrap = e.closest && e.closest('label');
        if (wrap) return wrap.textContent.trim();
        var txt = (e.textContent || '').trim();
        if (txt) return txt;
        var alt = e.getAttribute && e.getAttribute('alt');
        if (alt && alt.trim()) return alt.trim();
        var ti = e.getAttribute && e.getAttribute('title');
        if (ti && ti.trim()) return ti.trim();
        var val = e.value;
        if (val) return ('' + val).trim();
        var ph = e.getAttribute && e.getAttribute('placeholder');
        if (ph && ph.trim()) return ph.trim();
        return '';
    }

    function roleOf(e) {
        var explicit = (e.getAttribute('role') || '').trim().split(/\s+/)[0];
        var i;
        if (explicit) {
            for (i = 0; i < ROLES.length; i++) if (ROLES[i][1] === explicit) return ROLES[i];
        }
        for (i = 0; i < ROLES.length; i++) {
            try { if (e.matches(ROLES[i][2])) return ROLES[i]; } catch (x) { /* ignore */ }
        }
        return null;
    }

    function ownText(e) {
        var s = '';
        for (var n = e.firstChild; n; n = n.nextSibling) if (n.nodeType === 3) s += n.nodeValue;
        return s.replace(/\s+/g, ' ').trim();
    }

    function clip(s, n) {
        s = (s || '').replace(/\s+/g, ' ').trim();
        return s.length > n ? s.slice(0, n - 1) + '…' : s;
    }

    function visible(e, r) {
        if (r.width === 0 && r.height === 0) return false;
        var st = getComputedStyle(e);
        return st.visibility !== 'hidden' && st.display !== 'none' && parseFloat(st.opacity) !== 0;
    }

    function snapshot(opts) {
        var testIdAttr = (opts && opts.testIdAttribute) || 'data-testid';
        els = [];
        var sx = window.scrollX, sy = window.scrollY;
        var truncated = false;
        function node(e) {
            if (els.length >= MAX_NODES) { truncated = true; return null; }
            var i = els.length;
            els.push(e);
            var r = e.getBoundingClientRect();
            var role = roleOf(e);
            var cls = typeof e.className === 'string' ? e.className.trim().split(/\s+/).filter(Boolean).slice(0, 3) : [];
            var n = {
                i: i, tag: e.tagName.toLowerCase(),
                id: e.id || undefined,
                cls: cls.length ? cls : undefined,
                testId: e.getAttribute(testIdAttr) || undefined,
                role: role ? role[0] : undefined,
                text: clip(ownText(e), 80) || undefined,
                type: e.getAttribute('type') || undefined,
                placeholder: e.getAttribute('placeholder') || undefined,
                href: e.getAttribute('href') || undefined,
                rect: { x: Math.round(r.left + sx), y: Math.round(r.top + sy), width: Math.round(r.width), height: Math.round(r.height) },
                visible: visible(e, r),
                disabled: e.disabled === true || e.getAttribute('aria-disabled') === 'true' || undefined,
            };
            if (role) n.name = clip(accessibleName(e), 80) || undefined;
            if (e.tagName === 'svg') return n; // its shapes are noise in a page tree
            var kids = [];
            for (var c = e.firstElementChild; c; c = c.nextElementSibling) {
                if (SKIP[c.tagName]) continue;
                var k = node(c);
                if (k) kids.push(k);
            }
            if (kids.length) n.children = kids;
            return n;
        }
        var root = document.body ? node(document.body) : null;
        var doc = document.documentElement;
        return {
            url: location.href, title: document.title,
            width: Math.max(doc.scrollWidth, doc.clientWidth), height: Math.max(doc.scrollHeight, doc.clientHeight),
            viewport: { width: window.innerWidth, height: window.innerHeight },
            tree: root ? [root] : [], count: els.length, truncated: truncated,
        };
    }

    // ── locators ────────────────────────────────────────────────────────
    function containsCI(actual, target) {
        return !!actual && actual.trim().toLowerCase().indexOf(target.toLowerCase()) >= 0;
    }

    function cssQuote(v) {
        return "'" + v.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\n/g, '\\n') + "'";
    }

    function xpathLiteral(v) {
        if (v.indexOf("'") < 0) return "'" + v + "'";
        if (v.indexOf('"') < 0) return '"' + v + '"';
        return 'concat(' + v.split("'").map(function (p) { return "'" + p + "'"; }).join(", \"'\", ") + ')';
    }

    function all(css) {
        try { return Array.prototype.slice.call(document.querySelectorAll(css)); } catch (x) { return []; }
    }

    // Locator.textXPath (case-insensitive contains, innermost only)
    function byText(text) {
        var U = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', L = 'abcdefghijklmnopqrstuvwxyz';
        var p = "contains(translate(normalize-space(.), '" + U + "', '" + L + "'), " + xpathLiteral(text.toLowerCase()) + ')';
        var res = document.evaluate('.//*[' + p + '][not(.//*[' + p + '])]', document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
        var out = [];
        for (var i = 0; i < res.snapshotLength; i++) out.push(res.snapshotItem(i));
        return out;
    }

    function uniqueCss(e) {
        if (e.id && all('#' + CSS.escape(e.id)).length === 1 && !/\d{4,}|[:.]/.test(e.id)) return '#' + CSS.escape(e.id);
        var parts = [];
        for (var cur = e; cur && cur.nodeType === 1 && cur !== document.documentElement; cur = cur.parentElement) {
            if (cur !== e && cur.id && all('#' + CSS.escape(cur.id)).length === 1) { parts.unshift('#' + CSS.escape(cur.id)); break; }
            var part = cur.tagName.toLowerCase();
            var stable = typeof cur.className === 'string'
                ? cur.className.trim().split(/\s+/).filter(function (c) { return c && !/\d{3,}|[:[\]]/.test(c); }).slice(0, 2) : [];
            if (stable.length) part += '.' + stable.map(CSS.escape).join('.');
            var parent = cur.parentElement;
            if (parent) {
                var same = Array.prototype.filter.call(parent.children, function (s) { return s.tagName === cur.tagName; });
                if (same.length > 1) part += ':nth-of-type(' + (same.indexOf(cur) + 1) + ')';
            }
            parts.unshift(part);
            var css = parts.join(' > ');
            if (all(css).length === 1) return css;
        }
        return parts.join(' > ');
    }

    function details(i, opts) {
        var e = els[i];
        if (!e || !e.isConnected) return null;
        var testIdAttr = (opts && opts.testIdAttribute) || 'data-testid';
        var out = [];
        function add(kind, code, list) {
            out.push({ kind: kind, code: code, count: list.length, self: list.indexOf(e) >= 0 });
        }
        var tid = e.getAttribute(testIdAttr);
        if (tid) add('TEST ID', 'getByTestId(' + JSON.stringify(tid) + ')', all('[' + testIdAttr + '=' + cssQuote(tid) + ']'));
        var role = roleOf(e);
        var name = accessibleName(e);
        if (role && name && name.length <= 60) {
            add('ROLE', 'getByRole(Role.' + role[0] + ', ' + JSON.stringify(name) + ')',
                all(role[2]).filter(function (c) { return containsCI(accessibleName(c), name); }));
        }
        var isControl = false;
        try { isControl = e.matches(FORM_CONTROL_CSS); } catch (x) { /* ignore */ }
        if (isControl) {
            var label = e.getAttribute('aria-label') || (e.labels && e.labels.length ? e.labels[0].textContent : '');
            label = (label || '').replace(/\s+/g, ' ').trim();
            if (label && label.length <= 60) {
                add('LABEL', 'getByLabel(' + JSON.stringify(label) + ')',
                    all(FORM_CONTROL_CSS).filter(function (c) { return containsCI(accessibleName(c), label); }));
            }
        }
        var ph = e.getAttribute('placeholder');
        if (ph) add('PLACEHOLDER', 'getByPlaceholder(' + JSON.stringify(ph) + ')', all('[placeholder*=' + cssQuote(ph) + ' i]'));
        var text = (e.innerText || e.textContent || '').replace(/\s+/g, ' ').trim();
        if (!isControl && text && text.length <= 50) {
            var hits = byText(text);
            if (hits.indexOf(e) >= 0) add('TEXT', 'getByText(' + JSON.stringify(text) + ')', hits);
        }
        if (e.id) add('ID', 'findById(' + JSON.stringify(e.id) + ')', all('[id=' + cssQuote(e.id) + ']'));
        var css = uniqueCss(e);
        add('CSS', 'find(' + JSON.stringify(css) + ')', all(css));
        if (role && !name) add('ROLE', 'getByRole(Role.' + role[0] + ')', all(role[2]));

        var attrs = [];
        for (var a = 0; a < e.attributes.length; a++) attrs.push([e.attributes[a].name, clip(e.attributes[a].value, 200)]);
        var r = e.getBoundingClientRect();
        return {
            locators: out, attributes: attrs,
            role: role ? role[1] : '', name: clip(name, 120),
            text: clip(text, 200),
            value: e.type === 'password' ? '' : (typeof e.value === 'string' ? clip(e.value, 200) : ''),
            visible: visible(e, r), disabled: e.disabled === true,
            outer: clip(e.outerHTML.slice(0, e.outerHTML.indexOf('>') + 1), 300),
        };
    }

    // Like Chrome DevTools' "Find by string, selector, or XPath": the union of a CSS selector match, an XPath match
    // and a case-insensitive string match on the element's markup (<tag attr="value">) and own text.
    function markup(e) {
        var s = '<' + e.tagName.toLowerCase();
        for (var a = 0; a < e.attributes.length; a++) s += ' ' + e.attributes[a].name + '="' + e.attributes[a].value + '"';
        return (s + '>' + ownText(e)).toLowerCase();
    }

    function find(query) {
        var q = String(query || '').trim();
        var hits = new Set();
        var kinds = [];
        if (!q) return { indices: [], kinds: kinds };
        if (/^(\/|\(|\.\/|\.\.\/)/.test(q)) {
            try {
                var r = document.evaluate(q, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
                for (var i = 0; i < r.snapshotLength; i++) {
                    var n = r.snapshotItem(i);
                    hits.add(n.nodeType === 1 ? n : n.parentElement);
                }
                if (r.snapshotLength) kinds.push('XPath');
            } catch (x) { /* not XPath */ }
        }
        try {
            var css = document.querySelectorAll(q);
            css.forEach(function (e) { hits.add(e); });
            if (css.length) kinds.push('selector');
        } catch (x) { /* not a selector */ }
        var low = q.toLowerCase();
        var before = hits.size;
        els.forEach(function (e) { if (markup(e).indexOf(low) >= 0) hits.add(e); });
        if (hits.size > before) kinds.push('text');
        var indices = [];
        els.forEach(function (e, idx) { if (hits.has(e)) indices.push(idx); });
        return { indices: indices, kinds: kinds };
    }

    /** Scrolls the element (or document point) into the middle of the viewport; returns its centre in viewport pixels. */
    function center(i) {
        var e = els[i];
        if (!e || !e.isConnected) return null;
        e.scrollIntoView({ block: 'center', inline: 'center' });
        var r = e.getBoundingClientRect();
        return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
    }

    function focus(i) {
        var e = els[i];
        if (e && e.focus) e.focus();
        return !!e;
    }

    return (window.__testflyAgent = { version: VERSION, snapshot: snapshot, details: details, find: find, center: center, focus: focus });
})()
