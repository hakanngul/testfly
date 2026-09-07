package io.testfly.unit.assertion.ai;

import io.testfly.ai.AiProvider;
import io.testfly.ai.AiProviderRegistry;
import io.testfly.assertion.LocatorAssert;
import io.testfly.assertion.PageAssert;
import io.testfly.assertion.SeleniumAssert;
import io.testfly.assertion.SoftAssertions;
import io.testfly.assertion.ai.AiAssertEngine;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.steps.StepLogger;
import io.testfly.test.support.AssertionSupport;
import org.mockito.MockedStatic;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Unit tests for AI semantic assertions in {@link AiAssertEngine},
 * {@link PageAssert}, {@link LocatorAssert}, and {@link AssertionSupport}.
 */
@Test(singleThreaded = true)
public class AiAssertionTest {

    private WebDriver mockDriver;
    private AiProvider mockAiProvider;
    private MockedStatic<DriverManager> driverManagerMock;
    private MockedStatic<TestFlyContext> contextMock;
    private MockedStatic<StepLogger> stepLoggerMock;
    private MockedStatic<AiProviderRegistry> providerRegistryMock;
    private TestFlyConfig config;

    @BeforeMethod
    public void setUp() {
        mockDriver = mock(WebDriver.class);
        when(mockDriver.getCurrentUrl()).thenReturn("https://example.com/checkout");
        when(mockDriver.getTitle()).thenReturn("Checkout Success");
        when(mockDriver.getPageSource()).thenReturn("<main><div class='success'>Order Confirmed #12345</div></main>");

        driverManagerMock = mockStatic(DriverManager.class);
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockDriver);

        config = new TestFlyConfig();
        TestFlyConfig.Ai ai = new TestFlyConfig.Ai();
        ai.setProvider("mock-ai");
        ai.setApiKey("test-key-123");
        ai.setModel("mock-model");
        ai.setTimeoutSeconds(10);
        config.setAi(ai);

        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setExplicit(2);
        config.setTimeouts(timeouts);

        contextMock = mockStatic(TestFlyContext.class);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);

        stepLoggerMock = mockStatic(StepLogger.class);

        mockAiProvider = mock(AiProvider.class);
        providerRegistryMock = mockStatic(AiProviderRegistry.class);
        providerRegistryMock.when(() -> AiProviderRegistry.get(anyString(), nullable(String.class)))
                .thenReturn(mockAiProvider);

        SoftAssertions.clear();
    }

    @AfterMethod
    public void tearDown() {
        if (driverManagerMock != null) driverManagerMock.close();
        if (contextMock != null) contextMock.close();
        if (stepLoggerMock != null) stepLoggerMock.close();
        if (providerRegistryMock != null) providerRegistryMock.close();
        SoftAssertions.clear();
    }

    @Test
    public void testBuildPromptAndParseResult() {
        String prompt = AiAssertEngine.buildPrompt("https://test.com", "Test Page", "<div>Hello</div>", "Greeting visible", true);
        assertTrue(prompt.contains("https://test.com"));
        assertTrue(prompt.contains("Greeting visible"));
        assertTrue(prompt.contains("SATISFIES this condition"));

        String negativePrompt = AiAssertEngine.buildPrompt("https://test.com", "Test Page", "<div>Hello</div>", "Error alert", false);
        assertTrue(negativePrompt.contains("VIOLATES this condition"));

        String validJson = "```json\n{\n  \"passed\": true,\n  \"confidence\": 0.98,\n  \"reason\": \"Matches perfectly\"\n}\n```";
        AiAssertEngine.AiAssertionResult result = AiAssertEngine.parseResult(validJson, true);
        assertTrue(result.isPassed());
        assertEquals(result.confidence(), 0.98, 0.001);
        assertEquals(result.reason(), "Matches perfectly");

        AiAssertEngine.AiAssertionResult invalid = AiAssertEngine.parseResult("Not a json", true);
        assertFalse(invalid.isPassed());
    }

    @Test
    public void testPageSatisfiesAiPasses() {
        when(mockAiProvider.call(anyString(), nullable(String.class), anyString(), anyInt()))
                .thenReturn("{\"passed\": true, \"confidence\": 0.95, \"reason\": \"Order confirmed message found\"}");

        PageAssert pageAssert = SeleniumAssert.assertThat(mockDriver);
        pageAssert.satisfiesAi("Order confirmation message displayed");
    }

    @Test
    public void testPageSatisfiesAiFails() {
        when(mockAiProvider.call(anyString(), nullable(String.class), anyString(), anyInt()))
                .thenReturn("{\"passed\": false, \"confidence\": 0.90, \"reason\": \"No order confirmation message\"}");

        PageAssert pageAssert = SeleniumAssert.assertThat(mockDriver);
        try {
            pageAssert.satisfiesAi("Order confirmation message displayed");
            fail("Expected AssertionError on AI assertion failure");
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("Expected page to satisfy AI condition"));
            assertTrue(e.getMessage().contains("No order confirmation message"));
        }
    }

    @Test
    public void testPageViolatesAiPassesAndFails() {
        // When check succeeds (no violation)
        when(mockAiProvider.call(anyString(), nullable(String.class), anyString(), anyInt()))
                .thenReturn("{\"passed\": true, \"confidence\": 0.95, \"reason\": \"No errors detected\"}");

        PageAssert pageAssert = SeleniumAssert.assertThat(mockDriver);
        pageAssert.violatesAi("Error 500 banner");

        // When violation is detected
        when(mockAiProvider.call(anyString(), nullable(String.class), anyString(), anyInt()))
                .thenReturn("{\"passed\": false, \"confidence\": 0.95, \"reason\": \"Found critical error 500 on screen\"}");

        try {
            pageAssert.violatesAi("Error 500 banner");
            fail("Expected AssertionError on AI violation");
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("Expected page to not violate AI condition"));
            assertTrue(e.getMessage().contains("Found critical error 500"));
        }
    }

    @Test
    public void testPageSatisfiesAiSoftAssertion() {
        when(mockAiProvider.call(anyString(), nullable(String.class), anyString(), anyInt()))
                .thenReturn("{\"passed\": false, \"confidence\": 0.85, \"reason\": \"Total mismatch\"}");

        // Soft assertion does not throw immediately
        SeleniumAssert.softAssert(mockDriver).satisfiesAi("Total price is $100");

        assertTrue(SoftAssertions.get().hasFailed());
        assertFalse(SoftAssertions.get().getFailures().isEmpty());
        assertTrue(SoftAssertions.get().getFailures().get(0).contains("Total mismatch"));
    }

    @Test
    public void testLocatorSatisfiesAiPasses() {
        WebElement mockElement = mock(WebElement.class);
        when(mockElement.getAttribute("outerHTML")).thenReturn("<div id='badge' class='vip'>Gold Member</div>");
        when(mockDriver.findElements(By.id("badge"))).thenReturn(List.of(mockElement));

        when(mockAiProvider.call(anyString(), nullable(String.class), anyString(), anyInt()))
                .thenReturn("{\"passed\": true, \"confidence\": 0.99, \"reason\": \"VIP Gold badge confirmed\"}");

        LocatorAssert locatorAssert = SeleniumAssert.assertThat(By.id("badge"));
        locatorAssert.satisfiesAi("VIP member status badge");
    }

    @Test
    public void testLocatorViolatesAiFails() {
        WebElement mockElement = mock(WebElement.class);
        when(mockElement.getAttribute("outerHTML")).thenReturn("<div class='warning'>Out of stock</div>");
        when(mockDriver.findElements(By.className("warning"))).thenReturn(List.of(mockElement));

        when(mockAiProvider.call(anyString(), nullable(String.class), anyString(), anyInt()))
                .thenReturn("{\"passed\": false, \"confidence\": 0.95, \"reason\": \"Element contains out of stock warning\"}");

        LocatorAssert locatorAssert = SeleniumAssert.assertThat(By.className("warning"));
        try {
            locatorAssert.violatesAi("Out of stock or unavailable notice");
            fail("Expected AssertionError on locator AI violation");
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains("Expected element"));
            assertTrue(e.getMessage().contains("not violate AI condition"));
        }
    }

    @Test
    public void testAssertionSupportHelper() {
        when(mockAiProvider.call(anyString(), nullable(String.class), anyString(), anyInt()))
                .thenReturn("{\"passed\": true, \"confidence\": 0.95, \"reason\": \"Order confirmed\"}");

        AssertionSupport support = new AssertionSupport() {};
        PageAssert pageAssert = support.assertWithAi("Order confirmed message");
        assertNotNull(pageAssert);
    }
}
