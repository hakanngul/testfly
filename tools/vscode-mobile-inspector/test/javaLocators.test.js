const test = require('node:test');
const assert = require('node:assert');
const { findLocators, stringValue } = require('../out/javaLocators');

test('reads @MobileFind like MobilePages.selector', () => {
    const src = `import io.testfly.mobile.MobileFind;
class LoginPage extends MobilePage {
    @MobileFind("username") private MobileLocator user;
    @MobileFind(android = "com.app:id/login", ios = "loginButton")
    private MobileLocator login;
    @MobileFind(value = "x", androidXpath = "//android.widget.Button[@text='Go']") MobileLocator go;
    @MobileFind(iosPredicate = "label == \\"OK\\"", androidAccessibility = "OK") MobileLocator ok;
    @MobileFind(android = Ids.LOGIN) MobileLocator constant;
    // @MobileFind("commented") MobileLocator old;
    /* @MobileFind("blocked") */
}`;
    const found = findLocators(src);
    assert.deepStrictEqual(found.map(f => f.mobile), [
        { android: 'username', ios: 'username' },
        { android: 'com.app:id/login', ios: 'loginButton' },
        { android: "xpath=//android.widget.Button[@text='Go']", ios: 'x' },
        { android: 'accessibility=OK', ios: 'predicate=label == "OK"' },
    ]);
    assert.strictEqual(found[1].source, '@MobileFind(android = "com.app:id/login", ios = "loginButton")');
    assert.strictEqual(src.slice(found[0].offset, found[0].offset + 11), '@MobileFind');
});

test('reads web locators with literal arguments', () => {
    const src = `import io.testfly.test.BasePage;
import io.testfly.locator.Role;
public class Login extends BasePage {
    private final Locator email = getByTestId("email");
    private final Locator signIn = getByRole(Role.BUTTON, "Sign " + "in");
    private final Locator nav = getByRole(Role.NAVIGATION);
    private final Locator pw = getByLabel("Password");
    private final Locator css = find("#login > button");
    private final Locator byId = findById("em");
    private final Locator dyn = getByText(name);
    private final Locator text = getByText("Forgot \\"password\\"?");
    void x() { list.stream().find("nope"); this.find(".ok"); }
    default Locator find(String css) { return null; }
}`;
    const found = findLocators(src);
    assert.deepStrictEqual(found.map(f => f.web), [
        { kind: 'testId', args: ['email'] },
        { kind: 'role', args: ['BUTTON', 'Sign in'] },
        { kind: 'role', args: ['NAVIGATION'] },
        { kind: 'label', args: ['Password'] },
        { kind: 'css', args: ['#login > button'] },
        { kind: 'id', args: ['em'] },
        { kind: 'text', args: ['Forgot "password"?'] },
        { kind: 'css', args: ['.ok'] },
    ]);
    assert.strictEqual(found[1].source, 'getByRole(Role.BUTTON, "Sign " + "in")');
});

test('reads MobileScreen calls as selector strings', () => {
    const src = `import io.testfly.mobile.MobileRole;
class T extends BaseMobileTest {
    void t() {
        screen().getByTestId("login").tap();
        screen().getByRole(MobileRole.BUTTON, "Go").tap();
        screen().getByText("Hello").tap();
        screen().getByText("ell", false).tap();
        getByLabel("Menu");
    }
}`;
    assert.deepStrictEqual(findLocators(src).map(f => f.mobile && f.mobile.android), ['testid=login', 'role=button;name=Go', 'text=Hello', 'label=Menu']);
});

test('stringValue accepts literals and concatenations only', () => {
    assert.strictEqual(stringValue('"a\\tb" + "\\u00e7"'), 'a\tbç');
    assert.strictEqual(stringValue('"a" + b'), undefined);
    assert.strictEqual(stringValue('NAME'), undefined);
});
