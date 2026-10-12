package io.testfly.unit.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.testfly.api.mock.ApiMockServer;
import io.testfly.client.ApiClient;
import io.testfly.test.support.MockSupport;
import org.testng.IAnnotationTransformer;
import org.testng.TestListenerAdapter;
import org.testng.annotations.ITestAnnotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.testng.TestNG;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.testng.Assert.*;

@Test(singleThreaded = true)
public class ApiMockServerTest extends ApiClientTestBase {

    /**
     * Fixtures for nested TestNG runs. They are disabled so the outer surefire run (whose custom
     * excludes also pick up inner classes) never executes them; {@link #runNested} enables them.
     */
    public static class MockSupportFixture implements MockSupport {
        @Test(enabled = false)
        public void registersAStub() {
            stubGet("/fixture", 200, "{\"ok\":true}");
            assertEquals(mockServer().getRawServer().getStubMappings().size(), 1);
        }
    }

    /** The documented parallel-safe override: only this thread's stubs are removed. */
    public static class OwnStubsFixture implements MockSupport {
        @Test(enabled = false)
        public void registersAStub() {
            mockServer().stubJson("GET", "/own", 200, "{}");
        }

        @Override
        @AfterMethod(alwaysRun = true)
        public void resetMocks() {
            mockServer().resetOwnStubs();
        }
    }

    private static TestListenerAdapter runNested(Class<?> fixture) {
        TestNG testng = new TestNG(false);
        testng.setTestClasses(new Class[]{fixture});
        testng.setUseDefaultListeners(false);
        // TestNG keeps one annotation transformer; keep the service-loaded retry transformer out of the way.
        testng.setListenersToSkipFromBeingWiredInViaServiceLoaders(
                "io.testfly.listeners.RetryAnnotationTransformer");
        testng.addListener(new IAnnotationTransformer() {
            @Override
            public void transform(ITestAnnotation annotation, Class testClass, Constructor testConstructor, Method testMethod) {
                annotation.setEnabled(true);
            }
        });
        TestListenerAdapter results = new TestListenerAdapter();
        testng.addListener(results);
        testng.run();
        return results;
    }

    @AfterMethod(alwaysRun = true)
    public void stopServer() {
        ApiMockServer.getInstance().stop();
    }

    @Test
    public void testNgRunsMockSupportAfterMethodDefaultMethod() {
        TestListenerAdapter results = runNested(MockSupportFixture.class);

        assertEquals(results.getPassedTests().size(), 1, String.valueOf(results.getFailedTests()));
        assertTrue(ApiMockServer.getInstance().getRawServer().getStubMappings().isEmpty(),
                "MockSupport.resetMocks() (@AfterMethod on an interface default method) must have run");
    }

    @Test
    public void overriddenResetMocksRemovesOnlyOwnStubs() {
        ApiMockServer mock = ApiMockServer.getInstance();
        mock.start();
        mock.getRawServer().stubFor(WireMock.get("/foreign").willReturn(WireMock.ok()));

        TestListenerAdapter results = runNested(OwnStubsFixture.class);

        assertEquals(results.getPassedTests().size(), 1, String.valueOf(results.getFailedTests()));
        var urls = mock.getRawServer().getStubMappings().stream().map(m -> m.getRequest().getUrl()).toList();
        assertEquals(urls, java.util.List.of("/foreign"), "own stub removed once, foreign stub kept");
    }

    @Test
    public void stubsAreInstanceBoundAndResettablePerThread() throws Exception {
        ApiMockServer mock = ApiMockServer.getInstance();
        mock.start();
        String base = mock.getBaseUrl();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> a = executor.submit(() -> mock.stubJson("POST", "/a", 201, "{\"from\":\"a\"}"));
            a.get();
            Future<?> b = executor.submit(() -> {
                mock.stubFor(WireMock.get("/b").willReturn(WireMock.ok("b")));
                mock.resetOwnStubs();
            });
            b.get();
        }

        assertEquals(ApiClient.post(base + "/a").body("{}").send().status(), 201, "thread A's stub survives B's reset");
        assertEquals(ApiClient.get(base + "/b").send().status(), 404, "thread B removed its own stub");

        mock.reset();
        assertEquals(ApiClient.post(base + "/a").body("{}").send().status(), 404);
    }
}
