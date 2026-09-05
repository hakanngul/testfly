package io.testfly.network;

import io.testfly.driver.DriverManager;
import io.testfly.test.support.BrowserSupport;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mockStatic;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;

/**
 * Verifies the {@link BrowserSupport} default methods delegate to the per-thread
 * {@link NetworkMock}. Uses a non-Chromium driver (null) so registration happens
 * without CDP.
 */
public class BrowserSupportNetworkTest {

    /** Minimal BrowserSupport implementation for testing default methods. */
    static final class Support implements BrowserSupport {}

    @AfterMethod
    public void cleanup() {
        NetworkMock.cleanup();
    }

    @Test
    public void mockRoute_delegatesToPerThreadNetworkMock() {
        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class)) {
            dm.when(DriverManager::getDriver).thenReturn(null);
            Support support = new Support();

            NetworkMock returned = support.mockRoute("**/api/x", Response.status(200));
            assertSame(returned, NetworkMock.get(), "should operate on the thread's NetworkMock");

            // The rule is registered on the same instance
            RouteRule r = NetworkMock.get().resolveRuleForTest("https://h/api/x", "GET");
            assertNotNull(r);
        }
    }

    @Test
    public void mockRoute_methodScoped_delegates() {
        try (MockedStatic<DriverManager> dm = mockStatic(DriverManager.class)) {
            dm.when(DriverManager::getDriver).thenReturn(null);
            Support support = new Support();
            support.mockRoute("POST", "**/api/y", route -> route.passthrough());

            RouteRule r = NetworkMock.get().resolveRuleForTest("https://h/api/y", "POST");
            assertNotNull(r);
            org.testng.Assert.assertTrue(r.isHandler());
        }
    }

    @Test
    public void assertThatNetwork_returnsNetworkAssert() {
        Support support = new Support();
        NetworkAssert na = support.assertThatNetwork();
        assertNotNull(na);
    }
}
