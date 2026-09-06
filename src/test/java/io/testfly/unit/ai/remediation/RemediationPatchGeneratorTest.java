package io.testfly.unit.ai.remediation;

import io.testfly.ai.AiProvider;
import io.testfly.ai.AiProviderRegistry;
import io.testfly.ai.remediation.RemediationPatchGenerator;
import io.testfly.ai.remediation.SourceCodeLocator;
import io.testfly.config.TestFlyConfig;
import io.testfly.internal.TestFlyContext;
import io.testfly.metrics.TestTiming;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

@Test(singleThreaded = true)
public class RemediationPatchGeneratorTest {

    private static final Object LOCK = TestFlyContext.class;
    private File createdPatchFile;

    @BeforeMethod
    public void setup() {
        synchronized (LOCK) {
            TestFlyContext.reset();
        }
    }

    @AfterMethod
    public void tearDown() {
        synchronized (LOCK) {
            TestFlyContext.reset();
        }
        if (createdPatchFile != null && createdPatchFile.exists()) {
            createdPatchFile.delete();
        }
        new File("target/remediations/test1.patch").delete();
    }

    @Test
    public void sanitizePatch_stripsMarkdownCodeFences() {
        String fenced = """
            ```diff
            --- a/LoginPage.java
            +++ b/LoginPage.java
            @@ -12,3 +12,3 @@
            -    private By btn = By.id("old");
            +    private By btn = By.id("new");
            ```
            """;

        String clean = RemediationPatchGenerator.sanitizePatch(fenced);
        Assert.assertFalse(clean.contains("```"));
        Assert.assertTrue(clean.startsWith("--- a/LoginPage.java"));
        Assert.assertTrue(clean.contains("@@ -12,3 +12,3 @@"));
    }

    @Test
    public void buildPatchPrompt_containsAllRequiredContext() {
        File dummyFile = new File("src/test/java/com/example/MyTest.java");
        SourceCodeLocator.SourceSnippet snippet = new SourceCodeLocator.SourceSnippet(
                dummyFile, "src/test/java/com/example/MyTest.java", 42, 32, 52,
                "42: -> find(By.id(\"submit\")).click();\n",
                "find(By.id(\"submit\")).click();"
        );

        TestTiming timing = new TestTiming("com.example.MyTest#testLogin", "chrome");
        timing.setErrorMessage("NoSuchElementException: Element not found #submit");

        String prompt = RemediationPatchGenerator.buildPatchPrompt(
                "com.example.MyTest#testLogin", snippet, timing, "https://example.com/login", "Login"
        );

        Assert.assertTrue(prompt.contains("com.example.MyTest#testLogin"));
        Assert.assertTrue(prompt.contains("src/test/java/com/example/MyTest.java"));
        Assert.assertTrue(prompt.contains("Line: 42"));
        Assert.assertTrue(prompt.contains("NoSuchElementException"));
        Assert.assertTrue(prompt.contains("find(By.id(\"submit\")).click();"));
        Assert.assertTrue(prompt.contains("Unified Diff"));
    }

    @Test
    public void generateAndSave_whenDisabled_returnsNull() {
        synchronized (LOCK) {
            TestFlyConfig config = new TestFlyConfig();
            TestFlyConfig.Ai ai = new TestFlyConfig.Ai();
            ai.setGeneratePatch(false);
            config.setAi(ai);
            TestFlyContext.setConfig(config);

            File dummyFile = new File("src/test/java/com/example/MyTest.java");
            SourceCodeLocator.SourceSnippet snippet = new SourceCodeLocator.SourceSnippet(
                    dummyFile, "src/test/java/com/example/MyTest.java", 10, 1, 20, "code", "line"
            );
            TestTiming timing = new TestTiming("test1", "chrome");

            File patch = RemediationPatchGenerator.generateAndSave("test1", snippet, timing, null, null);
            Assert.assertNull(patch, "Should return null when generatePatch is false");
        }
    }

    @Test
    public void generateAndSave_whenEnabledAndValid_createsPatchFile() throws Exception {
        synchronized (LOCK) {
            TestFlyConfig config = new TestFlyConfig();
            TestFlyConfig.Ai ai = new TestFlyConfig.Ai();
            ai.setGeneratePatch(true);
            ai.setProvider("mock-patch-ai");
            ai.setApiKey("test-key");
            ai.setModel("mock-model");
            config.setAi(ai);
            TestFlyContext.setConfig(config);

            String mockDiff = """
                ```diff
                --- a/src/test/java/com/example/MyTest.java
                +++ b/src/test/java/com/example/MyTest.java
                @@ -42,3 +42,3 @@
                -    find(By.id("old")).click();
                +    find(By.cssSelector("button.submit-new")).click();
                ```
                """;

            AiProvider mockProvider = Mockito.mock(AiProvider.class);
            Mockito.when(mockProvider.name()).thenReturn("mock-patch-ai");
            Mockito.when(mockProvider.call(Mockito.anyString(), Mockito.nullable(String.class), Mockito.anyString(), Mockito.anyInt()))
                    .thenReturn(mockDiff);
            AiProviderRegistry.register(mockProvider);

            File dummyFile = new File("src/test/java/com/example/MyTest.java");
            SourceCodeLocator.SourceSnippet snippet = new SourceCodeLocator.SourceSnippet(
                    dummyFile, "src/test/java/com/example/MyTest.java", 42, 32, 52, "code", "line"
            );
            TestTiming timing = new TestTiming("com.example.MyTest#testLogin", "chrome");
            timing.setErrorMessage("Element not found");

            createdPatchFile = RemediationPatchGenerator.generateAndSave(
                    "com.example.MyTest#testLogin", snippet, timing, "https://example.com", "Test"
            );

            Assert.assertNotNull(createdPatchFile, "Should return the created patch file");
            Assert.assertTrue(createdPatchFile.exists(), "Patch file must exist on disk");
            Assert.assertTrue(createdPatchFile.getName().contains("com.example.MyTest_testLogin.patch"));

            String content = Files.readString(createdPatchFile.toPath());
            Assert.assertTrue(content.contains("--- a/src/test/java/com/example/MyTest.java"));
            Assert.assertTrue(content.contains("button.submit-new"));
        }
    }

    @Test
    public void generateAndSave_whenAiProviderThrows_failsGracefully() {
        synchronized (LOCK) {
            TestFlyConfig config = new TestFlyConfig();
            TestFlyConfig.Ai ai = new TestFlyConfig.Ai();
            ai.setGeneratePatch(true);
            ai.setProvider("mock-error-patch");
            ai.setApiKey("test-key");
            config.setAi(ai);
            TestFlyContext.setConfig(config);

            AiProvider mockProvider = Mockito.mock(AiProvider.class);
            Mockito.when(mockProvider.name()).thenReturn("mock-error-patch");
            Mockito.when(mockProvider.call(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyInt()))
                    .thenThrow(new RuntimeException("Simulated AI error"));
            AiProviderRegistry.register(mockProvider);

            File dummyFile = new File("src/test/java/com/example/MyTest.java");
            SourceCodeLocator.SourceSnippet snippet = new SourceCodeLocator.SourceSnippet(
                    dummyFile, "src/test/java/com/example/MyTest.java", 42, 32, 52, "code", "line"
            );
            TestTiming timing = new TestTiming("testError", "chrome");

            File patch = RemediationPatchGenerator.generateAndSave("testError", snippet, timing, null, null);
            Assert.assertNull(patch, "Should return null on AI exception without throwing");
        }
    }
}
