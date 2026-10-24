package io.testfly.unit.mobile;

import io.testfly.config.TestFlyConfig;
import io.testfly.mobile.Mobile;
import io.testfly.mobile.MobileBounds;
import io.testfly.mobile.MobileDevice;
import io.testfly.mobile.MobileException;
import io.testfly.mobile.MobileFind;
import io.testfly.mobile.MobileLocator;
import io.testfly.mobile.MobileLocatorException;
import io.testfly.mobile.MobileNode;
import io.testfly.mobile.MobilePages;
import io.testfly.mobile.MobileRole;
import io.testfly.mobile.MobileTimeoutException;
import io.testfly.mobile.internal.ClassChainQuery;
import io.testfly.mobile.internal.MobileSessions;
import io.testfly.mobile.internal.NsPredicate;
import io.testfly.mobile.internal.SelectorParser;
import io.testfly.mobile.internal.XPathQuery;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static io.testfly.mobile.MobileAssertions.expect;
import static org.testng.Assert.*;

/** XPath, iOS class chain and NSPredicate selectors, typed {@code @MobileFind} attributes and static factories. */
public class MobileSelectorsTest {

    // Window > Table > Cell(Orders, StaticText) / Cell(Settings, StaticText) / Button(Edit, disabled)
    private static MobileNode ios(String type, String name, String label, boolean enabled, List<MobileNode> children) {
        return new MobileNode(type, null, label, name, null, name, null, new MobileBounds(0, 0, 100, 40),
                true, enabled, false, false, false, children, Map.of());
    }

    private static final MobileNode ORDERS_TEXT = ios("StaticText", null, "Orders", true, List.of());
    private static final MobileNode ORDERS = ios("Cell", "orders", "Orders", true, List.of(ORDERS_TEXT));
    private static final MobileNode SETTINGS_TEXT = ios("StaticText", null, "Settings", true, List.of());
    private static final MobileNode SETTINGS = ios("Cell", "settings", "Settings", true, List.of(SETTINGS_TEXT));
    private static final MobileNode EDIT = ios("Button", "edit", "Edit", false, List.of());
    private static final MobileNode TABLE = ios("Table", "list", null, true, List.of(ORDERS, SETTINGS, EDIT));
    private static final List<MobileNode> TREE = List.of(ios("Window", null, null, true, List.of(TABLE)));

    @Test
    public void nsPredicateSubset() {
        assertTrue(matches("label == 'Orders'", ORDERS));
        assertFalse(matches("label == 'orders'", ORDERS));
        assertTrue(matches("label ==[c] 'orders'", ORDERS));
        assertTrue(matches("name BEGINSWITH 'ord' AND type == 'XCUIElementTypeCell'", ORDERS));
        assertTrue(matches("type == 'XCUIElementTypeButton' AND NOT enabled == true", EDIT));
        assertTrue(matches("(label CONTAINS 'zzz' OR label ENDSWITH 'ers')", ORDERS));
        assertTrue(matches("label LIKE 'Ord*'", ORDERS));
        assertTrue(matches("label MATCHES 'S.*s'", SETTINGS));
        assertTrue(matches("label != 'Orders'", SETTINGS));
        assertEquals(SelectorParser.parse("predicate=label == 'Orders'").evaluate(TREE), List.of(ORDERS, ORDERS_TEXT));

        for (String bad : new String[] {"label ==", "label == 'x' AND", "(label == 'x'", "label ~ 'x'"}) {
            expectThrows(MobileLocatorException.class, () -> NsPredicate.parse(bad));
        }
    }

    private static boolean matches(String predicate, MobileNode node) {
        Predicate<MobileNode> p = NsPredicate.parse(predicate);
        return p.test(node);
    }

    @Test
    public void iosClassChainSubset() {
        assertEquals(new ClassChainQuery("**/XCUIElementTypeCell").evaluate(TREE), List.of(ORDERS, SETTINGS));
        assertEquals(new ClassChainQuery("**/Cell[2]").evaluate(TREE), List.of(SETTINGS));
        assertEquals(new ClassChainQuery("**/Cell[-1]").evaluate(TREE), List.of(SETTINGS));
        assertEquals(new ClassChainQuery("**/Cell[`label == 'Orders'`]/StaticText").evaluate(TREE), List.of(ORDERS_TEXT));
        assertEquals(new ClassChainQuery("Window/Table/*[3]").evaluate(TREE), List.of(EDIT));
        assertTrue(new ClassChainQuery("Table").evaluate(TREE).isEmpty(), "a path without ** starts at the roots");
        assertEquals(new ClassChainQuery("**/Cell[1]").describe(), "iosClassChain(\"**/Cell[1]\")");
        for (String bad : new String[] {"", "**/Cell[", "**/Cell[`label == `]", "**/Cell[0]"}) {
            expectThrows(MobileLocatorException.class, () -> new ClassChainQuery(bad));
        }
    }

    @Test
    public void xpathOnBothPlatformsTrees() {
        assertEquals(new XPathQuery("//XCUIElementTypeCell[@name='settings']").evaluate(TREE), List.of(SETTINGS));
        assertEquals(new XPathQuery("//XCUIElementTypeCell[1]/XCUIElementTypeStaticText").evaluate(TREE), List.of(ORDERS_TEXT));
        assertEquals(new XPathQuery("//*[@enabled='false']").evaluate(TREE), List.of(EDIT));
        assertEquals(new XPathQuery("(//XCUIElementTypeStaticText)[last()]").evaluate(TREE), List.of(SETTINGS_TEXT));

        MobileNode text = new MobileNode("android.widget.TextView", "Hi", "greeting desc", null, null, "app:id/hi", null,
                MobileBounds.EMPTY, true, true, false, false, false, List.of(), Map.of());
        List<MobileNode> android = List.of(text);
        assertEquals(new XPathQuery("//android.widget.TextView[@resource-id='app:id/hi']").evaluate(android), List.of(text));
        assertEquals(new XPathQuery("//*[@content-desc='greeting desc' and @text='Hi']").evaluate(android), List.of(text));
        MobileLocatorException e = expectThrows(MobileLocatorException.class, () -> new XPathQuery("count(//*)").evaluate(android));
        assertTrue(e.getMessage().contains("must select elements"), e.getMessage());
        expectThrows(MobileLocatorException.class, () -> new XPathQuery("//*[@text="));
    }

    @Test
    public void selectorStringPrefixes() {
        assertEquals(SelectorParser.parse("id=com.x:id/btn").describe(), "getByTestId(\"com.x:id/btn\")");
        assertEquals(SelectorParser.parse("accessibility=edit").evaluate(TREE), List.of(EDIT));
        assertEquals(SelectorParser.parse("xpath=//XCUIElementTypeButton").describe(), "xpath(\"//XCUIElementTypeButton\")");
        assertEquals(SelectorParser.parse("chain=**/Cell").evaluate(TREE), List.of(ORDERS, SETTINGS));
        assertEquals(SelectorParser.parse("predicate=name == 'edit'").describe(), "iosPredicate(\"name == 'edit'\")");
    }

    static class TypedPage {
        @MobileFind(androidId = "dev.testfly.demo:id/username", iosXpath = "//*[@placeholder='Username']")
        MobileLocator username;

        @MobileFind(androidXpath = "//android.widget.Button[@text='Sign in']", iosPredicate = "text == 'Sign in'")
        MobileLocator signIn;

        @MobileFind(androidAccessibility = "Password", iosClassChain = "**/EditText[2]")
        MobileLocator password;
    }

    static class ConflictPage {
        @MobileFind(android = "username", androidId = "username")
        MobileLocator field;
    }

    @Test
    public void typedMobileFindAttributesPerPlatform() {
        for (String platform : new String[] {"ios", "android"}) {
            try (FakeMobilecliServer server = new FakeMobilecliServer(); MobileDevice device = connect(server, platform)) {
                TypedPage page = MobilePages.create(TypedPage.class, device.screen());
                page.username.fill("ada");
                page.password.fill("pw");
                expect(page.password).toHaveText("pw");
                assertEquals(page.username.getAttribute("resource-id"), "dev.testfly.demo:id/username", platform);
                page.signIn.tap();
                expect(device.screen().getByText("Hello, ada")).toBeVisible();
            }
        }
        try (FakeMobilecliServer server = new FakeMobilecliServer(); MobileDevice device = connect(server, "android")) {
            MobileException e = expectThrows(MobileException.class, () -> MobilePages.create(ConflictPage.class, device.screen()));
            assertTrue(e.getMessage().contains("more than one android selector"), e.getMessage());
        }
    }

    // Static locators bind to the device of the thread that uses them.
    private static final MobileLocator USERNAME = MobileLocator.id("username");
    private static final MobileLocator SIGN_IN = MobileLocator.byRole(MobileRole.BUTTON, "Sign in");
    private static final MobileLocator ROWS = MobileLocator.xpath("//android.widget.LinearLayout");

    @Test
    public void staticFactoriesBindLazilyAndParityReads() {
        expectThrows(MobileException.class, USERNAME::count);
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            TestFlyConfig.Mobile m = config(server, "android");
            MobileSessions.setOpener(() -> Mobile.connect(m));
            try {
                MobileSessions.open();
                assertTrue(USERNAME.isPresent());
                assertTrue(SIGN_IN.isDisabled());
                assertFalse(SIGN_IN.isEnabled());
                assertFalse(USERNAME.isFocused());
                USERNAME.fill("ada");
                assertTrue(USERNAME.isFocused());
                assertTrue(SIGN_IN.isEnabled());
                assertEquals(SIGN_IN.getAttribute("class"), "android.widget.Button");
                assertEquals(SIGN_IN.getAttribute("enabled"), "true");
                assertNull(SIGN_IN.getAttribute("content-desc"));
                assertTrue(MobileLocator.byText("Nowhere").isHidden());
                SIGN_IN.tap();

                assertEquals(ROWS.allTexts(), List.of("Orders", "Settings"));
                MobileLocator help = MobileLocator.byText("Help");
                help.scrollIntoView();
                expect(help).toBeVisible();
                MobileTimeoutException e = expectThrows(MobileTimeoutException.class, () -> MobileLocator.byText("Nowhere").scrollIntoView());
                assertTrue(e.getMessage().contains("to the end"), e.getMessage());
            } finally {
                MobileSessions.close();
                MobileSessions.setOpener(null);
            }
        }
    }

    private static TestFlyConfig.Mobile config(FakeMobilecliServer server, String platform) {
        String id = platform + "-" + Long.toHexString(System.nanoTime());
        FakeApp app = new FakeApp(id);
        app.platform = platform;
        app.install(server);
        TestFlyConfig.Mobile m = new TestFlyConfig.Mobile();
        m.setServerUrl(server.url());
        m.setPlatform(platform);
        m.setDeviceId(id);
        m.getApp().setBundleId(FakeApp.BUNDLE);
        m.getTimeouts().setActionMs(1500);
        m.getTimeouts().setPollMs(20);
        m.getTimeouts().setRpcMs(2000);
        m.getTimeouts().setReserveMs(2000);
        return m;
    }

    private static MobileDevice connect(FakeMobilecliServer server, String platform) {
        return Mobile.connect(config(server, platform));
    }

    private static <E extends Throwable> E expectThrows(Class<E> type, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + t, t);
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }
}
