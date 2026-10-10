/*
 * TestFly locator scanner — runs inside the page (Selenium executeScript or any
 * agent browser "evaluate" tool). Returns a JSON string describing every
 * interactive / meaningful element and the most resilient TestFly Locator
 * expression that resolves to exactly that element.
 *
 * Matching rules mirror io.testfly.locator.Locator / Role:
 *   - getByRole(Role, name): role CSS, then case-insensitive substring on the
 *     accessible name; .exact() = trimmed equality.
 *   - getByLabel: form-control CSS + the same accessible-name filter.
 *   - getByPlaceholder/AltText/Title: [attr*='v' i]; getByTestId: [data-testid='v'].
 *   - getByText: innermost element whose normalize-space(.) contains the text.
 *   - Locator does NOT filter hidden elements when resolving; hidden duplicates
 *     count, and the first match wins. Uniqueness here counts hidden ones too.
 *
 * Usage (Selenium): return eval(script)(configOrNull)
 * Usage (browser tool): paste the file, then `__testflyLocatorScan()`.
 */
(function () {
  var DEFAULT_ROLES = [
    ["BUTTON", "button", "button, [role='button'], input[type='button'], input[type='submit'], input[type='reset'], input[type='image'], summary"],
    ["LINK", "link", "a[href], area[href], [role='link']"],
    ["CHECKBOX", "checkbox", "input[type='checkbox'], [role='checkbox']"],
    ["RADIO", "radio", "input[type='radio'], [role='radio']"],
    ["SWITCH", "switch", "[role='switch']"],
    ["TEXTBOX", "textbox", "input:not([type]), input[type='text'], input[type='email'], input[type='password'], input[type='tel'], input[type='url'], input[type='number'], textarea, [role='textbox'], [contenteditable='true']"],
    ["SEARCHBOX", "searchbox", "input[type='search'], [role='searchbox']"],
    ["COMBOBOX", "combobox", "select, [role='combobox']"],
    ["OPTION", "option", "option, [role='option']"],
    ["HEADING", "heading", "h1, h2, h3, h4, h5, h6, [role='heading']"],
    ["IMG", "img", "img, [role='img']"],
    ["TAB", "tab", "[role='tab']"],
    ["MENUITEM", "menuitem", "[role='menuitem']"],
    ["SPINBUTTON", "spinbutton", "input[type='number'], [role='spinbutton']"],
    ["SLIDER", "slider", "input[type='range'], [role='slider']"],
    ["DIALOG", "dialog", "dialog, [role='dialog']"],
    ["ALERT", "alert", "[role='alert']"]
  ];
  var DEFAULT_FORM_CONTROL_CSS = "input:not([type='hidden']):not([type='button']):not([type='submit']):not([type='reset']), textarea, select, [contenteditable='true'], [role='textbox'], [role='searchbox'], [role='combobox'], [role='checkbox'], [role='radio'], [role='switch'], [role='slider'], [role='spinbutton']";
  // Elements worth a locator. Structural roles (LIST, ROW, REGION, ...) are skipped.
  var TARGET_CSS = "a[href], button, input:not([type='hidden']), select, textarea, summary, [role='button'], [role='link'], [role='checkbox'], [role='radio'], [role='switch'], [role='tab'], [role='menuitem'], [role='option'], [role='combobox'], [role='textbox'], [role='searchbox'], [role='slider'], [role='spinbutton'], [contenteditable='true'], h1, h2, h3, h4, h5, h6, [role='heading'], img[alt], [role='img'][aria-label], [role='alert'], dialog, [role='dialog']";

  // Port of Locator.ACCESSIBLE_NAME_JS — keep in sync (the Java driver passes the real one).
  function defaultAccessibleName(e) {
    if (!e) return '';
    var al = e.getAttribute && e.getAttribute('aria-label');
    if (al && al.trim()) return al.trim();
    var lb = e.getAttribute && e.getAttribute('aria-labelledby');
    if (lb) { var t = lb.split(/\s+/).map(function (id) { var r = document.getElementById(id); return r ? r.textContent : ''; }).join(' ').trim(); if (t) return t; }
    if (e.labels && e.labels.length) { return Array.prototype.map.call(e.labels, function (l) { return l.textContent; }).join(' ').trim(); }
    var id = e.getAttribute && e.getAttribute('id');
    if (id) { try { var lab = document.querySelector('label[for="' + id + '"]'); if (lab) return lab.textContent.trim(); } catch (x) { } }
    var wrap = e.closest && e.closest('label'); if (wrap) return wrap.textContent.trim();
    var txt = (e.textContent || '').trim(); if (txt) return txt;
    var alt = e.getAttribute && e.getAttribute('alt'); if (alt && alt.trim()) return alt.trim();
    var ti = e.getAttribute && e.getAttribute('title'); if (ti && ti.trim()) return ti.trim();
    var val = e.value; if (val) return ('' + val).trim();
    var ph = e.getAttribute && e.getAttribute('placeholder'); if (ph && ph.trim()) return ph.trim();
    return '';
  }

  function scan(config) {
    config = config || {};
    var roles = config.roles || DEFAULT_ROLES;
    var formControlCss = config.formControlCss || DEFAULT_FORM_CONTROL_CSS;
    var testIdAttr = config.testIdAttribute || 'data-testid';
    var maxElements = config.maxElements || 300;
    var includeHidden = !!config.includeHidden;
    var accName = defaultAccessibleName;
    if (config.accessibleNameJs) {
      accName = new Function('el', 'return (function(){' + config.accessibleNameJs.replace(/arguments\[0\]/g, 'el') + '})();');
    }

    function norm(s) { return (s || '').replace(/\s+/g, ' ').trim(); }
    function qsa(css, root) { try { return Array.prototype.slice.call((root || document).querySelectorAll(css)); } catch (e) { return null; } }
    function cssStr(v) { return v.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\n/g, '\\n').replace(/\r/g, '\\r'); }
    function javaStr(v) { return '"' + v.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n') + '"'; }
    function visible(el) {
      var r = el.getBoundingClientRect(); var st = getComputedStyle(el);
      return r.width > 0 && r.height > 0 && st.visibility !== 'hidden' && st.display !== 'none';
    }
    function xpathLiteral(v) {
      if (v.indexOf("'") < 0) return "'" + v + "'";
      if (v.indexOf('"') < 0) return '"' + v + '"';
      return "concat('" + v.split("'").join("', \"'\", '") + "')";
    }
    function textXPath(v, exact) {
      var U = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', L = 'abcdefghijklmnopqrstuvwxyz';
      var p = exact ? 'normalize-space(.)=' + xpathLiteral(v)
        : "contains(translate(normalize-space(.), '" + U + "', '" + L + "'), " + xpathLiteral(v.toLowerCase()) + ')';
      return './/*[' + p + '][not(.//*[' + p + '])]';
    }
    function byXPath(xp) {
      var out = [];
      try {
        var r = document.evaluate(xp, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
        for (var i = 0; i < r.snapshotLength; i++) out.push(r.snapshotItem(i));
      } catch (e) { return null; }
      return out;
    }
    function nameFilter(list, name, exact) {
      var t = exact ? name : name.toLowerCase();
      return list.filter(function (el) {
        var a = accName(el); if (!a) return false; a = a.trim();
        return exact ? a === t : a.toLowerCase().indexOf(t) >= 0;
      });
    }
    // Generated/unstable tokens: React useId, Ember/Angular/Vue counters, hashes, long digit runs.
    function stableToken(v) {
      if (!v || v.length > 60) return false;
      if (/^[:_]?r[0-9a-z]*:?$/.test(v) || /^:/.test(v)) return false;
      if (/^(ember|ng-|mat-|cdk-|react-|radix-|headlessui-|__)/i.test(v)) return false;
      if (/\d{4,}/.test(v) || /[0-9a-f]{8,}/i.test(v)) return false;
      if (/[-_][a-z0-9]{5,}$/i.test(v) && /\d/.test(v.slice(-6))) return false;
      return true;
    }
    function roleOf(el) {
      var explicit = el.getAttribute('role');
      var match = null;
      for (var i = 0; i < roles.length; i++) {
        if (explicit && roles[i][1] === explicit) return roles[i];
        if (!match) { try { if (el.matches(roles[i][2])) match = roles[i]; } catch (e) { } }
      }
      return match;
    }
    function shortText(s, n) { s = norm(s); return s.length > n ? s.slice(0, n) : s; }

    // Candidate = { strategy, java, matches(): Element[] }
    function candidates(el) {
      var c = [];
      var tid = el.getAttribute(testIdAttr);
      if (tid) c.push({ strategy: 'testId', java: 'getByTestId(' + javaStr(tid) + ')', css: '[' + testIdAttr + "='" + cssStr(tid) + "']",
        spec: { kind: 'testId', value: tid }, run: function () { return qsa('[' + testIdAttr + "='" + cssStr(tid) + "']"); } });

      var isControl = false; try { isControl = el.matches(formControlCss); } catch (e) { }
      if (isControl) {
        var labelText = '';
        if (el.labels && el.labels.length) labelText = norm(el.labels[0].textContent);
        else if (el.getAttribute('aria-label')) labelText = norm(el.getAttribute('aria-label'));
        if (labelText && labelText.length <= 80) {
          c.push({ strategy: 'label', java: 'getByLabel(' + javaStr(labelText) + ')',
            spec: { kind: 'label', value: labelText }, run: function () { var l = qsa(formControlCss); return l && nameFilter(l, labelText, false); } });
          c.push({ strategy: 'label', java: 'getByLabel(' + javaStr(labelText) + ').exact()',
            spec: { kind: 'label', value: labelText, exact: true }, run: function () { var l = qsa(formControlCss); return l && nameFilter(l, labelText, true); } });
        }
      }

      var role = roleOf(el);
      var name = norm(accName(el));
      // A bare <select> "names" itself with the concatenated option text — useless as a locator.
      var nameFromOptions = el.tagName === 'SELECT' && !(el.labels && el.labels.length) && !el.getAttribute('aria-label') && !el.getAttribute('aria-labelledby');
      if (role && name && name.length <= 80 && !nameFromOptions) {
        var rcss = role[2];
        if (role[0] === 'HEADING') {
          var lvl = /^H[1-6]$/.test(el.tagName) ? +el.tagName[1] : +(el.getAttribute('aria-level') || 0);
        }
        c.push({ strategy: 'role', java: 'getByRole(Role.' + role[0] + ', ' + javaStr(name) + ')',
          spec: { kind: 'role', role: role[0], name: name }, run: function () { var l = qsa(rcss); return l && nameFilter(l, name, false); } });
        c.push({ strategy: 'role', java: 'getByRole(Role.' + role[0] + ', ' + javaStr(name) + ').exact()',
          spec: { kind: 'role', role: role[0], name: name, exact: true }, run: function () { var l = qsa(rcss); return l && nameFilter(l, name, true); } });
        if (role[0] === 'HEADING' && lvl) {
          var hcss = 'h' + lvl + ", [role='heading'][aria-level='" + lvl + "']";
          c.push({ strategy: 'role', java: 'getByRole(Role.HEADING, ' + javaStr(name) + ').withLevel(' + lvl + ')',
            spec: { kind: 'role', role: 'HEADING', name: name, level: lvl }, run: function () { var l = qsa(hcss); return l && nameFilter(l, name, false); } });
        }
      }

      [['placeholder', 'getByPlaceholder', 'placeholder'], ['alt', 'getByAltText', 'altText'], ['title', 'getByTitle', 'title']].forEach(function (a) {
        var v = el.getAttribute(a[0]);
        if (v && norm(v) && v.length <= 80) {
          c.push({ strategy: a[2], java: a[1] + '(' + javaStr(v) + ')', spec: { kind: a[2], value: v },
            run: function () { return qsa('[' + a[0] + "*='" + cssStr(v) + "' i]"); } });
          c.push({ strategy: a[2], java: a[1] + '(' + javaStr(v) + ').exact()', spec: { kind: a[2], value: v, exact: true },
            run: function () { return qsa('[' + a[0] + "='" + cssStr(v) + "']"); } });
        }
      });

      var id = el.id;
      if (id && stableToken(id)) c.push({ strategy: 'id', java: 'findById(' + javaStr(id) + ')', css: '#' + CSS.escape(id),
        spec: { kind: 'id', value: id }, run: function () { return qsa('[id="' + id.replace(/"/g, '\\"') + '"]'); } });

      var nm = el.getAttribute('name');
      if (nm && stableToken(nm)) c.push({ strategy: 'name', java: 'findByName(' + javaStr(nm) + ')',
        spec: { kind: 'name', value: nm }, run: function () { return qsa('[name="' + nm.replace(/"/g, '\\"') + '"]'); } });

      var txt = norm(el.innerText || el.textContent);
      if (txt && txt.length <= 60 && !isControl) {
        c.push({ strategy: 'text', java: 'getByText(' + javaStr(txt) + ')', spec: { kind: 'text', value: txt },
          run: function () { return byXPath(textXPath(txt, false)); } });
        c.push({ strategy: 'text', java: 'getByText(' + javaStr(txt) + ').exact()', spec: { kind: 'text', value: txt, exact: true },
          run: function () { return byXPath(textXPath(txt, true)); } });
      }

      var css = uniqueCss(el);
      if (css) c.push({ strategy: 'css', java: 'find(' + javaStr(css) + ')', css: css, spec: { kind: 'css', value: css },
        run: function () { return qsa(css); } });
      return c;
    }

    function stableClasses(el) {
      return Array.prototype.filter.call(el.classList || [], function (k) {
        return stableToken(k) && !/^(css|sc|jsx|emotion|styled)-/i.test(k) && !/^(active|hover|focus|open|show|selected|disabled)$/i.test(k);
      }).slice(0, 2);
    }
    function segment(el) {
      var tag = el.tagName.toLowerCase();
      if (el.id && stableToken(el.id)) return tag + '#' + CSS.escape(el.id);
      var s = tag;
      ['name', 'type', 'aria-label', 'href', 'role'].forEach(function (a) {
        var v = el.getAttribute(a);
        if (v && a !== 'href' && v.length < 40 && (a !== 'name' || stableToken(v))) s += '[' + a + "='" + cssStr(v) + "']";
        if (a === 'href' && v && v.length < 60 && !/^javascript:/i.test(v) && v !== '#') s += "[href='" + cssStr(v) + "']";
      });
      stableClasses(el).forEach(function (k) { s += '.' + CSS.escape(k); });
      return s;
    }
    function uniqueCss(el) {
      var path = [], cur = el;
      for (var depth = 0; cur && cur.nodeType === 1 && depth < 5; depth++) {
        var seg = segment(cur);
        var trial = [seg].concat(path).join(' > ');
        var hits = qsa(trial);
        if (hits && hits.length === 1 && hits[0] === el) return trial;
        var parent = cur.parentElement;
        if (parent) {
          var same = Array.prototype.filter.call(parent.children, function (s) { return s.tagName === cur.tagName; });
          if (same.length > 1) {
            var withNth = seg + ':nth-of-type(' + (same.indexOf(cur) + 1) + ')';
            trial = [withNth].concat(path).join(' > ');
            hits = qsa(trial);
            if (hits && hits.length === 1 && hits[0] === el) return trial;
            seg = withNth;
          }
        }
        path.unshift(seg);
        if (/#/.test(seg)) break; // anchored on a stable id, stop climbing
        cur = parent;
      }
      var full = path.join(' > ');
      var h = qsa(full);
      return h && h.length === 1 && h[0] === el ? full : null;
    }

    function fieldName(el, best) {
      var role = roleOf(el);
      var bareSelect = el.tagName === 'SELECT' && !(el.labels && el.labels.length) && !el.getAttribute('aria-label');
      var base = norm((bareSelect ? '' : accName(el)) || el.getAttribute('placeholder') || el.getAttribute(testIdAttr) || el.id || el.getAttribute('name') || el.tagName)
        .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
        .replace(/ı/g, 'i').replace(/İ/g, 'I').replace(/[^A-Za-z0-9]+/g, ' ').trim().split(' ').slice(0, 4);
      var suffix = { BUTTON: 'Button', LINK: 'Link', CHECKBOX: 'Checkbox', RADIO: 'Radio', TEXTBOX: 'Input', SEARCHBOX: 'Input',
        COMBOBOX: 'Select', HEADING: 'Heading', IMG: 'Image', TAB: 'Tab', MENUITEM: 'MenuItem', SWITCH: 'Switch' }[role ? role[0] : ''] || 'Element';
      var words = base.filter(Boolean).map(function (w, i) { w = w.toLowerCase(); return i === 0 ? w : w[0].toUpperCase() + w.slice(1); });
      var n = words.join('') || 'element';
      if (/^[0-9]/.test(n)) n = 'e' + n;
      if (n.toLowerCase().slice(-suffix.length) !== suffix.toLowerCase()) n += suffix;
      return n;
    }

    var all = qsa(TARGET_CSS) || [];
    var seen = new Set(), out = [], usedNames = {}, elements = [];
    for (var i = 0; i < all.length && out.length < maxElements; i++) {
      var el = all[i];
      if (seen.has(el)) continue; seen.add(el);
      var vis = visible(el);
      if (!vis && !includeHidden) continue;
      var cands = candidates(el), best = null, alternatives = [];
      for (var j = 0; j < cands.length; j++) {
        var m = cands[j].run();
        if (!m) continue;
        var ok = m.length === 1 && m[0] === el;
        if (ok) { if (!best) best = cands[j]; else if (alternatives.length < 3 && alternatives.indexOf(cands[j].java) < 0) alternatives.push(cands[j].java); }
      }
      var nthFallback = null;
      if (!best && cands.length) {
        // Last resort: first candidate that contains the element, pinned with nth().
        for (var k = 0; k < cands.length && !nthFallback; k++) {
          var mm = cands[k].run(); var idx = mm ? mm.indexOf(el) : -1;
          if (idx >= 0) {
            nthFallback = Object.assign({}, cands[k], { java: cands[k].java + '.nth(' + idx + ')', spec: Object.assign({}, cands[k].spec, { nth: idx }) });
          }
        }
      }
      var chosen = best || nthFallback;
      if (!chosen) continue;
      var fname = fieldName(el, chosen);
      if (usedNames[fname]) { usedNames[fname]++; fname += usedNames[fname]; } else usedNames[fname] = 1;
      var r = roleOf(el);
      out.push({
        field: fname,
        java: chosen.java,
        strategy: chosen.strategy,
        spec: chosen.spec,
        unique: !!best,
        visible: vis,
        role: r ? r[0] : null,
        tag: el.tagName.toLowerCase(),
        text: shortText(accName(el) || el.getAttribute('placeholder') || '', 60),
        alternatives: alternatives
      });
      elements.push(el);
    }
    // Other test-id conventions present on the page -> candidates for Locator.setTestIdAttribute(...).
    var testIdHints = {};
    ['data-test', 'data-test-id', 'data-qa', 'data-cy', 'data-automation-id', 'data-testid'].forEach(function (a) {
      if (a === testIdAttr) return;
      var n = (qsa('[' + a + ']') || []).length; if (n) testIdHints[a] = n;
    });
    var json = JSON.stringify({ url: location.href, title: document.title, testIdAttribute: testIdAttr, testIdHints: testIdHints, count: out.length, locators: out });
    // Selenium callers ask for the DOM nodes too, so the driver can prove each locator hits the same node.
    return config.returnElements ? { json: json, elements: elements } : json;
  }

  if (typeof window !== 'undefined') window.__testflyLocatorScan = scan;
  return scan;
})()
