package io.testfly.network;

import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import org.mockito.MockedStatic;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.devtools.DevTools;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link NetworkMock} routing precedence, method selection,
 * blocklist, and CDP attachment. Uses mocked drivers; routing precedence is
 * verified through the package-private {@code resolveRuleForTest} hook without
 * simulating CDP events (which requires final CDP model classes).
 */
public class NetworkMockRoutingTest {

    @AfterMethod
    public void cleanup() {
        NetworkMock.cleanup();
    }

    // ------------------------------------------------------------------
    // Precedence & method selection (no CDP — driver resolves to null,
    // so mockRoute registers the rule but skips CDP send)
    // ------------------------------------------------------------------

    @Test
    public void legacyStubBeforeRoute_firstWins() {
        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class)) {
            dm.when(DriverManager::getDriver).thenReturn(null); // non-Chromium path
            NetworkMock mock = NetworkMock.get();
            mock.stub("**/api/x").returnStatus(201);          // legacy first
            mock.mockRoute("**/api/x", Response.status(500));  // route second

            RouteRule r = mock.resolveRuleForTest("https://h/api/x", "GET");
            assertEquals(r.source, RouteRule.Source.LEGACY_STUB, "first-registered wins");
            assertEquals(r.response.status(), 201);
        }
    }

    @Test
    public void exactMethodPreferredOverWildcard() {
        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class)) {
            dm.when(DriverManager::getDriver).thenReturn(null);
            NetworkMock mock = NetworkMock.get();
            mock.mockRoute("**/api/y", Response.status(200));         // any-method, first
            mock.mockRoute("POST", "**/api/y", Response.status(418)); // exact POST

            RouteRule r = mock.resolveRuleForTest("https://h/api/y", "POST");
            assertTrue(r.isExactMethod(), "exact method should win for POST");
            assertEquals(r.response.status(), 418);

            RouteRule g = mock.resolveRuleForTest("https://h/api/y", "GET");
            assertEquals(g.response.status(), 200, "GET falls back to any-method route");
        }
    }

    @Test
    public void nonMatchingMethod_doesNotMatch() {
        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class)) {
            dm.when(DriverManager::getDriver).thenReturn(null);
            NetworkMock mock = NetworkMock.get();
            mock.mockRoute("POST", "**/only-post", Response.status(200));

            assertNull(mock.resolveRuleForTest("https://h/only-post", "GET"),
                    "GET must not match a POST-only route");
        }
    }

    @Test
    public void explicitRoute_beatsBlocklist() {
        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class);
             MockedStatic<TestFlyContext> ctx = mockStatic(TestFlyContext.class)) {
            dm.when(DriverManager::getDriver).thenReturn(null);

            TestFlyConfig config = new TestFlyConfig();
            TestFlyConfig.Network net = new TestFlyConfig.Network();
            net.setBlockUrls(List.of("**/tracker/**"));
            config.setNetwork(net);
            ctx.when(TestFlyContext::getConfig).thenReturn(config);

            NetworkMock mock = NetworkMock.get();
            mock.activateBlocklistIfConfigured();
            mock.mockRoute("**/tracker/**", Response.json(200, "{}")); // explicit after blocklist

            RouteRule r = mock.resolveRuleForTest("https://h/tracker/pixel", "GET");
            assertEquals(r.source, RouteRule.Source.ROUTE, "explicit route beats blocklist");
        }
    }

    @Test
    public void blocklistMatches_whenNoExplicitRule() {
        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class);
             MockedStatic<TestFlyContext> ctx = mockStatic(TestFlyContext.class)) {
            dm.when(DriverManager::getDriver).thenReturn(null);

            TestFlyConfig config = new TestFlyConfig();
            TestFlyConfig.Network net = new TestFlyConfig.Network();
            net.setBlockUrls(List.of("**/ads/**"));
            config.setNetwork(net);
            ctx.when(TestFlyContext::getConfig).thenReturn(config);

            NetworkMock mock = NetworkMock.get();
            mock.activateBlocklistIfConfigured();

            RouteRule r = mock.resolveRuleForTest("https://h/ads/banner", "GET");
            assertEquals(r.source, RouteRule.Source.BLOCKLIST);
            assertEquals(r.response.abortReason(), AbortReason.BLOCKED_BY_CLIENT);
        }
    }

    // ------------------------------------------------------------------
    // Blocklist activation edge cases
    // ------------------------------------------------------------------

    @Test
    public void activateBlocklist_emptyList_isNoOp() {
        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class);
             MockedStatic<TestFlyContext> ctx = mockStatic(TestFlyContext.class)) {
            ChromiumDriver driver = mock(ChromiumDriver.class);
            DevTools devTools = mock(DevTools.class);
            lenient().when(driver.getDevTools()).thenReturn(devTools);
            dm.when(DriverManager::getDriver).thenReturn(driver);

            TestFlyConfig config = new TestFlyConfig();
            config.setNetwork(new TestFlyConfig.Network()); // empty blockUrls
            ctx.when(TestFlyContext::getConfig).thenReturn(config);

            NetworkMock mock = NetworkMock.get();
            mock.activateBlocklistIfConfigured();

            // No blocklist → no CDP attachment triggered by activation alone
            verify(driver, never()).getDevTools();
        }
    }

    // ------------------------------------------------------------------
    // CDP attachment on a supported browser
    // ------------------------------------------------------------------

    @Test
    public void mockRoute_onChromium_enablesFetchAndAddsListener() {
        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class)) {
            ChromiumDriver driver = mock(ChromiumDriver.class);
            DevTools devTools = mock(DevTools.class);
            when(driver.getDevTools()).thenReturn(devTools);
            dm.when(DriverManager::getDriver).thenReturn(driver);

            NetworkMock mock = NetworkMock.get();
            mock.mockRoute("**/api/z", Response.json(200, "{}"));

            verify(driver).getDevTools();
            verify(devTools).createSessionIfThereIsNotOne();
            // Fetch.enable is sent on the DevTools session; listener registration
            // is verified indirectly via isInterceptionActive() below.
            verify(devTools).send(org.mockito.ArgumentMatchers.any());
            assertTrue(mock.isInterceptionActive());
        }
    }

    @Test
    public void nonChromium_gracefulDegrade_noCdpSends() {
        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class)) {
            WebDriver plain = mock(WebDriver.class); // not a ChromiumDriver
            dm.when(DriverManager::getDriver).thenReturn(plain);

            NetworkMock mock = NetworkMock.get();
            mock.mockRoute("**/api/nope", Response.status(200));

            // Rule is registered but interception is not active on a non-Chromium browser
            assertTrue(!mock.isInterceptionActive());
            RouteRule r = mock.resolveRuleForTest("https://h/api/nope", "GET");
            assertSame(r.response.kind(), Response.Kind.FULFILL);
        }
    }
}
