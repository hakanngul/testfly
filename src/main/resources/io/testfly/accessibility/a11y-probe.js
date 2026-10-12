/*
 * TestFly accessibility probe. Runs as a WebDriver executeScript body.
 *
 *   arguments[0]  root element, or null for document.body
 *   arguments[1]  { mode: 'node' | 'tree', maxDepth, maxNodes, roles: [ ... ] }
 *
 * Returns a JSON string.
 *   mode 'node' -> { role, name, description, attrs }   (role is null for generic elements)
 *   mode 'tree' -> { nodes: [ { role, name?, attrs, children } ], truncated }
 *
 * Only the roles listed in opts.roles are reported, so a snapshot always round-trips
 * through the template parser. The accessible name follows the same precedence as
 * Locator.withName(): aria-label, aria-labelledby, label, then (for roles that take their
 * name from content) the visible text, then alt, title, value of buttons and placeholder.
 * This is a deliberate simplification of the full accname specification.
 */
var root = arguments[0] || document.body;
var opts = arguments[1] || {};
var mode = opts.mode || 'tree';
var maxDepth = opts.maxDepth || 25;
var maxNodes = opts.maxNodes || 2000;
var known = {};
(opts.roles || []).forEach(function (r) { known[r] = true; });

// Roles whose accessible name may come from their visible text.
var CONTENT = {
  button: 1, link: 1, heading: 1, option: 1, tab: 1, menuitem: 1,
  checkbox: 1, radio: 1, 'switch': 1, cell: 1, columnheader: 1, paragraph: 1
};
// Roles that never report children.
var LEAF = {
  textbox: 1, searchbox: 1, spinbutton: 1, slider: 1, progressbar: 1, img: 1, separator: 1
};
var SKIP_TAGS = {
  script: 1, style: 1, template: 1, noscript: 1, head: 1, meta: 1, link: 1, title: 1
};
var TEXTBOX_TYPES = { text: 1, email: 1, password: 1, tel: 1, url: 1 };
var BUTTON_TYPES = { button: 1, submit: 1, reset: 1, image: 1 };

var count = 0;
var truncated = false;

function norm(s) { return (s || '').replace(/\s+/g, ' ').trim(); }

function isHidden(el) {
  if (el.nodeType !== 1) return false;
  if (SKIP_TAGS[el.tagName.toLowerCase()]) return true;
  if (el.hasAttribute('hidden')) return true;
  if (el.getAttribute('aria-hidden') === 'true') return true;
  var s = window.getComputedStyle(el);
  return s.display === 'none' || s.visibility === 'hidden';
}

function roleOf(el) {
  var tag = el.tagName.toLowerCase();
  var explicit = (el.getAttribute('role') || '').trim().split(/\s+/)[0];
  if (explicit && known[explicit]) return explicit;
  var role = null;
  switch (tag) {
    case 'a': case 'area': role = el.hasAttribute('href') ? 'link' : null; break;
    case 'button': case 'summary': role = 'button'; break;
    case 'input':
      var t = (el.getAttribute('type') || 'text').toLowerCase();
      if (BUTTON_TYPES[t]) role = 'button';
      else if (t === 'checkbox') role = 'checkbox';
      else if (t === 'radio') role = 'radio';
      else if (t === 'range') role = 'slider';
      else if (t === 'number') role = 'spinbutton';
      else if (t === 'search') role = 'searchbox';
      else if (TEXTBOX_TYPES[t]) role = 'textbox';
      break;
    case 'textarea': role = 'textbox'; break;
    case 'select': role = 'combobox'; break;
    case 'option': role = 'option'; break;
    case 'h1': case 'h2': case 'h3': case 'h4': case 'h5': case 'h6': role = 'heading'; break;
    case 'img': role = 'img'; break;
    case 'ul': case 'ol': role = 'list'; break;
    case 'li': role = 'listitem'; break;
    case 'table': role = 'table'; break;
    case 'tr': role = 'row'; break;
    case 'td': role = 'cell'; break;
    case 'th': role = 'columnheader'; break;
    case 'nav': role = 'navigation'; break;
    case 'header': role = 'banner'; break;
    case 'footer': role = 'contentinfo'; break;
    case 'main': role = 'main'; break;
    case 'section': role = 'region'; break;
    case 'article': role = 'article'; break;
    case 'dialog': role = 'dialog'; break;
    case 'menu': role = 'menu'; break;
    case 'progress': role = 'progressbar'; break;
    case 'p': role = 'paragraph'; break;
    case 'hr': role = 'separator'; break;
  }
  if (!role) {
    var ce = el.getAttribute('contenteditable');
    if (ce !== null && (ce === '' || ce.toLowerCase() === 'true')) role = 'textbox';
  }
  return role && known[role] ? role : null;
}

// Visible text of a subtree. Block-level elements are separated by a space.
function textOf(n) {
  if (n.nodeType === 3) return n.nodeValue;
  if (n.nodeType !== 1 || isHidden(n)) return '';
  var out = '';
  for (var c = n.firstChild; c; c = c.nextSibling) out += textOf(c);
  var d = window.getComputedStyle(n).display;
  return d.indexOf('inline') === 0 ? out : ' ' + out + ' ';
}

function refText(ids) {
  return norm(ids.split(/\s+/).map(function (id) {
    var r = document.getElementById(id);
    return r ? r.textContent : '';
  }).join(' '));
}

function nameOf(el, role) {
  var v = norm(el.getAttribute('aria-label'));
  if (v) return v;
  var lb = el.getAttribute('aria-labelledby');
  if (lb) { v = refText(lb); if (v) return v; }
  if (el.labels && el.labels.length) {
    v = norm(Array.prototype.map.call(el.labels, function (l) { return l.textContent; }).join(' '));
    if (v) return v;
  }
  var id = el.getAttribute('id');
  if (id) {
    try {
      var lab = document.querySelector('label[for="' + id + '"]');
      if (lab) { v = norm(lab.textContent); if (v) return v; }
    } catch (e) { /* invalid id for a selector */ }
  }
  var wrap = el.closest && el.closest('label');
  if (wrap && role !== 'link' && role !== 'button') { v = norm(wrap.textContent); if (v) return v; }
  if (role && CONTENT[role]) { v = norm(textOf(el)); if (v) return v; }
  v = norm(el.getAttribute('alt')); if (v) return v;
  v = norm(el.getAttribute('title')); if (v) return v;
  if (el.tagName === 'INPUT' && BUTTON_TYPES[(el.getAttribute('type') || '').toLowerCase()]) {
    v = norm(el.value); if (v) return v;
  }
  v = norm(el.getAttribute('placeholder')); if (v) return v;
  return '';
}

function descriptionOf(el, name) {
  var db = el.getAttribute('aria-describedby');
  if (db) { var v = refText(db); if (v) return v; }
  var ad = norm(el.getAttribute('aria-description'));
  if (ad) return ad;
  var ti = norm(el.getAttribute('title'));
  if (ti && ti !== name) return ti;
  return '';
}

function statesOf(el, role) {
  var s = {};
  var tag = el.tagName.toLowerCase();
  if (role === 'heading') {
    var m = /^h([1-6])$/.exec(tag);
    var lv = m ? parseInt(m[1], 10) : parseInt(el.getAttribute('aria-level'), 10);
    if (lv) s.level = lv;
  }
  if (el.type === 'checkbox' || el.type === 'radio') {
    if (el.indeterminate) s.checked = 'mixed'; else if (el.checked) s.checked = true;
  } else if (el.hasAttribute('aria-checked')) {
    var ac = el.getAttribute('aria-checked');
    if (ac === 'mixed') s.checked = 'mixed'; else if (ac === 'true') s.checked = true;
  }
  var disabled = el.getAttribute('aria-disabled') === 'true';
  try { disabled = disabled || el.matches(':disabled'); } catch (e) { /* ignore */ }
  if (disabled) s.disabled = true;
  if (el.getAttribute('aria-expanded') === 'true') s.expanded = true;
  if (el.getAttribute('aria-selected') === 'true' || (tag === 'option' && el.selected)) s.selected = true;
  var ap = el.getAttribute('aria-pressed');
  if (ap === 'true') s.pressed = true; else if (ap === 'mixed') s.pressed = 'mixed';
  return s;
}

function decorativeImage(el) {
  return el.tagName === 'IMG' && el.getAttribute('alt') === '' && !el.getAttribute('role');
}

function walkChildren(parent, depth, consumesText, out) {
  for (var c = parent.firstChild; c; c = c.nextSibling) {
    if (count >= maxNodes) { truncated = true; return; }
    if (c.nodeType === 3) {
      if (!consumesText) {
        var t = norm(c.nodeValue);
        if (t) { out.push({ role: 'text', name: t, attrs: {}, children: [] }); count++; }
      }
      continue;
    }
    if (c.nodeType !== 1 || isHidden(c)) continue;
    walkElement(c, depth, consumesText, out);
  }
}

function walkElement(el, depth, consumesText, out) {
  var role = roleOf(el);
  if (!role || decorativeImage(el)) { walkChildren(el, depth, consumesText, out); return; }
  if (depth >= maxDepth) { truncated = true; return; }
  count++;
  var node = { role: role, attrs: statesOf(el, role), children: [] };
  var nm = nameOf(el, role);
  if (nm) node.name = nm;
  out.push(node);
  if (!LEAF[role]) walkChildren(el, depth + 1, !!CONTENT[role], node.children);
}

if (mode === 'node') {
  var r = roleOf(root);
  var n = nameOf(root, r);
  return JSON.stringify({
    role: r,
    name: n,
    description: descriptionOf(root, n),
    attrs: r ? statesOf(root, r) : {}
  });
}

var nodes = [];
walkElement(root, 0, false, nodes);
return JSON.stringify({ nodes: nodes, truncated: truncated });
