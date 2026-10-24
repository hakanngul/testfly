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
        useFakeDevice(server, id, "relaunch");
    }

    private static void useFakeDevice(FakeMobilecliServer server, String id, String reset) {
        useFakeDevice(server, id, reset, "off");
    }

    private static TestFlyConfig.Mobile config(FakeMobilecliServer server, String id) {
        TestFlyConfig.Mobile m = new TestFlyConfig.Mobile();
        m.setServerUrl(server.url());
        m.setPlatform("android");
        m.setDeviceId(id);
        m.getApp().setBundleId(FakeApp.BUNDLE);
        m.getTimeouts().setActionMs(1500);
        m.getTimeouts().setPollMs(20);
        return m;
    }

    private static void useFakeDevice(FakeMobilecliServer server, String id, String reset, String recordVideo) {
        FakeApp app = new FakeApp(id).install(server);
        APP.set(app);
        TestFlyConfig.Mobile m = new TestFlyConfig.Mobile();
        m.setServerUrl(server.url());
        m.setPlatform("android");
        m.setDeviceId(id);
        m.getApp().setBundleId(FakeApp.BUNDLE);
        m.getApp().setReset(reset);
        m.setRecordVideo(recordVideo);
        m.getTimeouts().setActionMs(1500);
        m.getTimeouts().setPollMs(20);
        MobileSessions.setOpener(p -> Mobile.connect(m));
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

    @Test
    public void mobileTestPlatformReachesTheOpenerOnTestNgAndJUnit() {
        String id = deviceId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            useFakeDevice(server, id);
            List<String> requested = new ArrayList<>();
            TestFlyConfig.Mobile m = config(server, id);
            MobileSessions.setOpener(p -> {
                synchronized (requested) {
                    requested.add(String.valueOf(p));
                }
                return Mobile.connect(m);
            });
            TestNG testng = new TestNG(false);
            testng.setTestClasses(new Class<?>[] {MobileBridgeSamples.TestNgPlatformSample.class});
            testng.setVerbose(0);
            testng.run();
            assertEquals(requested.stream().sorted().toList(), List.of("ANDROID", "IOS"), "method wins over class");

            requested.clear();
            LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                    .selectors(selectClass(MobileBridgeSamples.JUnitPlatformSample.class)).build());
            assertEquals(requested.stream().sorted().toList(), List.of("IOS", "null"), "no platform: the configured one");
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

    @Test
    public void parallelTestNgWorkersGetDifferentPoolDevices() {
        String a = deviceId();
        String b = deviceId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            // reset: none keeps the app as it is; the shared fake app starts in the foreground.
            new FakeApp(a, b).install(server).foreground = FakeApp.BUNDLE;
            TestFlyConfig.Mobile m = new TestFlyConfig.Mobile();
            m.setServerUrl(server.url());
            m.setPlatform("android");
            m.setDevices(List.of(a, b));
            m.getApp().setBundleId(FakeApp.BUNDLE);
            m.getApp().setReset("none");
            m.getTimeouts().setPollMs(20);
            MobileSessions.setOpener(p -> Mobile.connect(m));

            TestListenerAdapter results = new TestListenerAdapter();
            TestNG testng = new TestNG(false);
            testng.setTestClasses(new Class<?>[] {MobileBridgeSamples.ParallelSample.class});
            testng.setParallel(org.testng.xml.XmlSuite.ParallelMode.METHODS);
            testng.setThreadCount(2);
            testng.addListener(results);
            testng.setVerbose(0);
            testng.run();

            assertEquals(results.getPassedTests().size(), 2, results.getFailedTests().stream()
                    .map(r -> String.valueOf(r.getThrowable())).toList().toString());
            assertEquals(new java.util.HashSet<>(SEEN), java.util.Set.of("parallel:" + a, "parallel:" + b),
                    "the two parallel tests ran on different devices: " + SEEN);
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

    // ── mobile.recordVideo ──────────────────────────────────────────────

    @Test
    public void failedTestKeepsItsRecordingAndPassedTestDoesNot() throws Exception {
        String id = deviceId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            useFakeDevice(server, id, "relaunch", "failures");
            TestListenerAdapter results = new TestListenerAdapter();
            TestNG testng = new TestNG(false);
            testng.setTestClasses(new Class<?>[] {MobileBridgeSamples.TestNgSample.class});
            testng.addListener(results);
            testng.setVerbose(0);
            testng.run();
            assertEquals(results.getFailedTests().size(), 1);

            assertEquals(server.methods().stream().filter("device.screenrecord"::equals).count(), 2L, "one recording per test");
            Path videos = Path.of("target", "reports", "videos");
            try (var files = Files.list(videos)) {
                List<Path> kept = files.filter(f -> f.getFileName().toString().contains("failsOnPurpose")).toList();
                assertEquals(kept.size(), 1, "only the failed test's video is kept: " + kept);
                for (Path f : kept) {
                    Files.delete(f);
                }
            }
            try (var files = Files.list(Path.of("target", "mobile-recordings"))) {
                assertTrue(files.noneMatch(f -> f.getFileName().toString().startsWith(id)), "the passed test's video is deleted");
            }
        }
    }

    // ── @PreCondition on mobile tests ───────────────────────────────────

    private static final java.util.concurrent.atomic.AtomicBoolean CONDITIONS_REGISTERED = new java.util.concurrent.atomic.AtomicBoolean();

    private static void registerConditions() {
        if (CONDITIONS_REGISTERED.compareAndSet(false, true)) {
            io.testfly.mobile.MobileConditions.register(new MobileBridgeSamples.SignInConditions());
        }
    }

    private static List<String> runTestNg(Class<?> sample) {
        TestListenerAdapter results = new TestListenerAdapter();
        TestNG testng = new TestNG(false);
        testng.setTestClasses(new Class<?>[] {sample});
        testng.addListener(results);
        testng.setVerbose(0);
        testng.run();
        assertEquals(results.getFailedTests().size(), 0, String.valueOf(results.getFailedTests().stream().map(ITestResult::getThrowable).toList()));
        assertEquals(results.getPassedTests().size(), 2);
        synchronized (SEEN) {
            return SEEN.stream().filter(s -> s.startsWith("provider:")).toList();
        }
    }

    @Test
    public void preConditionRunsBeforeEveryMobileTestWhenTheAppIsReset() {
        registerConditions();
        String id = deviceId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            useFakeDevice(server, id, "relaunch");
            assertEquals(runTestNg(MobileBridgeSamples.TestNgPreConditionSample.class), List.of("provider:" + id, "provider:" + id),
                    "the app is relaunched before each test, so the condition runs each time");
        }
    }

    @Test
    public void preConditionRunsOncePerDeviceWhenTheAppKeepsItsState() {
        registerConditions();
        String id = deviceId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            useFakeDevice(server, id, "none");
            APP.get().foreground = FakeApp.BUNDLE; // reset none never launches the app
            assertEquals(runTestNg(MobileBridgeSamples.TestNgPreConditionSample.class), List.of("provider:" + id),
                    "the second test reuses the signed-in app");
        }
    }

    @Test
    public void preConditionRunsForJUnit5MobileTests() {
        registerConditions();
        String id = deviceId();
        try (FakeMobilecliServer server = new FakeMobilecliServer()) {
            useFakeDevice(server, id, "relaunch");
            SummaryGeneratingListener listener = new SummaryGeneratingListener();
            LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
                    .selectors(selectClass(MobileBridgeSamples.JUnitPreConditionSample.class)).build(), listener);
            assertEquals(listener.getSummary().getTestsSucceededCount(), 1, String.valueOf(listener.getSummary().getFailures().stream().map(f -> f.getException()).toList()));
            synchronized (SEEN) {
                assertEquals(SEEN, List.of("provider:" + id));
            }
        }
    }

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
