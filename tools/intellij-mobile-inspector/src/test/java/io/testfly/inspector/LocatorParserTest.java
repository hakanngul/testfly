package io.testfly.inspector;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** The same cases as the VS Code extension's test/javaLocators.test.js, and the count cache. */
public class LocatorParserTest {

    @Test
    public void readsMobileFindLikeMobilePages() {
        String src = """
                import io.testfly.mobile.MobileFind;
                class LoginPage extends MobilePage {
                    @MobileFind("username") private MobileLocator user;
                    @MobileFind(android = "com.app:id/login", ios = "loginButton")
                    private MobileLocator login;
                    @MobileFind(value = "x", androidXpath = "//android.widget.Button[@text='Go']") MobileLocator go;
                    @MobileFind(iosPredicate = "label == \\"OK\\"", androidAccessibility = "OK") MobileLocator ok;
                    @MobileFind(android = Ids.LOGIN) MobileLocator constant;
                    // @MobileFind("commented") MobileLocator old;
                    /* @MobileFind("blocked") */
                    @MobileFind(ios = "onlyIos") MobileLocator iosOnly;
                }""";
        List<LocatorParser.Found> found = LocatorParser.find(src);
        List<String> pairs = new ArrayList<>();
        found.forEach(f -> pairs.add(f.android() + " | " + f.ios()));
        assertEquals(List.of("username | username", "com.app:id/login | loginButton", "xpath=//android.widget.Button[@text='Go'] | x",
                "accessibility=OK | predicate=label == \"OK\"", "null | onlyIos"), pairs);
        assertEquals("@MobileFind(android = \"com.app:id/login\", ios = \"loginButton\")", found.get(1).source());
        assertTrue(src.startsWith("@MobileFind", found.get(0).start()));
        assertNull(found.get(4).selectorFor("android"));
    }

    @Test
    public void readsScreenCallsAsSelectorStrings() {
        String src = """
                import io.testfly.mobile.MobileRole;
                class T extends BaseMobileTest {
                    void t() {
                        screen().getByTestId("login").tap();
                        screen().getByRole(MobileRole.BUTTON, "Go").tap();
                        screen().getByText("Hello" + " there").tap();
                        screen().getByText("ell", false).tap();
                        getByLabel("Menu");
                        screen().getByText(name).tap();
                    }
                }""";
        List<String> selectors = LocatorParser.find(src).stream().map(LocatorParser.Found::android).toList();
        assertEquals(List.of("testid=login", "role=button;name=Go", "text=Hello there", "label=Menu"), selectors);
    }

    @Test
    public void webLocatorsAreNotMobile() {
        String src = """
                import io.testfly.locator.Role;
                class Login extends BasePage {
                    private final Locator email = getByTestId("email");
                    private final Locator go = getByRole(Role.BUTTON, "Go");
                }""";
        assertTrue(LocatorParser.find(src).isEmpty());
    }

    @Test
    public void countsAreRequestedOnceAndDroppedWithTheScreen() {
        List<String> asked = new ArrayList<>();
        long[] lastId = {0};
        int[] changes = {0};
        LocatorCounts.onChange(() -> changes[0]++);
        LocatorCounts.Target target = new LocatorCounts.Target() {
            public String label() { return "Pixel 8"; }
            public String platform() { return "android"; }
            public void resolve(long id, List<String> selectors) { lastId[0] = id; asked.addAll(selectors); }
            public void reveal(String selector) { }
        };
        LocatorCounts.screenRead(target);
        assertTrue(LocatorCounts.counts(List.of("a", "b", "a")).isEmpty());
        assertTrue("pending selectors are not asked again", LocatorCounts.counts(List.of("a")).isEmpty());
        assertEquals(List.of("a", "b"), asked);
        LocatorCounts.resolved(lastId[0], List.of(new LocatorCounts.Count(1, ""), new LocatorCounts.Count(0, "")));
        Map<String, LocatorCounts.Count> counts = LocatorCounts.counts(List.of("a", "b"));
        assertEquals(1, counts.get("a").count());
        assertEquals("✓ 1 match on Pixel 8", LocatorCounts.label(counts.get("a"), "android", "Pixel 8", true));
        assertEquals("✗ no match on Pixel 8", LocatorCounts.label(counts.get("b"), "android", "Pixel 8", true));
        assertEquals("⚠ 3 matches on Pixel 8", LocatorCounts.label(new LocatorCounts.Count(3, ""), "android", "Pixel 8", true));
        assertEquals("⊘ no iOS selector", LocatorCounts.label(null, "ios", "iPhone", false));
        assertEquals("✗ unknown role 'knob'", LocatorCounts.label(new LocatorCounts.Count(-1, "unknown role 'knob'"), "android", "x", true));

        long stale = lastId[0];
        LocatorCounts.screenRead(target); // a new screen: counts are asked again, a late answer is ignored
        LocatorCounts.resolved(stale, List.of(new LocatorCounts.Count(9, "")));
        assertTrue(LocatorCounts.counts(List.of("a")).isEmpty());
        LocatorCounts.closed(target);
        assertNull(LocatorCounts.target());
        assertTrue(changes[0] >= 4);
    }
}
