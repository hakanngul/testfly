package io.testfly.unit.flakiness;

import io.testfly.flakiness.SmartTriageEngine;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriverException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.net.ConnectException;

public class SmartTriageEngineTest {

    @Test
    public void testTriageSystemFlaky() {
        // Retry passed rule
        Assert.assertEquals(SmartTriageEngine.triageFailure(new AssertionError("foo"), true),
                SmartTriageEngine.TriageResult.SYSTEM_FLAKY);

        // TimeoutException
        Assert.assertEquals(SmartTriageEngine.triageFailure(new TimeoutException("wait failed"), false),
                SmartTriageEngine.TriageResult.SYSTEM_FLAKY);

        // ConnectException
        Assert.assertEquals(SmartTriageEngine.triageFailure(new ConnectException("connection refused"), false),
                SmartTriageEngine.TriageResult.SYSTEM_FLAKY);

        // StaleElementReferenceException
        Assert.assertEquals(SmartTriageEngine.triageFailure(new StaleElementReferenceException("stale"), false),
                SmartTriageEngine.TriageResult.SYSTEM_FLAKY);
    }

    @Test
    public void testTriageApplicationBug() {
        // AssertionError
        Assert.assertEquals(SmartTriageEngine.triageFailure(new AssertionError("Expected true but was false"), false),
                SmartTriageEngine.TriageResult.APPLICATION_BUG);
    }

    @Test
    public void testTriageNeedsInvestigation() {
        // NullPointerException -> 0 flaky score -> APPLICATION_BUG
        Assert.assertEquals(SmartTriageEngine.triageFailure(new NullPointerException("NPE"), false),
                SmartTriageEngine.TriageResult.APPLICATION_BUG);
        
        // WebDriverException alone might not cross the 70 threshold (scores 40)
        Assert.assertEquals(SmartTriageEngine.triageFailure(new WebDriverException("unknown err"), false),
                SmartTriageEngine.TriageResult.NEEDS_INVESTIGATION);
    }
    
    @Test
    public void testRootCauseExtraction() {
        // Nested exception
        Throwable root = new ConnectException("Connection refused");
        Throwable runtime = new RuntimeException("wrapped", root);
        Throwable outer = new WebDriverException("outer", runtime);
        
        Assert.assertEquals(SmartTriageEngine.triageFailure(outer, false),
                SmartTriageEngine.TriageResult.SYSTEM_FLAKY); // Should extract ConnectException
    }
}
