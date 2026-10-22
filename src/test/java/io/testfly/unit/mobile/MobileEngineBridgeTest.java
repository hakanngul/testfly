package io.testfly.unit.mobile;

import io.testfly.config.TestFlyConfig;
import io.testfly.mobile.Mobile;
import io.testfly.mobile.internal.MobileSessions;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;
import org.testng.TestNG;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.testng.Assert.*;

/**
 * Runs real TestNG and JUnit 5 sample tests in-process against the fake mobilecli server and checks
 * what the engine bridges do: the device opens before the test and closes after it, no browser is
 * started, and a failing test gets a device screenshot.
 */
@Test(singleThreaded = true)
public class MobileEngineBridgeTest {

    static final AtomicReference<FakeApp> APP = new AtomicReference<>();
    static final List<String> SEEN = new ArrayList<>();

    private static String deviceId() {
        return "bridge-" + Long.toHexString(System.nanoTime());
    }

    private static void useFakeDevice(FakeMobilecliServer server, String id) {
        FakeApp app = new FakeApp(id).install(server);
        APP.set(app);
        TestFlyConfig.Mobile m = new TestFlyConfig.Mobile();
        m.setServerUrl(server.url());
        m.setPlatform("android");
        m.setDeviceId(id);
        m.getApp().setBundleId(FakeApp.BUNDLE);
        m.getTimeouts().setActionMs(1500);
        m.getTimeouts().setPollMs(20);
        MobileSessions.setOpener(() -> Mobile.connect(m));
    }

    /**
     * The in-process TestNG/JUnit/Cucumber runs export reports; keep them out of the shared
     * {@code target/reports} that other test classes read in parallel forks.
     */
    @org.testng.annotations.BeforeMethod(alwaysRun = true)
    public void isolateReports() throws Exception {
        System.setProperty("testfly.reports.dir", Files.createTempDirectory("tf-mobile-bridge-reports").toString());
    }

    @AfterMethod(alwaysRun = true)
    public void restore() {
        System.clearProperty("testfly.reports.dir");
        MobileSessions.setOpener(null);
        synchronized (SEEN) {
            SEEN.clear();
        }
    }

    // ── TestNG ──────────────────────────────────────────────────────────

    @Test
    public void testNgOpensTheDeviceSkipsTheBrowserAndScreenshotsFailures() throws Exception {
        String id = deviceId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            useFakeDevice(server, id);
            TestListenerAdapter results = new TestListenerAdapter();
            TestNG testng = new TestNG(false);
            testng.setTestClasses(new Class<?>[] {MobileBridgeSamples.TestNgSample.class});
            testng.addListener(results);
            testng.setVerbose(0);
            testng.run();

            assertEquals(results.getPassedTests().size(), 1, "signsIn passes");
            assertEquals(results.getFailedTests().size(), 1, "failsOnPurpose fails");
            assertEquals(SEEN, List.of("testng:" + id + ":browser=false"));

            ITestResult failed = results.getFailedTests().get(0);
            assertTrue(failed.getThrowable().getMessage().contains("Not on this screen"), failed.getThrowable().getMessage());
            assertScreenshotTaken(server, "failsOnPurpose");
            assertDeviceClosed(server);
        }
    }

    // ── JUnit 5 ─────────────────────────────────────────────────────────

    @Test
    public void junit5OpensTheDeviceSkipsTheBrowserAndScreenshotsFailures() throws Exception {
        String id = deviceId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            useFakeDevice(server, id);
            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request().selectors(selectClass(MobileBridgeSamples.JUnitSample.class)).build();
            Launcher launcher = LauncherFactory.create();
            SummaryGeneratingListener listener = new SummaryGeneratingListener();
            launcher.execute(request, listener);
            TestExecutionSummary summary = listener.getSummary();

            assertEquals(summary.getTestsSucceededCount(), 1, "signsIn passes");
            assertEquals(summary.getTestsFailedCount(), 1, "failsOnPurpose fails");
            assertEquals(SEEN, List.of("junit:" + id + ":browser=false"));
            assertScreenshotTaken(server, "failsOnPurpose");
            assertDeviceClosed(server);
        }
    }

    @Test
    public void mobileTestAnnotationWorksOnAMethodOfAWebOrPlainClass() {
        String id = deviceId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            useFakeDevice(server, id);
            TestListenerAdapter results = new TestListenerAdapter();
            TestNG testng = new TestNG(false);
            testng.setTestClasses(new Class<?>[] {MobileBridgeSamples.WebClassWithMobileMethod.class});
            testng.addListener(results);
            testng.setVerbose(0);
            testng.run();
            assertEquals(results.getPassedTests().size(), 1, String.valueOf(results.getFailedTests()));

            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(selectClass(MobileBridgeSamples.JUnitAnnotated.class)).build();
            SummaryGeneratingListener listener = new SummaryGeneratingListener();
            LauncherFactory.create().execute(request, listener);
            assertEquals(listener.getSummary().getTestsSucceededCount(), 1, String.valueOf(listener.getSummary().getFailures()));

            assertEquals(SEEN, List.of("mixed:" + id + ":browser=false", "junit-annotated:" + id + ":browser=false"));
            assertFalse(MobileSessions.isOpen());
        }
    }

    // ── Cucumber ────────────────────────────────────────────────────────

    private static byte runCucumber(String tag) {
        return io.cucumber.core.cli.Main.run(new String[] {
                "--glue", "io.testfly.cucumber",
                "--glue", "io.testfly.unit.mobile.cucumber",
                "--tags", tag,
                "--plugin", "pretty",
                "--monochrome",
                "classpath:mobile/features"}, Thread.currentThread().getContextClassLoader());
    }

    @Test
    public void cucumberMobileTagOpensTheDeviceAndScreenshotsFailures() throws Exception {
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            useFakeDevice(server, deviceId());
            assertEquals(runCucumber("@pass"), (byte) 0, "the passing scenario passes");
            List<String> afterPass = server.methods();
            assertEquals(afterPass.get(afterPass.size() - 1), "device.apps.terminate", "the device is closed after the scenario");
            assertFalse(afterPass.contains("device.screenshot"));

            assertEquals(runCucumber("@fail"), (byte) 1, "the failing scenario fails");
            assertScreenshotTaken(server, "A_missing_element_fails_with_a_device_screenshot");
            assertFalse(MobileSessions.isOpen());
        }
    }

    @Test
    public void usingTheDeviceOutsideAMobileTestExplainsHowToGetOne() {
        try {
            new io.testfly.mobile.MobileSupport() { }.device();
            fail("expected a MobileException");
        } catch (io.testfly.mobile.MobileException e) {
            assertTrue(e.getMessage().contains("@MobileTest") && e.getMessage().contains("@mobile"), e.getMessage());
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static void assertScreenshotTaken(FakeMobilecliServer server, String testName) throws Exception {
        assertTrue(server.methods().contains("device.screenshot"), server.methods().toString());
        try (var files = Files.list(Path.of("target/reports/screenshots"))) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith(testName + "_mobile_")),
                    "a device screenshot is saved for " + testName);
        }
    }

    private static void assertDeviceClosed(FakeMobilecliServer server) {
        List<String> methods = server.methods();
        assertEquals(methods.stream().filter("device.apps.launch"::equals).count(), 2L, "one launch per test");
        assertEquals(methods.get(methods.size() - 1), "device.apps.terminate", "the last call closes the app: " + methods);
        assertFalse(MobileSessions.isOpen(), "nothing left open on the runner thread");
        assertEquals(APP.get().foreground, "com.android.launcher");
    }
}
