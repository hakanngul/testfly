package io.testfly.unit.loadtest;

import com.acme.uploadtests.DownloadTest;
import com.acme.uploadtests.FileUploadTest;
import io.testfly.loadtest.BaseLoadTest;
import io.testfly.loadtest.LoadTest;
import io.testfly.loadtest.internal.LoadTestDetector;
import io.testfly.test.NoBrowser;
import io.testfly.test.support.LoadTestSupport;
import org.testng.annotations.Test;

import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** T1.11 — LoadTestDetector replaces the name-based "loadtest" heuristics. */
public class LoadTestDetectorTest {

    static class ExtendsBase extends BaseLoadTest {
    }

    static class ImplementsSupport implements LoadTestSupport {
    }

    @LoadTest
    static class AnnotatedClass {
    }

    @NoBrowser
    static class NoBrowserClass {
    }

    static class MethodLevel {
        @LoadTest
        public void load() {
        }

        @NoBrowser
        public void noBrowser() {
        }

        public void plain() {
        }
    }

    // ── Negative: names containing "loadtest" are NOT load tests ──────────

    @Test
    public void fileUploadTest_isNotALoadTest() {
        assertFalse(LoadTestDetector.isLoadTest(FileUploadTest.class, null));
        assertFalse(LoadTestDetector.skipsBrowser(FileUploadTest.class, null));
    }

    @Test
    public void downloadTest_isNotALoadTest() {
        assertFalse(LoadTestDetector.isLoadTest(DownloadTest.class, null));
        assertFalse(LoadTestDetector.skipsBrowser(DownloadTest.class, null));
    }

    @Test
    public void classInPackageUploadTests_isNotALoadTest() {
        assertTrue(FileUploadTest.class.getPackageName().contains("uploadtests"));
        assertFalse(LoadTestDetector.isLoadTest(FileUploadTest.class, null));
    }

    @Test
    public void legacyNameMatch_logsOneTimeWarning() {
        LoadTestDetector.isLoadTest(FileUploadTest.class, null);
        LoadTestDetector.isLoadTest(FileUploadTest.class, null);
        assertTrue(LoadTestDetector.legacyNameWarnings().contains(FileUploadTest.class.getName()));
        long count = LoadTestDetector.legacyNameWarnings().stream()
                .filter(FileUploadTest.class.getName()::equals).count();
        assertEquals(count, 1L, "warning recorded once per class");
    }

    // ── Positive: explicit signals ───────────────────────────────────────

    @Test
    public void baseLoadTestSubclass_isALoadTest() {
        assertTrue(LoadTestDetector.isLoadTest(ExtendsBase.class, null));
        assertTrue(LoadTestDetector.skipsBrowser(ExtendsBase.class, null));
    }

    @Test
    public void loadTestSupportImplementor_isALoadTest() {
        assertTrue(LoadTestDetector.isLoadTest(ImplementsSupport.class, null));
    }

    @Test
    public void loadTestAnnotatedClass_isALoadTest() {
        assertTrue(LoadTestDetector.isLoadTest(AnnotatedClass.class, null));
    }

    @Test
    public void loadTestAnnotatedMethod_isALoadTest() throws Exception {
        Method load = MethodLevel.class.getMethod("load");
        Method plain = MethodLevel.class.getMethod("plain");
        assertTrue(LoadTestDetector.isLoadTest(MethodLevel.class, load));
        assertFalse(LoadTestDetector.isLoadTest(MethodLevel.class, plain));
    }

    @Test
    public void noBrowser_skipsBrowserButIsNotALoadTest() throws Exception {
        assertTrue(LoadTestDetector.skipsBrowser(NoBrowserClass.class, null));
        assertFalse(LoadTestDetector.isLoadTest(NoBrowserClass.class, null));
        Method noBrowser = MethodLevel.class.getMethod("noBrowser");
        assertTrue(LoadTestDetector.skipsBrowser(MethodLevel.class, noBrowser));
    }

    @Test
    public void nullInputs_areSafe() {
        assertFalse(LoadTestDetector.isLoadTest(null, null));
        assertFalse(LoadTestDetector.skipsBrowser(null, null));
        assertFalse(LoadTestDetector.isLoadTestActive(null, null));
    }

    // ── Cucumber tags ────────────────────────────────────────────────────

    @Test
    public void exactLoadTestTag_matches_caseInsensitive() {
        assertTrue(LoadTestDetector.isLoadTestTag("@loadtest"));
        assertTrue(LoadTestDetector.isLoadTestTag("@LoadTest"));
    }

    @Test
    public void tagsMerelyContainingLoadtest_doNotMatch() {
        assertFalse(LoadTestDetector.isLoadTestTag("@fileuploadtest"));
        assertFalse(LoadTestDetector.isLoadTestTag("@downloadtests"));
        assertFalse(LoadTestDetector.isLoadTestTag(null));
    }
}
