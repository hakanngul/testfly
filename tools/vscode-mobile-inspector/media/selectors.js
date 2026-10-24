// TestFly selector suggestions for a mobilecli UI element, ranked like TestFly's locator guidance
// (id, role + name, label, placeholder, text, xpath) with how many elements of the tree each one
// matches. The matching rules are a simplified copy of io.testfly.mobile.internal.LocatorEngine.
// Plain script: the webview loads it as a global, the Node tests require it.
(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.TestFlySelectors = factory();
    }
})(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    // shortcut: role mapping covers the common native classes; TestFly's MobileRole has more aliases.
    const ROLE_TYPES = {
        button: ['button', 'imagebutton', 'materialbutton', 'appcompatbutton'],
        textfield: ['textfield', 'securetextfield', 'edittext', 'textinputedittext', 'appcompatedittext', 'searchfield'],
        switch: ['switch', 'switchcompat', 'switchmaterial', 'togglebutton'],
        checkbox: ['checkbox', 'appcompatcheckbox', 'materialcheckbox'],
        image: ['image', 'imageview', 'appcompatimageview'],
        text: ['statictext', 'textview', 'appcompattextview', 'materialtextview'],
        link: ['link'],
        cell: ['cell'],
        tab: ['tab', 'tabbar'],
    };

    function flatten(elements, out) {
        out = out || [];
        for (const e of elements || []) {
            out.push(e);
            flatten(e.children, out);
        }
        return out;
    }

    /** android.widget.EditText -> edittext; XCUIElementTypeTextField / TextField -> textfield. */
    function bareType(type) {
        let t = String(type || '');
        t = t.substring(t.lastIndexOf('.') + 1);
        if (t.startsWith('XCUIElementType')) t = t.substring('XCUIElementType'.length);
        return t.toLowerCase();
    }

    function roleOf(el) {
        const bare = bareType(el.type);
        for (const role of Object.keys(ROLE_TYPES)) {
            if (ROLE_TYPES[role].includes(bare)) return role;
        }
        return null;
    }

    function displayText(el) {
        return el.text || el.label || el.value || '';
    }

    /** Name used by role selectors: label, else text. */
    function accessibleName(el) {
        return el.label || el.text || '';
    }

    function matchesTestId(el, id) {
        const own = el.identifier || '';
        return own === id || own.endsWith(':id/' + id);
    }

    /** Appium-style element name used in XPath: XCUIElementTypeButton, android.widget.Button. */
    function xpathName(el) {
        const type = String(el.type || 'Unknown');
        const name = type.includes('.') || type.startsWith('XCUIElementType') ? type : 'XCUIElementType' + type;
        return name.replace(/[^A-Za-z0-9_.-]/g, '_');
    }

    function quote(value) {
        if (!value.includes("'")) return "'" + value + "'";
        if (!value.includes('"')) return '"' + value + '"';
        return 'concat(' + value.split("'").map(p => "'" + p + "'").join(", \"'\", ") + ')';
    }

    /**
     * Ranked suggestions for `el` within `elements`: [{kind, selector, count}]. `countXpath(expr)`
     * counts XPath matches (the webview passes one built on document.evaluate); without it XPath
     * suggestions get count -1 (unknown).
     */
    function suggest(el, elements, countXpath) {
        const all = flatten(elements);
        const count = pred => all.filter(pred).length;
        const out = [];
        const add = (kind, selector, n) => {
            if (!out.some(s => s.selector === selector)) out.push({ kind, selector, count: n });
        };

        if (el.identifier) {
            const id = el.identifier;
            add('id', id.includes('=') ? 'testid=' + id : id, count(e => matchesTestId(e, id)));
            const short = id.includes(':id/') ? id.substring(id.indexOf(':id/') + 4) : null;
            if (short) add('id', short, count(e => matchesTestId(e, short)));
        }
        const role = roleOf(el);
        const name = accessibleName(el);
        if (role && name) {
            add('role', 'role=' + role + ';name=' + name, count(e => roleOf(e) === role && accessibleName(e) === name));
        }
        if (el.label) add('label', 'label=' + el.label, count(e => e.label === el.label));
        if (el.placeholder) add('placeholder', 'placeholder=' + el.placeholder, count(e => e.placeholder === el.placeholder));
        const text = displayText(el);
        if (text) add('text', 'text=' + text, count(e => displayText(e) === text));

        const xpaths = [];
        if (el.identifier) xpaths.push('//' + xpathName(el) + '[@identifier=' + quote(el.identifier) + ']');
        if (text) xpaths.push('//' + xpathName(el) + '[@text=' + quote(text) + ' or @label=' + quote(text) + ']');
        if (!el.identifier && !text) xpaths.push('(//' + xpathName(el) + ')[' + (all.filter(e => e.type === el.type).indexOf(el) + 1) + ']');
        for (const x of xpaths) {
            add('xpath', 'xpath=' + x, countXpath ? countXpath(x) : -1);
        }
        return out;
    }

    /** XML document mirroring TestFly's XPathQuery, so XPath counts in the inspector match the framework. */
    function toXml(elements, doc) {
        const rootEl = doc.createElement('hierarchy');
        doc.appendChild(rootEl);
        const attrs = ['text', 'label', 'name', 'value', 'placeholder', 'identifier'];
        const add = (parent, el) => {
            const node = doc.createElement(xpathName(el));
            node.setAttribute('type', el.type || '');
            node.setAttribute('class', el.type || '');
            for (const a of attrs) if (el[a]) node.setAttribute(a, el[a]);
            if (el.identifier) node.setAttribute('resource-id', el.identifier);
            if (el.label) node.setAttribute('content-desc', el.label);
            node.setAttribute('enabled', String(el.enabled !== false));
            for (const f of ['selected', 'checked', 'focused']) node.setAttribute(f, String(el[f] === true));
            const r = el.rect || {};
            for (const k of ['x', 'y', 'width', 'height']) node.setAttribute(k, String(r[k] ?? 0));
            parent.appendChild(node);
            for (const c of el.children || []) add(node, c);
        };
        for (const el of elements || []) add(rootEl, el);
        return doc;
    }

    /** A Java field name from the element: loginButton, userNameField, … */
    function fieldName(el) {
        let base = el.identifier ? el.identifier.substring(el.identifier.lastIndexOf('/') + 1) : accessibleName(el) || el.placeholder || bareType(el.type);
        base = base.substring(base.lastIndexOf('.') + 1)
            .replace(/ı/g, 'i').replace(/İ/g, 'I').normalize('NFD').replace(/[\u0300-\u036f]/g, ''); // Giriş -> Giris
        const words = base.replace(/([a-z0-9])([A-Z])/g, '$1 $2').split(/[^A-Za-z0-9]+/).filter(Boolean);
        if (!words.length) return 'element';
        const camel = words.map((w, i) => (i === 0 ? w.charAt(0).toLowerCase() + w.slice(1) : w.charAt(0).toUpperCase() + w.slice(1))).join('');
        return /^[0-9]/.test(camel) ? 'e' + camel : camel;
    }

    /** `@MobileFind(android = "…")` / `@MobileFind(ios = "…")` field for the running platform. */
    function mobileFind(el, selector, platform) {
        const attr = String(platform || '').toLowerCase() === 'ios' ? 'ios' : 'android';
        const escaped = selector.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
        return '@MobileFind(' + attr + ' = "' + escaped + '")\nprivate MobileLocator ' + fieldName(el) + ';';
    }

    /** Smallest element whose rect contains (x, y), in device coordinates. */
    function hitTest(elements, x, y) {
        let best = null;
        let bestArea = Infinity;
        for (const e of flatten(elements)) {
            const r = e.rect;
            if (!r || r.width <= 0 || r.height <= 0) continue;
            if (x >= r.x && x <= r.x + r.width && y >= r.y && y <= r.y + r.height) {
                const area = r.width * r.height;
                if (area <= bestArea) {
                    best = e;
                    bestArea = area;
                }
            }
        }
        return best;
    }

    return { flatten, bareType, roleOf, displayText, suggest, toXml, fieldName, mobileFind, hitTest, xpathName };
});
