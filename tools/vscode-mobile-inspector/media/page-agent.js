// Runs inside the inspected page through Runtime.evaluate; the host calls (source).snapshot(...) etc.
// Locator counts follow io.testfly.locator.Locator: Role css selectors, ACCESSIBLE_NAME_JS,
// FORM_CONTROL_CSS, case-insensitive substring matching by default and innermost-only text matching.
(function () {
    'use strict';
    var VERSION = 4;
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

    // Locator.ACCESSIBLE_NAME_JS; `document` there is the frame the driver is switched into, here the element's own
    function accessibleName(e) {
        if (!e) return '';
        var doc = e.ownerDocument || document;
        var al = e.getAttribute && e.getAttribute('aria-label');
        if (al && al.trim()) return al.trim();
        var lb = e.getAttribute && e.getAttribute('aria-labelledby');
        if (lb) {
            var t = lb.split(/\s+/).map(function (id) { var r = doc.getElementById(id); return r ? r.textContent : ''; }).join(' ').trim();
            if (t) return t;
        }
        if (e.labels && e.labels.length) return Array.prototype.map.call(e.labels, function (l) { return l.textContent; }).join(' ').trim();
        var id = e.getAttribute && e.getAttribute('id');
        if (id) { try { var lab = doc.querySelector('label[for="' + id + '"]'); if (lab) return lab.textContent.trim(); } catch (x) { /* odd id */ } }
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
        var st = (e.ownerDocument.defaultView || window).getComputedStyle(e);
        return st.visibility !== 'hidden' && st.display !== 'none' && parseFloat(st.opacity) !== 0;
    }

    // Where a same-origin frame's viewport sits in the top window's viewport: its box plus its border.
    function frameOffset(doc) {
        var x = 0, y = 0;
        try {
            for (var w = doc.defaultView; w && w.frameElement; w = w.parent) {
                var f = w.frameElement, fr = f.getBoundingClientRect();
                x += fr.left + f.clientLeft;
                y += fr.top + f.clientTop;
            }
        } catch (err) { /* cross-origin parent */ }
        return { x: x, y: y };
    }

    // Shadow roots (open) and same-origin frames show in the tree as #shadow-root and #document nodes, like in DevTools.
    var pseudo = 0;
    function pseudoNode(tag, host, text, kids) {
        var n = { i: -(++pseudo), tag: tag, pseudo: true, text: text || undefined, rect: { x: 0, y: 0, width: 0, height: 0 }, visible: false };
        if (kids && kids.length) n.children = kids;
        return n;
    }

    function snapshot(opts) {
        var testIdAttr = (opts && opts.testIdAttribute) || 'data-testid';
        els = [];
        pseudo = 0;
        var sx = window.scrollX, sy = window.scrollY;
        var truncated = false;
        function node(e, off) {
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
                rect: { x: Math.round(r.left + off.x + sx), y: Math.round(r.top + off.y + sy), width: Math.round(r.width), height: Math.round(r.height) },
                visible: visible(e, r),
                disabled: e.disabled === true || e.getAttribute('aria-disabled') === 'true' || undefined,
            };
            if (role) n.name = clip(accessibleName(e), 80) || undefined;
            if (e.tagName === 'svg') return n; // its shapes are noise in a page tree
            var kids = [];
            if (e.shadowRoot) kids.push(pseudoNode('#shadow-root', e, 'open', childNodes(e.shadowRoot, off)));
            if (e.tagName === 'IFRAME' || e.tagName === 'FRAME') {
                var fdoc = null;
                try { fdoc = e.contentDocument; } catch (x) { /* cross-origin */ }
                if (fdoc && fdoc.body) {
                    var fo = { x: off.x + r.left + e.clientLeft, y: off.y + r.top + e.clientTop };
                    var body = node(fdoc.body, fo);
                    kids.push(pseudoNode('#document', e, clip(fdoc.location.href, 80), body ? [body] : []));
                } else {
                    kids.push(pseudoNode('#document', e, 'cross-origin: not inspected'));
                }
            }
            kids = kids.concat(childNodes(e, off));
            if (kids.length) n.children = kids;
            return n;
        }
        function childNodes(parent, off) {
            var kids = [];
            for (var c = parent.firstElementChild; c; c = c.nextElementSibling) {
                if (SKIP[c.tagName]) continue;
                var k = node(c, off);
                if (k) kids.push(k);
            }
            return kids;
        }
        var root = document.body ? node(document.body, { x: 0, y: 0 }) : null;
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

    /** querySelectorAll in a document or shadow root (the top document by default); [] for an invalid selector. */
    function all(css, root) {
        try { return Array.prototype.slice.call((root || document).querySelectorAll(css)); } catch (x) { return []; }
    }

    // Locator.textXPath (case-insensitive contains, innermost only)
    function byText(text, doc) {
        doc = doc || document;
        var U = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', L = 'abcdefghijklmnopqrstuvwxyz';
        var p = "contains(translate(normalize-space(.), '" + U + "', '" + L + "'), " + xpathLiteral(text.toLowerCase()) + ')';
        var res = doc.evaluate('.//*[' + p + '][not(.//*[' + p + '])]', doc, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
        var out = [];
        for (var i = 0; i < res.snapshotLength; i++) out.push(res.snapshotItem(i));
        return out;
    }

    /** The shortest CSS path that is unique in the element's own document or shadow root. */
    function uniqueCss(e) {
        var root = e.getRootNode();
        if (e.id && all('#' + CSS.escape(e.id), root).length === 1 && !/\d{4,}|[:.]/.test(e.id)) return '#' + CSS.escape(e.id);
        var parts = [];
        for (var cur = e; cur && cur.nodeType === 1 && cur !== e.ownerDocument.documentElement; cur = cur.parentElement) {
            if (cur !== e && cur.id && all('#' + CSS.escape(cur.id), root).length === 1) { parts.unshift('#' + CSS.escape(cur.id)); break; }
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
            if (all(css, root).length === 1) return css;
        }
        return parts.join(' > ');
    }

    /** Frames (outermost first) and shadow hosts around an element, as unique CSS in their own scope. */
    function contextOf(e) {
        var frames = [];
        try {
            for (var w = e.ownerDocument.defaultView; w && w.frameElement; w = w.parent) frames.unshift(uniqueCss(w.frameElement));
        } catch (x) { /* cross-origin parent */ }
        var hosts = [];
        for (var r = e.getRootNode(); r && r.nodeType === 11 && r.host; r = r.host.getRootNode()) hosts.unshift(r.host);
        return { frames: frames, hosts: hosts };
    }

    // ShadowDom.pierce(host, ..., target): each selector runs in the shadow root of the element before it.
    function pierce(chain, doc) {
        var scope = doc;
        for (var i = 0; i < chain.length - 1; i++) {
            var host = all(chain[i], scope)[0];
            if (!host || !host.shadowRoot) return [];
            scope = host.shadowRoot;
        }
        return all(chain[chain.length - 1], scope);
    }

    function details(i, opts) {
        var e = els[i];
        if (!e || !e.isConnected) return null;
        var testIdAttr = (opts && opts.testIdAttribute) || 'data-testid';
        var out = [];
        function add(kind, code, list) {
            out.push({ kind: kind, code: code, count: list.length, self: list.indexOf(e) >= 0 });
        }
        // Locators run in the element's document: the page, or the frame withinFrame(...) switches into.
        var doc = e.ownerDocument;
        var ctx = contextOf(e);
        var role = roleOf(e);
        var name = accessibleName(e);
        var text = (e.innerText || e.textContent || '').replace(/\s+/g, ' ').trim();
        var isControl = false;
        try { isControl = e.matches(FORM_CONTROL_CSS); } catch (x) { /* ignore */ }
        if (ctx.hosts.length) {
            // inside a shadow root: TestFly's locators do not reach it, ShadowDom.pierce does (CSS only)
            var chain = ctx.hosts.map(uniqueCss).concat([uniqueCss(e)]);
            add('SHADOW', 'ShadowDom.pierce(' + chain.map(function (c) { return JSON.stringify(c); }).join(', ') + ')', pierce(chain, doc));
        } else {
            var tid = e.getAttribute(testIdAttr);
            if (tid) add('TEST ID', 'getByTestId(' + JSON.stringify(tid) + ')', all('[' + testIdAttr + '=' + cssQuote(tid) + ']', doc));
            if (role && name && name.length <= 60) {
                add('ROLE', 'getByRole(Role.' + role[0] + ', ' + JSON.stringify(name) + ')',
                    all(role[2], doc).filter(function (c) { return containsCI(accessibleName(c), name); }));
            }
            if (isControl) {
                var label = e.getAttribute('aria-label') || (e.labels && e.labels.length ? e.labels[0].textContent : '');
                label = (label || '').replace(/\s+/g, ' ').trim();
                if (label && label.length <= 60) {
                    add('LABEL', 'getByLabel(' + JSON.stringify(label) + ')',
                        all(FORM_CONTROL_CSS, doc).filter(function (c) { return containsCI(accessibleName(c), label); }));
                }
            }
            var ph = e.getAttribute('placeholder');
            if (ph) add('PLACEHOLDER', 'getByPlaceholder(' + JSON.stringify(ph) + ')', all('[placeholder*=' + cssQuote(ph) + ' i]', doc));
            if (!isControl && text && text.length <= 50) {
                var hits = byText(text, doc);
                if (hits.indexOf(e) >= 0) add('TEXT', 'getByText(' + JSON.stringify(text) + ')', hits);
            }
            if (e.id) add('ID', 'findById(' + JSON.stringify(e.id) + ')', all('[id=' + cssQuote(e.id) + ']', doc));
            var css = uniqueCss(e);
            add('CSS', 'find(' + JSON.stringify(css) + ')', all(css, doc));
            if (role && !name) add('ROLE', 'getByRole(Role.' + role[0] + ')', all(role[2], doc));
        }

        var attrs = [];
        for (var a = 0; a < e.attributes.length; a++) attrs.push([e.attributes[a].name, clip(e.attributes[a].value, 200)]);
        var r = e.getBoundingClientRect();
        return {
            locators: out, attributes: attrs, frames: ctx.frames, shadow: ctx.hosts.length > 0,
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
        // like DevTools, the selector also runs inside frames and shadow roots
        var roots = [document];
        els.forEach(function (e) {
            if (e.shadowRoot) roots.push(e.shadowRoot);
            if (e.tagName === 'IFRAME' || e.tagName === 'FRAME') { try { if (e.contentDocument) roots.push(e.contentDocument); } catch (x) { /* cross-origin */ } }
        });
        var before0 = hits.size;
        try {
            roots.forEach(function (root) { root.querySelectorAll(q).forEach(function (e) { hits.add(e); }); });
            if (hits.size > before0) kinds.push('selector');
        } catch (x) { /* not a selector */ }
        var low = q.toLowerCase();
        var before = hits.size;
        els.forEach(function (e) { if (markup(e).indexOf(low) >= 0) hits.add(e); });
        if (hits.size > before) kinds.push('text');
        var indices = [];
        els.forEach(function (e, idx) { if (hits.has(e)) indices.push(idx); });
        return { indices: indices, kinds: kinds };
    }

    // The elements a TestFly Locator factory finds, as io.testfly.locator.Locator builds them (default, non-exact matching).
    function locate(kind, args, opts) {
        var v = args[0];
        var list;
        switch (kind) {
            case 'testId': list = all('[' + ((opts && opts.testIdAttribute) || 'data-testid') + '=' + cssQuote(v) + ']'); break;
            case 'role':
                var role = null;
                for (var i = 0; i < ROLES.length; i++) if (ROLES[i][0] === v) role = ROLES[i];
                if (!role) return { error: 'Role.' + v + ' is not a TestFly Role' };
                list = all(role[2]);
                if (args.length > 1) list = list.filter(function (c) { return containsCI(accessibleName(c), args[1]); });
                break;
            case 'label': list = all(FORM_CONTROL_CSS).filter(function (c) { return containsCI(accessibleName(c), v); }); break;
            case 'placeholder': list = all('[placeholder*=' + cssQuote(v) + ' i]'); break;
            case 'altText': list = all('[alt*=' + cssQuote(v) + ' i]'); break;
            case 'title': list = all('[title*=' + cssQuote(v) + ' i]'); break;
            case 'text': list = byText(v); break;
            case 'id': list = all('[id=' + cssQuote(v) + ']'); break;
            case 'name': list = all('[name=' + cssQuote(v) + ']'); break;
            case 'className':
                try { list = Array.prototype.slice.call(document.getElementsByClassName(v)); } catch (x) { list = []; }
                break;
            case 'xpath':
                try {
                    var r = document.evaluate(v, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
                    list = [];
                    for (var j = 0; j < r.snapshotLength; j++) if (r.snapshotItem(j).nodeType === 1) list.push(r.snapshotItem(j));
                } catch (x) { return { error: 'Invalid XPath: ' + x.message }; }
                break;
            case 'css':
                try { list = Array.prototype.slice.call(document.querySelectorAll(v)); } catch (x) { return { error: 'Invalid CSS selector' }; }
                break;
            default: return { error: 'Unknown locator ' + kind };
        }
        var indices = [];
        list.forEach(function (e) { var k = els.indexOf(e); if (k >= 0) indices.push(k); });
        return { count: list.length, indices: indices };
    }

    function locateMany(list, opts) {
        return list.map(function (l) { return locate(l.kind, l.args, opts); });
    }

    /** Scrolls the element (or document point) into the middle of the viewport; returns its centre in viewport pixels. */
    function center(i) {
        var e = els[i];
        if (!e || !e.isConnected) return null;
        e.scrollIntoView({ block: 'center', inline: 'center' });
        var r = e.getBoundingClientRect();
        var off = frameOffset(e.ownerDocument);
        return { x: off.x + r.left + r.width / 2, y: off.y + r.top + r.height / 2 };
    }

    function focus(i) {
        var e = els[i];
        if (e && e.focus) e.focus();
        return !!e;
    }

    return (window.__testflyAgent = { version: VERSION, snapshot: snapshot, details: details, find: find, locate: locate, locateMany: locateMany, center: center, focus: focus });
})()
