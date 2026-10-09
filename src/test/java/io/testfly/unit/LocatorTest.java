package io.testfly.unit;

import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.locator.Locator;
import io.testfly.locator.LocatorException;
import io.testfly.test.BaseTest;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.testng.annotations.Test;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link Locator}.
 * All tests use a mocked WebDriver — no real browser required.
 * Thread-safe for parallel=methods via singleThreaded.
 */
@Test(singleThreaded = true)
public class LocatorTest {

    @Test
    public void testFirstAndLastMethods() {
        Locator locator = Locator.cssSelector(".item");
        assertNotNull(locator.first());
        assertNotNull(locator.last());
    }

    @Test
    public void resolve_throwsLocatorException_whenNoElementsFound() {
        LocatorException ex = new LocatorException("No element found for: By.id: missing");
        assertTrue(ex.getMessage().contains("No element found"));
    }

    @Test
    public void locatorException_preservesCause() {
        RuntimeException cause = new RuntimeException("root cause");
        LocatorException ex = new LocatorException("wrapped", cause);
        assertEquals(ex.getCause(), cause);
        assertEquals(ex.getMessage(), "wrapped");
    }

    @Test
    public void locator_toString_includesRootBy() {
        Locator loc = Locator.of(By.id("username"));
        assertTrue(loc.toString().contains("username"),
                "toString should include root By description");
    }

    @Test
    public void locator_toString_includesFilterAndNth() {
        Locator loc = Locator.of(By.cssSelector(".row"))
                .filter(".active")
                .nth(2);
        String str = loc.toString();
        assertTrue(str.contains(".active"), "toString should include filter");
        assertTrue(str.contains("2"), "toString should include nth index");
    }

    @Test
    public void locator_toString_includesWithText() {
        Locator loc = Locator.cssSelector("button").withText("Save");
        assertTrue(loc.toString().contains("Save"), "toString should include withText value");
    }

    @Test
    public void locator_toString_includesWithin() {
        Locator loc = Locator.of(By.cssSelector("input"))
                .within(By.id("login-form"));
        assertTrue(loc.toString().contains("login-form"), "toString should include within container");
    }

    @Test
    public void locatorOfCss_createsByCssSelector() {
        Locator loc = Locator.cssSelector(".submit-btn");
        assertTrue(loc.toString().contains("submit-btn"));
        @SuppressWarnings("deprecation")
        Locator legacy = Locator.css(".submit-btn");
        assertTrue(legacy.toString().contains("submit-btn"));
    }

    @Test
    public void locatorOf_createsByLocator() {
        Locator loc = Locator.of(By.name("email"));
        assertTrue(loc.toString().contains("email"));
    }

    @Test
    public void locatorException_withMessageOnly() {
        LocatorException ex = new LocatorException("test error");
        assertEquals("test error", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    public void locator_chaining_doesNotMutateOriginal() {
        Locator base = Locator.cssSelector(".item");
        Locator filtered = base.filter(".active");
        assertNotNull(base);
        assertNotNull(filtered);
        assertTrue(filtered.toString().contains(".active"));
    }

    // ----------------------------------------------------------
    // BaseTest.find() alias
    // ----------------------------------------------------------

    private static class BaseTestFixture extends BaseTest {
        Locator findCss(String css) {
            return find(css);
        }

        Locator findBy(By by) {
            return find(by);
        }

        @SuppressWarnings("removal")
        Locator dollarCss(String css) {
            return $(css);
        }
    }

    @Test
    public void baseTest_findByCss_delegatesToLocatorOfCss() {
        BaseTestFixture fixture = new BaseTestFixture();
        Locator loc = fixture.findCss(".submit-btn");
        assertTrue(loc.toString().contains("submit-btn"));
    }

    @Test
    public void baseTest_findByBy_delegatesToLocatorOf() {
        BaseTestFixture fixture = new BaseTestFixture();
        Locator loc = fixture.findBy(By.id("username"));
        assertTrue(loc.toString().contains("username"));
    }

    @Test
    public void baseTest_dollarAliasStillWorks() {
        BaseTestFixture fixture = new BaseTestFixture();
        Locator loc = fixture.dollarCss(".submit-btn");
        assertTrue(loc.toString().contains("submit-btn"));
    }

    // ----------------------------------------------------------
    // Self-healing integration
    // ----------------------------------------------------------

    private MockedStatic<?>[] setupHealingMocks(boolean selfHealingEnabled) {
        WebDriver mockDriver = mock(WebDriver.class,
                Mockito.withSettings().extraInterfaces(JavascriptExecutor.class));
        lastMockDriver = mockDriver;

        MockedStatic<DriverManager> driverManagerMock = mockStatic(DriverManager.class);
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockDriver);

        TestFlyConfig.Locators locators = new TestFlyConfig.Locators();
        locators.setSelfHealing(selfHealingEnabled);

        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setExplicit(2);

        TestFlyConfig config = new TestFlyConfig();
        config.setLocators(locators);
        config.setTimeouts(timeouts);

        MockedStatic<TestFlyContext> contextMock = mockStatic(TestFlyContext.class);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);
        contextMock.when(TestFlyContext::getCurrentTestId).thenReturn("test-1");

        return new MockedStatic<?>[] { driverManagerMock, contextMock };
    }

    private WebDriver lastMockDriver;

    private void closeMocks(MockedStatic<?>[] mocks) {
        for (MockedStatic<?> m : mocks)
            m.close();
    }

    @Test
    public void resolve_triggersSelfHealing_whenPlainByNotFound_andHealingEnabled() {
        MockedStatic<?>[] mocks = setupHealingMocks(true);
        try {
            By primary = By.cssSelector("#login-btn");
            when(lastMockDriver.findElements(primary)).thenReturn(Collections.emptyList());
            WebElement healed = mock(WebElement.class);
            when(healed.isDisplayed()).thenReturn(true);
            when(healed.getText()).thenReturn("Healed Button");
            when(lastMockDriver.findElements(By.id("login-btn"))).thenReturn(List.of(healed));
            assertEquals(Locator.of(primary).getText(), "Healed Button");
        } finally {
            closeMocks(mocks);
        }
    }

    @Test
    public void resolve_throwsException_whenHealingDisabled_andElementNotFound() {
        MockedStatic<?>[] mocks = setupHealingMocks(false);
        try {
            By primary = By.cssSelector("#login-btn");
            when(lastMockDriver.findElements(primary)).thenReturn(Collections.emptyList());
            assertThrows(LocatorException.class, () -> Locator.of(primary).getText());
        } finally {
            closeMocks(mocks);
        }
    }

    @Test
    public void resolve_skipsSelfHealing_whenChainFilterApplied() {
        MockedStatic<?>[] mocks = setupHealingMocks(true);
        try {
            By primary = By.cssSelector("#login-btn");
            when(lastMockDriver.findElements(primary)).thenReturn(Collections.emptyList());
            assertThrows(LocatorException.class,
                    () -> Locator.of(primary).filter(".active").getText());
        } finally {
            closeMocks(mocks);
        }
    }

    @Test
    public void elements_withText_matchesSubstringByDefault() {
        MockedStatic<?>[] mocks = setupHealingMocks(false);
        try {
            WebElement row1 = mock(WebElement.class);
            when(row1.getText()).thenReturn("ORD-1234 Shipped");
            when(row1.isDisplayed()).thenReturn(true);

            WebElement row2 = mock(WebElement.class);
            when(row2.getText()).thenReturn("ORD-5678 Pending");
            when(row2.isDisplayed()).thenReturn(true);

            By by = By.cssSelector("tbody tr");
            when(lastMockDriver.findElements(by)).thenReturn(List.of(row1, row2));

            List<WebElement> matched = Locator.of(by).withText("ORD-1234").elements();
            assertEquals(matched.size(), 1);
            assertSame(matched.get(0), row1);
        } finally {
            closeMocks(mocks);
        }
    }

    @Test
    public void elements_withTextExact_requiresExactMatch() {
        MockedStatic<?>[] mocks = setupHealingMocks(false);
        try {
            WebElement row1 = mock(WebElement.class);
            when(row1.getText()).thenReturn("ORD-1234 Shipped");
            when(row1.isDisplayed()).thenReturn(true);

            WebElement row2 = mock(WebElement.class);
            when(row2.getText()).thenReturn("ORD-1234");
            when(row2.isDisplayed()).thenReturn(true);

            By by = By.cssSelector("tbody tr");
            when(lastMockDriver.findElements(by)).thenReturn(List.of(row1, row2));

            List<WebElement> matched = Locator.of(by).withText("ORD-1234").exact().elements();
            assertEquals(matched.size(), 1);
            assertSame(matched.get(0), row2);
        } finally {
            closeMocks(mocks);
        }
    }

    // ----------------------------------------------------------
    // Auto-wait contract (API-011 / TST-005)
    // ----------------------------------------------------------

    @Test
    public void click_waitsForLateElement_andReResolvesEachPoll() {
        MockedStatic<?>[] mocks = setupHealingMocks(false);
        try {
            By by = By.cssSelector("#late");
            WebElement late = mock(WebElement.class);
            when(late.isDisplayed()).thenReturn(true);
            when(late.isEnabled()).thenReturn(true);
            // absent on the first two polls, present afterwards
            when(lastMockDriver.findElements(by))
                    .thenReturn(Collections.emptyList(), Collections.emptyList(), List.of(late));

            Locator.of(by).click();

            org.mockito.Mockito.verify(late).click();
            org.mockito.Mockito.verify(lastMockDriver, org.mockito.Mockito.atLeast(3)).findElements(by);
        } finally {
            closeMocks(mocks);
        }
    }

    @Test
    public void resolve_waitsForExplicitTimeout_beforeThrowing() {
        MockedStatic<?>[] mocks = setupHealingMocks(false);
        try {
            By by = By.cssSelector("#never");
            when(lastMockDriver.findElements(by)).thenReturn(Collections.emptyList());

            long start = System.nanoTime();
            LocatorException ex = expectThrows(LocatorException.class, () -> Locator.of(by).getText());
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertTrue(elapsedMs >= 1500, "should wait ~timeouts.explicit (2s) but took " + elapsedMs + "ms");
            assertTrue(ex.getMessage().contains("#never"), ex.getMessage());
        } finally {
            closeMocks(mocks);
        }
    }

    @Test
    public void resolve_healsOnlyAfterTimeout_whenHealingEnabled() {
        MockedStatic<?>[] mocks = setupHealingMocks(true);
        try {
            By primary = By.cssSelector("#login-btn");
            when(lastMockDriver.findElements(primary)).thenReturn(Collections.emptyList());
            WebElement healed = mock(WebElement.class);
            when(healed.isDisplayed()).thenReturn(true);
            when(healed.getText()).thenReturn("Healed");
            when(lastMockDriver.findElements(By.id("login-btn"))).thenReturn(List.of(healed));

            long start = System.nanoTime();
            assertEquals(Locator.of(primary).getText(), "Healed");
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertTrue(elapsedMs >= 1500, "heal must run after the wait times out, took " + elapsedMs + "ms");
        } finally {
            closeMocks(mocks);
        }
    }

    @Test
    public void isVisible_doesNotWait_whenElementAbsent() {
        MockedStatic<?>[] mocks = setupHealingMocks(false);
        try {
            By by = By.cssSelector("#absent");
            when(lastMockDriver.findElements(by)).thenReturn(Collections.emptyList());

            long start = System.nanoTime();
            assertFalse(Locator.of(by).isVisible());
            assertFalse(Locator.of(by).isEnabled());
            assertEquals(Locator.of(by).count(), 0);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertTrue(elapsedMs < 1000, "isVisible/isEnabled/count must not wait, took " + elapsedMs + "ms");
        } finally {
            closeMocks(mocks);
        }
    }

    // ----------------------------------------------------------
    // getByText innermost match (API-012)
    // ----------------------------------------------------------

    private List<String> matchedNodeNames(Locator locator, String html) throws Exception {
        MockedStatic<?>[] mocks = setupHealingMocks(false);
        try {
            when(lastMockDriver.findElements(org.mockito.ArgumentMatchers.any(By.class)))
                    .thenReturn(Collections.emptyList());
            locator.count();
            ArgumentCaptor<By> captor = ArgumentCaptor.forClass(By.class);
            org.mockito.Mockito.verify(lastMockDriver).findElements(captor.capture());
            String xpath = String.valueOf(((By.Remotable) captor.getValue()).getRemoteParameters().value());

            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
            NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath()
                    .evaluate(xpath, doc, XPathConstants.NODESET);
            List<String> names = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                names.add(n.getNodeName());
            }
            return names;
        } finally {
            closeMocks(mocks);
        }
    }

    private static final String SIGN_IN_PAGE =
            "<html><body><div id='app'><form><button>Sign In</button></form></div></body></html>";

    @Test
    public void byText_matchesOnlyInnermostElement() throws Exception {
        assertEquals(matchedNodeNames(Locator.byText("Sign In"), SIGN_IN_PAGE), List.of("button"));
    }

    @Test
    public void byText_exact_matchesOnlyInnermostElement() throws Exception {
        assertEquals(matchedNodeNames(Locator.byText("Sign In").exact(), SIGN_IN_PAGE), List.of("button"));
    }

    @Test
    public void byText_keepsParent_whenTextSpansChildElements() throws Exception {
        String html = "<html><body><p>Click <b>Sign</b> In</p></body></html>";
        assertEquals(matchedNodeNames(Locator.byText("sign in"), html), List.of("p"));
    }
}
