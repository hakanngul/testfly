const test = require('node:test');
const assert = require('node:assert');
const S = require('../media/selectors.js');

const field = (id, extra) => ({ type: 'android.widget.EditText', identifier: id, rect: { x: 0, y: 0, width: 100, height: 40 }, ...extra });

const android = [{
    type: 'android.widget.FrameLayout', ref: '@e1', rect: { x: 0, y: 0, width: 1080, height: 2400 },
    children: [
        field('com.app:id/etUserName', { ref: '@e2', placeholder: 'Username' }),
        { type: 'android.widget.Button', ref: '@e3', identifier: 'com.app:id/btnLogin', text: 'Sign in', rect: { x: 40, y: 500, width: 1000, height: 120 } },
        { type: 'android.widget.TextView', ref: '@e4', identifier: 'com.app:id/tvTitle', text: 'Summary', rect: { x: 0, y: 700, width: 500, height: 60 } },
        { type: 'android.widget.TextView', ref: '@e5', identifier: 'com.app:id/tvTitle', text: 'Bets', rect: { x: 0, y: 800, width: 500, height: 60 } },
    ],
}];

test('ids come first, full and short, with match counts', () => {
    const login = android[0].children[1];
    const s = S.suggest(login, android);
    assert.deepStrictEqual(s.slice(0, 3).map(x => [x.kind, x.selector, x.count]), [
        ['id', 'com.app:id/btnLogin', 1],
        ['id', 'btnLogin', 1],
        ['role', 'role=button;name=Sign in', 1],
    ]);
    assert.ok(s.some(x => x.selector === 'text=Sign in' && x.count === 1));
    assert.ok(s.some(x => x.kind === 'xpath' && x.count === -1), 'no XPath counter: unknown');
});

test('repeated ids are reported as not unique', () => {
    const title = android[0].children[2];
    const s = S.suggest(title, android);
    assert.strictEqual(s[0].count, 2);
    assert.ok(s.some(x => x.selector === 'text=Summary' && x.count === 1));
});

test('iOS role names, labels and placeholders', () => {
    const ios = [{ type: 'Window', children: [
        { type: 'TextField', identifier: 'LoginScreen.nameTextField', placeholder: 'Member id', rect: { x: 10, y: 10, width: 300, height: 40 } },
        { type: 'Button', label: 'Giriş Yap', name: 'Giriş Yap', rect: { x: 10, y: 60, width: 300, height: 40 } },
    ] }];
    const button = ios[0].children[1];
    const s = S.suggest(button, ios).map(x => x.selector);
    assert.deepStrictEqual(s.slice(0, 3), ['role=button;name=Giriş Yap', 'label=Giriş Yap', 'text=Giriş Yap']);
    assert.strictEqual(S.xpathName(button), 'XCUIElementTypeButton');
    assert.ok(S.suggest(ios[0].children[0], ios).some(x => x.selector === 'placeholder=Member id'));
});

test('field names and @MobileFind snippets', () => {
    assert.strictEqual(S.fieldName({ identifier: 'com.app:id/etUserName' }), 'etUserName');
    assert.strictEqual(S.fieldName({ identifier: 'LoginScreen.loginButton' }), 'loginButton');
    assert.strictEqual(S.fieldName({ type: 'Button', label: 'Giriş Yap' }), 'girisYap');
    assert.strictEqual(S.fieldName({ type: 'Button', label: '30 gün' }), 'e30Gun');
    assert.strictEqual(S.mobileFind({ identifier: 'x:id/btn' }, 'role=button;name="Go"', 'ios'),
        '@MobileFind(ios = "role=button;name=\\"Go\\"")\nprivate MobileLocator btn;');
});

test('hit test picks the smallest element under the point', () => {
    assert.strictEqual(S.hitTest(android, 50, 550).ref, '@e3');
    assert.strictEqual(S.hitTest(android, 900, 2000).ref, '@e1');
    assert.strictEqual(S.hitTest(android, 5000, 5000), null);
});
