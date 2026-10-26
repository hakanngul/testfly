// Turns Browser Inspector steps into a TestFly BasePage. Shared by the webview and the Node tests.
(function (root, factory) {
    if (typeof module === 'object' && module.exports) module.exports = factory();
    else root.TestFlyRecorder = factory();
})(typeof self !== 'undefined' ? self : this, function () {
    'use strict';

    /** A Java field name from the node and its locator: signIn, emailField, … */
    function fieldName(n, best) {
        const arg = best ? (best.code.match(/"((?:[^"\\]|\\.)*)"\)$/) || [])[1] : '';
        const base = n.testId || (best && best.kind !== 'CSS' && best.kind !== 'SHADOW' ? arg : '') || n.name || n.id || n.placeholder || n.text || n.tag;
        const words = String(base).replace(/\\./g, '').normalize('NFKD').replace(/[^\p{L}\p{N}]+/gu, ' ').trim().split(/\s+/).slice(0, 4);
        let name = words.map((w, i) => i ? w.charAt(0).toUpperCase() + w.slice(1).toLowerCase() : w.toLowerCase()).join('') || n.tag;
        if (/^\d/.test(name)) name = n.tag + name;
        return name;
    }

    /** A locator as a page-object member: a Locator field, or a method for a ShadowDom.pierce element. */
    function member(name, loc) {
        return loc.kind === 'SHADOW'
            ? `private WebElement ${name}() {\n    return ${loc.code};\n}`
            : `private final Locator ${name} = ${loc.code};`;
    }

    const str = v => '"' + String(v).replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n') + '"';

    /**
     * Steps: {action: open, url}, {action: back|forward|refresh},
     * {action: click|fill, loc: {kind, code}, name, text?, frames?: [css]}.
     */
    function codegen(steps) {
        const names = new Map(); // locator key -> field name
        const members = [];
        const lines = [];
        const field = st => {
            const key = (st.frames || []).join(' >> ') + ' :: ' + st.loc.code;
            if (!names.has(key)) {
                let name = st.name || 'element';
                for (let i = 2; [...names.values()].includes(name); i++) name = (st.name || 'element') + i;
                names.set(key, name);
                members.push(member(name, st.loc));
            }
            return names.get(key);
        };
        for (const st of steps) {
            let line;
            switch (st.action) {
                case 'open': line = `open(${str(st.url)})`; break;
                case 'back': case 'forward': case 'refresh': line = st.action + '()'; break;
                case 'click':
                case 'fill': {
                    const shadow = st.loc.kind === 'SHADOW';
                    const target = field(st) + (shadow ? '()' : '');
                    line = st.action === 'click' ? `${target}.click()` : shadow ? `${target}.sendKeys(${str(st.text)})` : `${target}.fill(${str(st.text)})`;
                    // a step in a frame runs after withinFrame switches into it, outermost frame first
                    for (const css of [...(st.frames || [])].reverse()) line = `withinFrame(By.cssSelector(${str(css)}), () -> ${line})`;
                    break;
                }
                default: continue;
            }
            lines.push(line + ';');
        }
        const indent = (text, pad) => text.split('\n').map(l => pad + l).join('\n');
        return 'public class RecordedPage extends BasePage {\n\n'
            + members.map(m => indent(m, '    ') + '\n\n').join('')
            + '    public void recordedFlow() {\n' + lines.map(l => '        ' + l + '\n').join('') + '    }\n}\n';
    }

    return { fieldName, member, codegen };
});
