package io.testfly.unit;

import io.testfly.quarantine.QuarantineLoader;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link QuarantineLoader}.
 * Uses a temp YAML file and the system property override to avoid touching the real
 * working-directory file.
 */
@Test(singleThreaded = true)
public class QuarantineTest {

    private static final Object LOCK = new Object();

    private File tempFile;

    @BeforeMethod
    public void setup() throws Exception {
        synchronized (LOCK) {
            System.clearProperty("testfly.quarantine");
            QuarantineLoader.reload();
            tempFile = File.createTempFile("testfly-quarantine-", ".yml");
            tempFile.deleteOnExit();
        }
    }

    @AfterMethod
    public void cleanup() {
        synchronized (LOCK) {
            System.clearProperty("testfly.quarantine");
            QuarantineLoader.reload();
            if (tempFile != null) tempFile.delete();
            tempFile = null;
        }
    }

    // ── plain string entries ───────────────────────────────────────────────

    @Test
    public void plainEntry_classMethod_isQuarantined() throws Exception {
        write("quarantine:\n  - com.example.LoginTest#loginTest\n");
        synchronized (LOCK) {
            assertTrue(QuarantineLoader.isQuarantined("com.example.LoginTest#loginTest"));
        }
    }

    @Test
    public void plainEntry_classOnly_matchesAllMethods() throws Exception {
        write("quarantine:\n  - com.example.PaymentTest\n");
        synchronized (LOCK) {
            assertTrue(QuarantineLoader.isQuarantined("com.example.PaymentTest#checkout"));
            assertTrue(QuarantineLoader.isQuarantined("com.example.PaymentTest#refund"));
        }
    }

    @Test
    public void plainEntry_classOnly_doesNotMatchOtherClass() throws Exception {
        write("quarantine:\n  - com.example.PaymentTest\n");
        synchronized (LOCK) {
            assertFalse(QuarantineLoader.isQuarantined("com.example.LoginTest#loginTest"));
        }
    }

    @Test
    public void notInFile_returnsNotQuarantined() throws Exception {
        write("quarantine:\n  - com.example.LoginTest#loginTest\n");
        synchronized (LOCK) {
            assertFalse(QuarantineLoader.isQuarantined("com.example.OtherTest#otherMethod"));
        }
    }

    // ── structured entries ─────────────────────────────────────────────────

    @Test
    public void structuredEntry_isQuarantined() throws Exception {
        write("quarantine:\n  - test: com.example.SearchTest#searchSpecial\n    reason: \"JIRA-42\"\n");
        synchronized (LOCK) {
            assertTrue(QuarantineLoader.isQuarantined("com.example.SearchTest#searchSpecial"));
        }
    }

    @Test
    public void structuredEntry_reasonIsReturned() throws Exception {
        write("quarantine:\n  - test: com.example.SearchTest#searchSpecial\n    reason: \"JIRA-42 unicode bug\"\n");
        synchronized (LOCK) {
            assertEquals(QuarantineLoader.getReason("com.example.SearchTest#searchSpecial"), "JIRA-42 unicode bug");
        }
    }

    @Test
    public void structuredEntry_noReason_returnsDefaultMessage() throws Exception {
        write("quarantine:\n  - test: com.example.SearchTest#searchSpecial\n");
        synchronized (LOCK) {
            String reason = QuarantineLoader.getReason("com.example.SearchTest#searchSpecial");
            assertNotNull(reason);
            assertFalse(reason.isEmpty());
            assertTrue(reason.contains("testfly-quarantine.yml"));
        }
    }

    // ── class-level reason lookup ──────────────────────────────────────────

    @Test
    public void classOnlyEntry_reasonAppliedToMethodLookup() throws Exception {
        write("quarantine:\n  - test: com.example.PaymentTest\n    reason: \"Payment gateway unstable\"\n");
        synchronized (LOCK) {
            assertEquals(
                QuarantineLoader.getReason("com.example.PaymentTest#checkout"),
                "Payment gateway unstable"
            );
        }
    }

    // ── mixed format ───────────────────────────────────────────────────────

    @Test
    public void mixedFormat_bothFormatsWorkTogether() throws Exception {
        write("quarantine:\n" +
              "  - com.example.LoginTest#loginTest\n" +
              "  - test: com.example.SearchTest#search\n" +
              "    reason: \"Flaky\"\n");
        synchronized (LOCK) {
            assertTrue(QuarantineLoader.isQuarantined("com.example.LoginTest#loginTest"));
            assertTrue(QuarantineLoader.isQuarantined("com.example.SearchTest#search"));
            assertEquals(QuarantineLoader.getReason("com.example.SearchTest#search"), "Flaky");
        }
    }

    // ── empty / missing file ───────────────────────────────────────────────

    @Test
    public void emptyFile_nothingIsQuarantined() throws Exception {
        write("");
        synchronized (LOCK) {
            assertFalse(QuarantineLoader.isQuarantined("com.example.LoginTest#loginTest"));
            assertEquals(QuarantineLoader.size(), 0);
        }
    }

    @Test
    public void emptyList_nothingIsQuarantined() throws Exception {
        write("quarantine: []\n");
        synchronized (LOCK) {
            assertFalse(QuarantineLoader.isQuarantined("anything#method"));
            assertEquals(QuarantineLoader.size(), 0);
        }
    }

    @Test
    public void missingFile_nothingIsQuarantined() {
        synchronized (LOCK) {
            System.clearProperty("testfly.quarantine");
            QuarantineLoader.reload();
            assertFalse(QuarantineLoader.isQuarantined("com.example.LoginTest#loginTest"));
            assertEquals(QuarantineLoader.size(), 0);
        }
    }

    // ── caching ────────────────────────────────────────────────────────────

    @Test
    public void caching_fileReadOnlyOnce() throws Exception {
        write("quarantine:\n  - com.example.LoginTest#loginTest\n");
        synchronized (LOCK) {
            assertTrue(QuarantineLoader.isQuarantined("com.example.LoginTest#loginTest"));
            // Second call uses cache — delete file to prove it's not re-read
            tempFile.delete();
            assertTrue(QuarantineLoader.isQuarantined("com.example.LoginTest#loginTest"));
        }
    }

    @Test
    public void reload_clearsCache() throws Exception {
        write("quarantine:\n  - com.example.LoginTest#loginTest\n");
        synchronized (LOCK) {
            assertTrue(QuarantineLoader.isQuarantined("com.example.LoginTest#loginTest"));
            QuarantineLoader.reload();
            // After reload with no file set, should return false
            System.clearProperty("testfly.quarantine");
            QuarantineLoader.reload();
            assertFalse(QuarantineLoader.isQuarantined("com.example.LoginTest#loginTest"));
        }
    }

    // ── Cucumber: tag matching ────────────────────────────────────────────

    @Test
    public void cucumber_tagEntry_matchesScenarioWithThatTag() throws Exception {
        write("quarantine:\n  - \"@smoke\"\n");
        List<String> tags = Arrays.asList("@smoke", "@login");
        synchronized (LOCK) {
            assertTrue(QuarantineLoader.isQuarantinedScenario(tags, "classpath:login.feature", "Login"));
        }
    }

    @Test
    public void cucumber_tagEntry_caseInsensitive() throws Exception {
        write("quarantine:\n  - \"@Smoke\"\n");
        List<String> tags = Collections.singletonList("@smoke");
        synchronized (LOCK) {
            assertTrue(QuarantineLoader.isQuarantinedScenario(tags, "classpath:login.feature", "Login"));
        }
    }

    @Test
    public void cucumber_tagEntry_noAtPrefixInYaml_stillMatches() throws Exception {
        write("quarantine:\n  - \"@smoke\"\n");
        List<String> tags = Collections.singletonList("smoke");
        synchronized (LOCK) {
            assertTrue(QuarantineLoader.isQuarantinedScenario(tags, "classpath:login.feature", "Login"));
        }
    }

    @Test
    public void cucumber_tagEntry_doesNotMatchDifferentTag() throws Exception {
        write("quarantine:\n  - \"@smoke\"\n");
        List<String> tags = Collections.singletonList("@regression");
        synchronized (LOCK) {
            assertFalse(QuarantineLoader.isQuarantinedScenario(tags, "classpath:login.feature", "Login"));
        }
    }

    @Test
    public void cucumber_tagEntry_withReason() throws Exception {
        write("quarantine:\n  - test: \"@regression\"\n    reason: \"Regression suite broken\"\n");
        List<String> tags = Collections.singletonList("@regression");
        synchronized (LOCK) {
            assertTrue(QuarantineLoader.isQuarantinedScenario(tags, "classpath:payment.feature", "Pay"));
            assertEquals(QuarantineLoader.getScenarioReason(tags, "classpath:payment.feature", "Pay"),
                    "Regression suite broken");
        }
    }

    // ── Cucumber: feature file matching ──────────────────────────────────

    @Test
    public void cucumber_featureFile_matchesAllScenariosInFile() throws Exception {
        write("quarantine:\n  - login.feature\n");
        List<String> noTags = Collections.emptyList();
        synchronized (LOCK) {
            assertTrue(QuarantineLoader.isQuarantinedScenario(noTags, "classpath:features/login.feature", "Any scenario"));
            assertTrue(QuarantineLoader.isQuarantinedScenario(noTags, "classpath:features/login.feature", "Another scenario"));
        }
    }

    @Test
    public void cucumber_featureFile_doesNotMatchDifferentFile() throws Exception {
        write("quarantine:\n  - login.feature\n");
        List<String> noTags = Collections.emptyList();
        synchronized (LOCK) {
            assertFalse(QuarantineLoader.isQuarantinedScenario(noTags, "classpath:features/payment.feature", "Pay"));
        }
    }

    @Test
    public void cucumber_featureFile_withSubPath_matchesUri() throws Exception {
        write("quarantine:\n  - features/payment.feature\n");
        List<String> noTags = Collections.emptyList();
        synchronized (LOCK) {
            assertTrue(QuarantineLoader.isQuarantinedScenario(noTags, "classpath:features/payment.feature", "Pay"));
        }
    }

    @Test
    public void cucumber_featureFile_withReason() throws Exception {
        write("quarantine:\n  - test: payment.feature\n    reason: \"Gateway down\"\n");
        List<String> noTags = Collections.emptyList();
        synchronized (LOCK) {
            assertTrue(QuarantineLoader.isQuarantinedScenario(noTags, "classpath:features/payment.feature", "Pay"));
            assertEquals(QuarantineLoader.getScenarioReason(noTags, "classpath:features/payment.feature", "Pay"),
                    "Gateway down");
        }
    }

    // ── Cucumber: feature#scenario-name matching ──────────────────────────

    @Test
    public void cucumber_featureHashName_matchesSpecificScenario() throws Exception {
        write("quarantine:\n  - \"checkout.feature#Checkout with 3D Secure\"\n");
        List<String> noTags = Collections.emptyList();
        synchronized (LOCK) {
            assertTrue(QuarantineLoader.isQuarantinedScenario(
                    noTags, "classpath:features/checkout.feature", "Checkout with 3D Secure"));
        }
    }

    @Test
    public void cucumber_featureHashName_doesNotMatchDifferentScenario() throws Exception {
        write("quarantine:\n  - \"checkout.feature#Checkout with 3D Secure\"\n");
        List<String> noTags = Collections.emptyList();
        synchronized (LOCK) {
            assertFalse(QuarantineLoader.isQuarantinedScenario(
                    noTags, "classpath:features/checkout.feature", "Checkout as guest"));
        }
    }

    @Test
    public void cucumber_featureHashName_scenarioNameCaseInsensitive() throws Exception {
        write("quarantine:\n  - \"login.feature#Login with expired session\"\n");
        List<String> noTags = Collections.emptyList();
        synchronized (LOCK) {
            assertTrue(QuarantineLoader.isQuarantinedScenario(
                    noTags, "classpath:login.feature", "LOGIN WITH EXPIRED SESSION"));
        }
    }

    // ── matchesScenario static helper ─────────────────────────────────────

    @Test
    public void matchesScenario_javaEntry_returnsFalse() {
        List<String> tags = Collections.singletonList("@smoke");
        synchronized (LOCK) {
            assertFalse(QuarantineLoader.matchesScenario(
                    "com.example.LoginTest#loginTest", tags, "classpath:login.feature", "Login"));
            assertFalse(QuarantineLoader.matchesScenario(
                    "com.example.LoginTest", tags, "classpath:login.feature", "Login"));
        }
    }

    @Test
    public void featureUriMatches_variousUriFormats() {
        synchronized (LOCK) {
            assertTrue(QuarantineLoader.featureUriMatches("login.feature",
                    "classpath:features/login.feature"));
            assertTrue(QuarantineLoader.featureUriMatches("features/login.feature",
                    "classpath:features/login.feature"));
            assertTrue(QuarantineLoader.featureUriMatches("login.feature",
                    "file:///home/ci/project/src/test/resources/features/login.feature"));
            assertFalse(QuarantineLoader.featureUriMatches("login.feature",
                    "classpath:features/payment.feature"));
        }
    }

    // ── size ───────────────────────────────────────────────────────────────

    @Test
    public void size_reflectsNumberOfEntries() throws Exception {
        write("quarantine:\n  - com.example.A#a\n  - com.example.B\n  - test: com.example.C#c\n    reason: \"x\"\n");
        synchronized (LOCK) {
            assertEquals(QuarantineLoader.size(), 3);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private void write(String content) throws Exception {
        synchronized (LOCK) {
            try (FileWriter fw = new FileWriter(tempFile)) {
                fw.write(content);
            }
            System.setProperty("testfly.quarantine", tempFile.getAbsolutePath());
            QuarantineLoader.reload();
        }
    }
}
