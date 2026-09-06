package io.testfly.unit.ai;

import io.testfly.ai.DomPruner;
import org.mockito.Mockito;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import org.testng.annotations.Test;

public class DomPrunerTest {

    @Test
    public void prune_stripsScriptStyleSvgAndComments() {
        String html = """
            <html>
              <head>
                <script type="text/javascript">var secret = 123;</script>
                <style>body { background: red; }</style>
              </head>
              <body>
                <!-- navigation start -->
                <nav>
                  <svg width="24" height="24"><path d="M0 0h24v24H0z"/></svg>
                  <button id="login-btn" data-testid="submit-btn" type="submit">Sign In</button>
                </nav>
              </body>
            </html>
            """;

        String pruned = DomPruner.prune(html);

        Assert.assertFalse(pruned.contains("var secret"), "Should strip script content");
        Assert.assertFalse(pruned.contains("background: red"), "Should strip style content");
        Assert.assertFalse(pruned.contains("<svg"), "Should strip SVG elements");
        Assert.assertFalse(pruned.contains("navigation start"), "Should strip comments");
        Assert.assertTrue(pruned.contains("id=\"login-btn\""), "Should preserve element ID");
        Assert.assertTrue(pruned.contains("data-testid=\"submit-btn\""), "Should preserve data-testid");
        Assert.assertTrue(pruned.contains("Sign In"), "Should preserve button text");
    }

    @Test
    public void prune_stripsInlineStylesAndEventHandlers() {
        String html = """
            <div style="color: blue; padding: 10px" onclick="handleClick()">
              <input type="text" name="username" placeholder="Enter username" />
            </div>
            """;

        String pruned = DomPruner.prune(html);

        Assert.assertFalse(pruned.contains("style="), "Should strip style attribute");
        Assert.assertFalse(pruned.contains("onclick="), "Should strip event handlers");
        Assert.assertTrue(pruned.contains("name=\"username\""), "Should preserve input name");
        Assert.assertTrue(pruned.contains("placeholder=\"Enter username\""), "Should preserve placeholder");
    }

    @Test
    public void prune_respectsTokenBudget() {
        StringBuilder largeHtml = new StringBuilder("<div>");
        for (int i = 0; i < 2000; i++) {
            largeHtml.append("<span>Item ").append(i).append("</span> ");
        }
        largeHtml.append("</div>");

        // Set budget to 100 tokens (approx 400 chars)
        String pruned = DomPruner.prune(largeHtml.toString(), 100);

        Assert.assertTrue(pruned.length() <= 400 + 100, "Should truncate within budget");
        Assert.assertTrue(pruned.contains("[DOM truncated to fit token budget]"));
    }

    @Test
    public void prune_handlesNullAndEmpty() {
        Assert.assertEquals(DomPruner.prune((String) null), "");
        Assert.assertEquals(DomPruner.prune("   "), "");
        Assert.assertEquals(DomPruner.prune((WebDriver) null), "");
    }

    @Test
    public void prune_withWebDriverJavascriptExecutor_usesJsFastPath() {
        WebDriver driver = Mockito.mock(WebDriver.class, Mockito.withSettings().extraInterfaces(JavascriptExecutor.class));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        Mockito.when(js.executeScript(Mockito.anyString())).thenReturn("<button id='fast-path'>Fast</button>");

        String result = DomPruner.prune(driver);
        Assert.assertTrue(result.contains("id='fast-path'"));
        Assert.assertTrue(result.contains("Fast"));
    }

    @Test
    public void prune_withWebDriverFallback_usesPageSource() {
        WebDriver driver = Mockito.mock(WebDriver.class);
        Mockito.when(driver.getPageSource()).thenReturn("<button id='fallback-btn'>Fallback</button>");

        String result = DomPruner.prune(driver);
        Assert.assertTrue(result.contains("id='fallback-btn'"));
        Assert.assertTrue(result.contains("Fallback"));
    }
}
