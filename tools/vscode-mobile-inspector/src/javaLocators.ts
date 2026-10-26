/**
 * Finds TestFly locators in Java source so the editor can show how many elements each one matches on the screen or
 * page the inspector has open. Only literal arguments are read: a locator built from a constant or a variable is
 * skipped, since its value is not known without compiling.
 */

export type WebKind = 'testId' | 'role' | 'label' | 'placeholder' | 'text' | 'altText' | 'title' | 'id' | 'name' | 'className' | 'xpath' | 'css';

export interface MobileSelectors {
    android?: string;
    ios?: string;
}

export interface JavaLocator {
    /** Offset of the annotation or call in the source. */
    offset: number;
    /** What the code says, e.g. `getByTestId("email")` or `@MobileFind(android = "login")`. */
    source: string;
    /** Native mobile: a selector per platform, as MobilePages.selector picks it. */
    mobile?: MobileSelectors;
    /** Web: the Locator factory and its literal arguments (role: [ROLE] or [ROLE, name]). */
    web?: { kind: WebKind; args: string[] };
}

const WEB_CALLS: Record<string, WebKind> = {
    getByTestId: 'testId', getByRole: 'role', getByLabel: 'label', getByPlaceholder: 'placeholder', getByText: 'text',
    getByAltText: 'altText', getByTitle: 'title', findById: 'id', findByName: 'name', findByClassName: 'className',
    findByXpath: 'xpath', find: 'css',
};

// MobileScreen factories as MobileFind selector strings (exact matching, like the one-argument overloads)
const MOBILE_CALLS: Record<string, string> = {
    getByTestId: 'testid=', getByText: 'text=', getByLabel: 'label=', getByPlaceholder: 'placeholder=', getByType: 'type=',
};

// MobilePages.selector: typed attributes become prefixed selectors
const TYPED: Record<'android' | 'ios', [string, string][]> = {
    android: [['androidAccessibility', 'accessibility='], ['androidId', 'id='], ['androidXpath', 'xpath='], ['androidClassName', 'type=']],
    ios: [['iosAccessibility', 'accessibility='], ['iosId', 'id='], ['iosXpath', 'xpath='], ['iosClassChain', 'chain='],
        ['iosPredicate', 'predicate='], ['iosClassName', 'type=']],
};

export function findLocators(source: string): JavaLocator[] {
    const code = blankComments(source);
    const out: JavaLocator[] = [];
    const mobileFile = /\bimport\s+io\.testfly\.mobile\./.test(code) && !/\bimport\s+io\.testfly\.(locator|test)\./.test(code);

    for (const m of code.matchAll(/@MobileFind\b\s*(\()?/g)) {
        if (!m[1]) {
            continue;
        }
        const args = parseArgs(code, m.index! + m[0].length);
        if (!args) {
            continue;
        }
        const attrs: Record<string, string> = {};
        let literal = true;
        for (const a of args.list) {
            const named = /^([A-Za-z]\w*)\s*=\s*([\s\S]*)$/.exec(a);
            const value = stringValue(named ? named[2] : a);
            if (value === undefined) {
                literal = false;
                break;
            }
            attrs[named ? named[1] : 'value'] = value;
        }
        if (literal) {
            out.push({ offset: m.index!, source: code.slice(m.index!, args.end + 1).replace(/\s+/g, ' '), mobile: mobileFindSelectors(attrs) });
        }
    }

    const names = [...new Set([...Object.keys(WEB_CALLS), ...Object.keys(MOBILE_CALLS)])].join('|');
    for (const m of code.matchAll(new RegExp(`(\\.\\s*)?\\b(${names})\\s*\\(`, 'g'))) {
        const name = m[2];
        const start = m.index! + (m[1] ? m[1].length : 0);
        const receiver = code.slice(Math.max(0, m.index! - 40), m.index! + (m[1] ? 1 : 0));
        // find(...) is a common name; only TestFly's own (unqualified or this.) is read
        if (name === 'find' && m[1] && !/\bthis\s*\.$/.test(receiver)) {
            continue;
        }
        if (/\b(?:public|private|protected|static|void|default)\s+[\w<>, ?]*\s*$/.test(code.slice(Math.max(0, start - 80), start))) {
            continue; // a method declaration, not a call
        }
        const args = parseArgs(code, m.index! + m[0].length);
        if (!args || !args.list.length) {
            continue;
        }
        const text = code.slice(start, args.end + 1).replace(/\s+/g, ' ');
        const mobile = /MobileRole\./.test(args.list[0]) || /\bscreen\(\)\s*\.$/.test(receiver) || (mobileFile && !/(^|[^\w.])Role\./.test(args.list[0]));
        if (mobile) {
            const selector = mobileCall(name, args.list);
            if (selector) {
                out.push({ offset: start, source: text, mobile: { android: selector, ios: selector } });
            }
        } else if (WEB_CALLS[name]) {
            const web = webCall(WEB_CALLS[name], args.list);
            if (web) {
                out.push({ offset: start, source: text, web });
            }
        }
    }
    return out.sort((a, b) => a.offset - b.offset);
}

function mobileFindSelectors(attrs: Record<string, string>): MobileSelectors {
    const pick = (platform: 'android' | 'ios') => {
        const typed = TYPED[platform].filter(([k]) => attrs[k]).map(([k, prefix]) => prefix + attrs[k]);
        return typed[typed.length - 1] || attrs[platform] || attrs.value || undefined;
    };
    return { android: pick('android'), ios: pick('ios') };
}

function mobileCall(name: string, args: string[]): string | undefined {
    if (name === 'getByRole') {
        const role = /^MobileRole\.([A-Z_]+)$/.exec(args[0].trim());
        if (!role || args.length > 2) {
            return undefined;
        }
        const nameArg = args[1] !== undefined ? stringValue(args[1]) : '';
        return nameArg === undefined ? undefined : `role=${role[1].toLowerCase()}${nameArg ? ';name=' + nameArg : ''}`;
    }
    const prefix = MOBILE_CALLS[name];
    const value = stringValue(args[0]);
    // the (text, false) overloads match substrings, which has no selector-string form
    if (!prefix || value === undefined || (args.length === 2 && args[1].trim() !== 'true') || args.length > 2) {
        return undefined;
    }
    return prefix + value;
}

function webCall(kind: WebKind, args: string[]): JavaLocator['web'] {
    if (kind === 'role') {
        const role = /^Role\.([A-Z_]+)$/.exec(args[0].trim());
        if (!role || args.length > 2) {
            return undefined;
        }
        if (args.length === 1) {
            return { kind, args: [role[1]] };
        }
        const name = stringValue(args[1]);
        return name === undefined ? undefined : { kind, args: [role[1], name] };
    }
    const value = args.length === 1 ? stringValue(args[0]) : undefined;
    return value === undefined ? undefined : { kind, args: [value] };
}

/** Arguments of the call whose '(' is just before `from`, split at top-level commas; undefined when unbalanced. */
function parseArgs(code: string, from: number): { list: string[]; end: number } | undefined {
    const list: string[] = [];
    let depth = 0;
    let start = from;
    for (let i = from; i < code.length && i < from + 4000; i++) {
        const c = code[i];
        if (c === '"' || c === "'") {
            i = skipLiteral(code, i);
            if (i < 0) {
                return undefined;
            }
        } else if (c === '(' || c === '{' || c === '[') {
            depth++;
        } else if (c === ')' || c === '}' || c === ']') {
            if (depth === 0) {
                const last = code.slice(start, i).trim();
                if (last || list.length) {
                    list.push(last);
                }
                return { list, end: i };
            }
            depth--;
        } else if (c === ',' && depth === 0) {
            list.push(code.slice(start, i).trim());
            start = i + 1;
        }
    }
    return undefined;
}

/** Index of the closing quote of the literal opening at `i`, or -1. */
function skipLiteral(code: string, i: number): number {
    const q = code[i];
    if (q === '"' && code.startsWith('"""', i)) {
        const end = code.indexOf('"""', i + 3);
        return end < 0 ? -1 : end + 2;
    }
    for (let j = i + 1; j < code.length; j++) {
        if (code[j] === '\\') {
            j++;
        } else if (code[j] === q) {
            return j;
        } else if (code[j] === '\n') {
            return -1;
        }
    }
    return -1;
}

/** The value of a string literal or a `+` concatenation of literals; undefined for anything else. */
export function stringValue(expr: string): string | undefined {
    const parts: string[] = [];
    let rest = expr.trim();
    if (!rest) {
        return undefined;
    }
    while (rest) {
        const m = /^"((?:[^"\\\n]|\\.)*)"\s*(\+\s*)?/.exec(rest);
        if (!m) {
            return undefined;
        }
        parts.push(unescape(m[1]));
        rest = rest.slice(m[0].length);
        if (!m[2] && rest) {
            return undefined;
        }
    }
    return parts.join('');
}

function unescape(s: string): string {
    return s.replace(/\\(u+[0-9a-fA-F]{4}|[0-7]{1,3}|.)/g, (_, e: string) => {
        if (e[0] === 'u') {
            return String.fromCharCode(parseInt(e.replace(/^u+/, ''), 16));
        }
        if (/^[0-7]/.test(e)) {
            return String.fromCharCode(parseInt(e, 8));
        }
        return ({ n: '\n', t: '\t', r: '\r', b: '\b', f: '\f', s: ' ' } as Record<string, string>)[e] ?? e;
    });
}

/** Comments replaced by spaces, so offsets stay the same and commented-out locators are ignored. */
function blankComments(code: string): string {
    let out = '';
    for (let i = 0; i < code.length; i++) {
        const c = code[i];
        if (c === '"' || c === "'") {
            const end = skipLiteral(code, i);
            const stop = end < 0 ? i : end;
            out += code.slice(i, stop + 1);
            i = stop;
        } else if (c === '/' && code[i + 1] === '/') {
            const end = code.indexOf('\n', i);
            const stop = end < 0 ? code.length : end;
            out += ' '.repeat(stop - i);
            i = stop - 1;
        } else if (c === '/' && code[i + 1] === '*') {
            const end = code.indexOf('*/', i + 2);
            const stop = end < 0 ? code.length : end + 2;
            out += code.slice(i, stop).replace(/[^\n]/g, ' ');
            i = stop - 1;
        } else {
            out += c;
        }
    }
    return out;
}
