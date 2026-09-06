package io.testfly.unit.ai.remediation;

import io.testfly.ai.remediation.SourceCodeLocator;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SourceCodeLocatorTest {

    @Test
    public void findFailureSnippet_skipsFrameworkClassesAndFindsTarget() {
        // Stack trace pointing to an existing file in the repo (DomPruner.java)
        String stackTrace = """
            java.lang.NullPointerException: Cannot invoke method
                at io.testfly.wait.WaitEngine.waitForVisible(WaitEngine.java:76)
                at org.openqa.selenium.support.ui.FluentWait.until(FluentWait.java:228)
                at io.testfly.ai.DomPruner.prune(DomPruner.java:55)
                at org.testng.internal.invokers.TestInvoker.invokeTestMethod(TestInvoker.java:228)
            """;

        SourceCodeLocator.SourceSnippet snippet = SourceCodeLocator.findFailureSnippet(stackTrace);

        Assert.assertNotNull(snippet, "Should resolve snippet for DomPruner.java");
        Assert.assertEquals(snippet.lineNumber(), 55);
        Assert.assertTrue(snippet.file().exists());
        Assert.assertTrue(snippet.relativePath().contains("DomPruner.java"));
        Assert.assertTrue(snippet.contextCode().contains("->"));
        Assert.assertTrue(snippet.startLine() <= 55 && snippet.endLine() >= 55);
    }

    @Test
    public void findFailureSnippet_handlesNullAndEmpty() {
        Assert.assertNull(SourceCodeLocator.findFailureSnippet((String) null));
        Assert.assertNull(SourceCodeLocator.findFailureSnippet("   "));
        Assert.assertNull(SourceCodeLocator.findFailureSnippet(new StackTraceElement[0]));
    }

    @Test
    public void findFailureSnippet_whenAllLinesIgnored_returnsNull() {
        String onlyFramework = """
            at io.testfly.wait.WaitEngine.waitForVisible(WaitEngine.java:76)
            at org.openqa.selenium.By.findElement(By.java:123)
            at org.testng.internal.MethodInvocationHelper.invokeMethod(MethodInvocationHelper.java:139)
            """;

        SourceCodeLocator.SourceSnippet snippet = SourceCodeLocator.findFailureSnippet(onlyFramework);
        Assert.assertNull(snippet, "Should return null when only framework classes are present");
    }
}
