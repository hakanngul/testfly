const test = require('node:test');
const assert = require('node:assert');
const R = require('../media/recorder.js');

test('records a BasePage with one member per locator, frames and shadow roots', () => {
    const email = { kind: 'TEST ID', code: 'getByTestId("email")' };
    const go = { kind: 'ROLE', code: 'getByRole(Role.BUTTON, "Sign in")' };
    const pay = { kind: 'SHADOW', code: 'ShadowDom.pierce("#pw", "#pay")' };
    const card = { kind: 'TEST ID', code: 'getByTestId("card")' };
    const code = R.codegen([
        { action: 'open', url: 'https://shop.test/login' },
        { action: 'fill', loc: email, name: 'email', text: 'a"b@c.d' },
        { action: 'click', loc: go, name: 'signIn' },
        { action: 'click', loc: go, name: 'signIn' },
        { action: 'click', loc: pay, name: 'pay' },
        { action: 'fill', loc: pay, name: 'pay', text: 'x' },
        { action: 'fill', loc: card, name: 'card', text: '4111', frames: ['#outer', 'iframe.card'] },
        { action: 'click', loc: { kind: 'CSS', code: 'find("button")' }, name: 'signIn' },
        { action: 'back' },
    ]);
    assert.match(code, /^public class RecordedPage extends BasePage \{/);
    assert.match(code, / {4}private final Locator email = getByTestId\("email"\);/);
    assert.match(code, / {4}private WebElement pay\(\) \{\n {8}return ShadowDom\.pierce\("#pw", "#pay"\);\n {4}\}/);
    assert.match(code, /private final Locator signIn2 = find\("button"\);/, 'a clashing name gets a suffix');
    assert.strictEqual(code.match(/signIn = /g).length, 1, 'one field per locator');
    const body = code.slice(code.indexOf('recordedFlow'));
    assert.deepStrictEqual(body.split('\n').slice(1, -3).map(l => l.trim()), [
        'open("https://shop.test/login");',
        'email.fill("a\\"b@c.d");',
        'signIn.click();',
        'signIn.click();',
        'pay().click();',
        'pay().sendKeys("x");',
        'withinFrame(By.cssSelector("#outer"), () -> withinFrame(By.cssSelector("iframe.card"), () -> card.fill("4111")));',
        'signIn2.click();',
        'back();',
    ]);
});

test('field names come from the test id, the locator argument or the text', () => {
    assert.strictEqual(R.fieldName({ tag: 'button', testId: 'sign-in' }, null), 'signIn');
    assert.strictEqual(R.fieldName({ tag: 'button', name: 'x' }, { kind: 'ROLE', code: 'getByRole(Role.BUTTON, "Giriş yap")' }), 'girisYap');
    assert.strictEqual(R.fieldName({ tag: 'div', text: '2 items' }, { kind: 'CSS', code: 'find("div")' }), 'div2Items');
});
