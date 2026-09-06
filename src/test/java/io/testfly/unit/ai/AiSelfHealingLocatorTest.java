package io.testfly.unit.ai;

import io.testfly.ai.AiHealingEngine;
import io.testfly.ai.AiProvider;
import io.testfly.ai.AiProviderRegistry;
import io.testfly.config.TestFlyConfig;
import io.testfly.healing.HealLog;
import io.testfly.internal.TestFlyContext;
import org.mockito.Mockito;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class AiSelfHealingLocatorTest {

    private static final Object LOCK = TestFlyContext.class;

    @BeforeMethod
    public void setup() throws Exception {
        synchronized (LOCK) {
            resetContext();
            HealLog.clear();
        }
    }

    @AfterMethod
    public void tearDown() throws Exception {
        synchronized (LOCK) {
            resetContext();
            HealLog.clear();
        }
    }

    private static void resetContext() throws Exception {
        Field configField = TestFlyContext.class.getDeclaredField("CONFIG");
        configField.setAccessible(true);
        AtomicReference<?> ref = (AtomicReference<?>) configField.get(null);
        ref.set(null);
        TestFlyContext.clearCurrentTestId();
    }

    @Test
    public void parseHealedLocator_handlesVariousFormats() {
        // Standard JSON with CSS
        String jsonCss = "{\"type\": \"cssSelector\", \"value\": \"button.submit-v2\", \"confidence\": 0.9}";
        By byCss = AiHealingEngine.parseHealedLocator(jsonCss);
        Assert.assertEquals(byCss, By.cssSelector("button.submit-v2"));

        // Standard JSON with XPath
        String jsonXpath = "{\"type\": \"xpath\", \"value\": \"//button[text()='Giris']\"}";
        By byXpath = AiHealingEngine.parseHealedLocator(jsonXpath);
        Assert.assertEquals(byXpath, By.xpath("//button[text()='Giris']"));

        // JSON with ID
        String jsonId = "{\"type\": \"id\", \"value\": \"new-btn-id\"}";
        By byId = AiHealingEngine.parseHealedLocator(jsonId);
        Assert.assertEquals(byId, By.id("new-btn-id"));

        // Markdown code fence wrapped
        String fenced = "```json\n{\"type\": \"name\", \"value\": \"password_confirm\"}\n```";
        By byName = AiHealingEngine.parseHealedLocator(fenced);
        Assert.assertEquals(byName, By.name("password_confirm"));

        // Invalid JSON or missing values
        Assert.assertNull(AiHealingEngine.parseHealedLocator(null));
        Assert.assertNull(AiHealingEngine.parseHealedLocator(""));
        Assert.assertNull(AiHealingEngine.parseHealedLocator("invalid json string"));
        Assert.assertNull(AiHealingEngine.parseHealedLocator("{\"type\": \"id\"}"));
    }

    @Test
    public void buildPrompt_containsOriginalLocatorAndDom() {
        String prompt = AiHealingEngine.buildPrompt(
                "By.id: old-btn",
                "https://test.com/login",
                "Login Page",
                "<button class='new-btn'>Sign In</button>"
        );

        Assert.assertTrue(prompt.contains("By.id: old-btn"));
        Assert.assertTrue(prompt.contains("https://test.com/login"));
        Assert.assertTrue(prompt.contains("Login Page"));
        Assert.assertTrue(prompt.contains("<button class='new-btn'>Sign In</button>"));
        Assert.assertTrue(prompt.contains("\"type\""));
    }

    @Test
    public void heal_whenAiHealingDisabled_returnsNullWithoutCallingAi() {
        synchronized (LOCK) {
            TestFlyConfig config = new TestFlyConfig();
            TestFlyConfig.Locators locators = new TestFlyConfig.Locators();
            locators.setAiHealing(false);
            config.setLocators(locators);
            TestFlyContext.initialize(config);

            WebDriver driver = Mockito.mock(WebDriver.class);
            WebElement healed = AiHealingEngine.heal(driver, By.id("missing-btn"), "test_01");

            Assert.assertNull(healed);
            Mockito.verifyNoInteractions(driver);
        }
    }

    @Test
    public void heal_whenAiEnabledAndElementFound_returnsElementAndRecordsHeal() {
        synchronized (LOCK) {
            TestFlyConfig config = new TestFlyConfig();
            TestFlyConfig.Locators locators = new TestFlyConfig.Locators();
            locators.setAiHealing(true);
            config.setLocators(locators);

            TestFlyConfig.Ai ai = new TestFlyConfig.Ai();
            ai.setProvider("mock-ai");
            ai.setApiKey("mock-key");
            ai.setModel("mock-model");
            config.setAi(ai);
            TestFlyContext.initialize(config);

            // Register mock AI provider
            AiProvider mockProvider = Mockito.mock(AiProvider.class);
            Mockito.when(mockProvider.name()).thenReturn("mock-ai");
            Mockito.when(mockProvider.call(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyInt()))
                    .thenReturn("{\"type\": \"cssSelector\", \"value\": \".healed-button\"}");
            AiProviderRegistry.register(mockProvider);

            // Mock WebDriver and visible WebElement
            WebDriver driver = Mockito.mock(WebDriver.class);
            Mockito.when(driver.getPageSource()).thenReturn("<html><body><button class='healed-button'>OK</button></body></html>");
            Mockito.when(driver.getCurrentUrl()).thenReturn("https://example.com");
            Mockito.when(driver.getTitle()).thenReturn("Example");

            WebElement mockElement = Mockito.mock(WebElement.class);
            Mockito.when(mockElement.isDisplayed()).thenReturn(true);
            Mockito.when(driver.findElements(By.cssSelector(".healed-button"))).thenReturn(List.of(mockElement));

            WebElement healed = AiHealingEngine.heal(driver, By.id("old-button"), "test_healed_success");

            Assert.assertNotNull(healed);
            Assert.assertEquals(healed, mockElement);
            Assert.assertEquals(HealLog.getAll().size(), 1);
            Assert.assertEquals(HealLog.getAll().get(0).getStrategy(), "ai-healed");
            Assert.assertEquals(HealLog.getAll().get(0).getHealedLocator(), "By.cssSelector: .healed-button");
        }
    }

    @Test
    public void heal_whenAiSuggestedElementNotVisible_returnsNull() {
        synchronized (LOCK) {
            TestFlyConfig config = new TestFlyConfig();
            TestFlyConfig.Locators locators = new TestFlyConfig.Locators();
            locators.setAiHealing(true);
            config.setLocators(locators);

            TestFlyConfig.Ai ai = new TestFlyConfig.Ai();
            ai.setProvider("mock-ai-hidden");
            ai.setApiKey("mock-key");
            config.setAi(ai);
            TestFlyContext.initialize(config);

            AiProvider mockProvider = Mockito.mock(AiProvider.class);
            Mockito.when(mockProvider.name()).thenReturn("mock-ai-hidden");
            Mockito.when(mockProvider.call(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyInt()))
                    .thenReturn("{\"type\": \"id\", \"value\": \"hidden-btn\"}");
            AiProviderRegistry.register(mockProvider);

            WebDriver driver = Mockito.mock(WebDriver.class);
            Mockito.when(driver.getPageSource()).thenReturn("<html><body><button id='hidden-btn'>Hidden</button></body></html>");

            WebElement hiddenElement = Mockito.mock(WebElement.class);
            Mockito.when(hiddenElement.isDisplayed()).thenReturn(false);
            Mockito.when(driver.findElements(By.id("hidden-btn"))).thenReturn(List.of(hiddenElement));

            WebElement healed = AiHealingEngine.heal(driver, By.id("old-button"), "test_hidden");

            Assert.assertNull(healed, "Should not return non-visible healed element");
            Assert.assertEquals(HealLog.getAll().size(), 0);
        }
    }

    @Test
    public void heal_whenAiProviderThrowsException_failsGracefully() {
        synchronized (LOCK) {
            TestFlyConfig config = new TestFlyConfig();
            TestFlyConfig.Locators locators = new TestFlyConfig.Locators();
            locators.setAiHealing(true);
            config.setLocators(locators);

            TestFlyConfig.Ai ai = new TestFlyConfig.Ai();
            ai.setProvider("mock-ai-error");
            ai.setApiKey("mock-key");
            config.setAi(ai);
            TestFlyContext.initialize(config);

            AiProvider mockProvider = Mockito.mock(AiProvider.class);
            Mockito.when(mockProvider.name()).thenReturn("mock-ai-error");
            Mockito.when(mockProvider.call(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyInt()))
                    .thenThrow(new RuntimeException("Simulated AI connection timeout"));
            AiProviderRegistry.register(mockProvider);

            WebDriver driver = Mockito.mock(WebDriver.class);
            Mockito.when(driver.getPageSource()).thenReturn("<html><body><div>Page</div></body></html>");

            // Should not throw, should return null gracefully
            WebElement healed = AiHealingEngine.heal(driver, By.id("old-button"), "test_error");
            Assert.assertNull(healed);
        }
    }
}
