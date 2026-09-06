package io.testfly.unit.agent;

import io.testfly.agent.ActionCache;
import io.testfly.agent.ActionCompiler;
import io.testfly.agent.ActionExecutor;
import io.testfly.agent.ActionPlan;
import io.testfly.agent.ActionStep;
import io.testfly.agent.ActionType;
import io.testfly.ai.AiProvider;
import io.testfly.ai.AiProviderRegistry;
import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.locator.Locator;
import io.testfly.steps.StepLogger;
import io.testfly.test.support.ActionSupport;
import io.testfly.test.support.LocatorSupport;
import org.mockito.MockedStatic;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for Sprint 4 Goal-Oriented Dynamic Steps (ActionCompiler, ActionExecutor, ActionCache).
 */
@Test(singleThreaded = true)
public class ActionAgentTest {

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
        when(mockDriver.getCurrentUrl()).thenReturn("https://shop.example.com/cart");
        when(mockDriver.getTitle()).thenReturn("Shopping Cart");
        when(mockDriver.getPageSource()).thenReturn("<div class='cart'><button class='remove-item'>Remove</button></div>");

        driverManagerMock = mockStatic(DriverManager.class);
        driverManagerMock.when(DriverManager::getDriver).thenReturn(mockDriver);

        config = new TestFlyConfig();
        TestFlyConfig.Ai ai = new TestFlyConfig.Ai();
        ai.setProvider("mock-ai");
        ai.setApiKey("test-key-123");
        ai.setModel("mock-model");
        ai.setTimeoutSeconds(10);
        ai.setActionCache(true);
        config.setAi(ai);

        TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
        timeouts.setExplicit(1);
        config.setTimeouts(timeouts);

        contextMock = mockStatic(TestFlyContext.class);
        contextMock.when(TestFlyContext::getConfig).thenReturn(config);

        stepLoggerMock = mockStatic(StepLogger.class);

        mockAiProvider = mock(AiProvider.class);
        providerRegistryMock = mockStatic(AiProviderRegistry.class);
        providerRegistryMock.when(() -> AiProviderRegistry.get(anyString(), nullable(String.class)))
                .thenReturn(mockAiProvider);

        ActionCache.clear();
    }

    @AfterMethod
    public void tearDown() {
        if (driverManagerMock != null) driverManagerMock.close();
        if (contextMock != null) contextMock.close();
        if (stepLoggerMock != null) stepLoggerMock.close();
        if (providerRegistryMock != null) providerRegistryMock.close();
        ActionCache.clear();
    }

    @Test
    public void testParseLocator() {
        By css = ActionExecutor.parseLocator(".submit-btn");
        assertTrue(css.toString().contains(".submit-btn"));

        By xpath = ActionExecutor.parseLocator("//button[@id='save']");
        assertTrue(xpath.toString().contains("//button[@id='save']"));

        By xpathParen = ActionExecutor.parseLocator("(//input)[1]");
        assertTrue(xpathParen.toString().contains("(//input)[1]"));
    }

    @Test
    public void testActionCacheOperations() {
        String url = "https://example.com/cart";
        String goal = "Remove first item";

        assertNull(ActionCache.get(url, goal));

        ActionStep step = new ActionStep(ActionType.CLICK, ".remove-item", null, "Click remove");
        ActionPlan plan = new ActionPlan(goal, "/cart", List.of(step), System.currentTimeMillis());

        ActionCache.put(url, goal, plan);
        assertNotNull(ActionCache.get(url, goal));
        assertEquals(ActionCache.get(url, goal).steps().size(), 1);

        ActionCache.invalidate(url, goal);
        assertNull(ActionCache.get(url, goal));
    }

    @Test
    public void testActionCompilerBuildPromptAndParse() {
        String prompt = ActionCompiler.buildPrompt("https://shop.com/cart", "Cart", "<button>Del</button>", "Delete item");
        assertTrue(prompt.contains("Delete item"));
        assertTrue(prompt.contains("CLICK"));
        assertTrue(prompt.contains("https://shop.com/cart"));

        String rawJson = """
                ```json
                {
                  "goal": "Delete item",
                  "steps": [
                    {
                      "action": "CLICK",
                      "locator": ".btn-delete",
                      "value": null,
                      "description": "Click delete button"
                    },
                    {
                      "action": "TYPE",
                      "locator": "#coupon",
                      "value": "DISCOUNT10",
                      "description": "Enter coupon code"
                    }
                  ]
                }
                ```
                """;

        ActionPlan plan = ActionCompiler.parsePlan(rawJson, "Delete item", "/cart");
        assertEquals(plan.goal(), "Delete item");
        assertEquals(plan.steps().size(), 2);
        assertEquals(plan.steps().get(0).action(), ActionType.CLICK);
        assertEquals(plan.steps().get(0).locator(), ".btn-delete");
        assertEquals(plan.steps().get(1).action(), ActionType.TYPE);
        assertEquals(plan.steps().get(1).value(), "DISCOUNT10");
    }

    @Test
    public void testCompileAndFreezeCaching() {
        when(mockAiProvider.call(anyString(), nullable(String.class), anyString(), anyInt()))
                .thenReturn("""
                        {
                          "goal": "Delete item",
                          "steps": [
                            {"action": "CLICK", "locator": ".remove-item", "value": null, "description": "Remove"}
                          ]
                        }
                        """);

        // 1. First call: Cache miss -> calls AI provider
        ActionPlan plan1 = ActionCompiler.compile(mockDriver, "Delete item");
        assertNotNull(plan1);
        assertEquals(plan1.steps().size(), 1);
        verify(mockAiProvider, times(1)).call(anyString(), nullable(String.class), anyString(), anyInt());

        // 2. Second call: Cache HIT -> returns cached plan with ZERO AI calls
        ActionPlan plan2 = ActionCompiler.compile(mockDriver, "Delete item");
        assertNotNull(plan2);
        assertEquals(plan2.steps().size(), 1);
        assertEquals(plan2.steps().get(0).locator(), ".remove-item");
        verify(mockAiProvider, times(1)).call(anyString(), nullable(String.class), anyString(), anyInt());
    }

    @Test
    public void testActionSupportActDelegation() {
        when(mockAiProvider.call(anyString(), nullable(String.class), anyString(), anyInt()))
                .thenReturn("""
                        {
                          "goal": "Search phone",
                          "steps": [
                            {"action": "TYPE", "locator": "#search", "value": "Pixel", "description": "Type query"},
                            {"action": "CLICK", "locator": "#search-btn", "value": null, "description": "Click search"}
                          ]
                        }
                        """);

        WebElement searchInput = mock(WebElement.class);
        when(searchInput.isDisplayed()).thenReturn(true);
        when(mockDriver.findElement(By.cssSelector("#search"))).thenReturn(searchInput);
        when(mockDriver.findElements(By.cssSelector("#search"))).thenReturn(List.of(searchInput));

        WebElement searchBtn = mock(WebElement.class);
        when(searchBtn.isDisplayed()).thenReturn(true);
        when(searchBtn.isEnabled()).thenReturn(true);
        when(mockDriver.findElement(By.cssSelector("#search-btn"))).thenReturn(searchBtn);
        when(mockDriver.findElements(By.cssSelector("#search-btn"))).thenReturn(List.of(searchBtn));

        ActionSupport support = new ActionSupport() {};
        support.act("Search phone");

        verify(searchInput).clear();
        verify(searchInput).sendKeys("Pixel");
        verify(searchBtn).click();
    }

    @Test
    public void testLocatorSupportByIntent() {
        when(mockAiProvider.call(anyString(), nullable(String.class), anyString(), anyInt()))
                .thenReturn("""
                        {
                          "goal": "Find checkout button",
                          "steps": [
                            {"action": "CLICK", "locator": "button.checkout-now", "value": null, "description": "Checkout"}
                          ]
                        }
                        """);

        LocatorSupport support = new LocatorSupport() {};
        Locator loc = support.byIntent("checkout button");
        assertNotNull(loc);
        assertTrue(loc.toString().contains("checkout-now"));
    }
}
