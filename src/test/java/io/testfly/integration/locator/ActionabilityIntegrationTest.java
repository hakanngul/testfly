package io.testfly.integration.locator;

import com.sun.net.httpserver.HttpServer;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.locator.Locator;
import io.testfly.locator.LocatorException;
import io.testfly.wait.NotActionableException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Real-browser checks for the actionability model: a click waits for an overlay to
 * disappear and for an animation to finish, scrolls an off-screen button into view,
 * and fails with a message naming the blocking element when it never becomes
 * clickable. Each page records timestamps so the tests prove the click came
 * <em>after</em> the page settled, not just that it happened.
 *
 * <p>Excluded from the default {@code mvn test} run; execute with:
 *
 * <pre>
 * mvn verify -Preal-backends -Dit.test=ActionabilityIntegrationTest
 * </pre>
 *
 * <p>Self-skips when no browser can be created.
 */
@Test(groups = {"integration"}, singleThreaded = true)
public class ActionabilityIntegrationTest {

    private static final String BUTTON_JS =
            "var c=0;function hit(){c++;document.getElementById('count').textContent=c;"
                    + "window.__clickedAt=performance.now();}";

    private HttpServer server;
    private int port;
    private WebDriver driver;
    private int originalTimeout;

    private static String page(String body, String script) {
        return "<!DOCTYPE html><html><head><style>body{margin:0;font-family:sans-serif}"
                + "@keyframes slide{from{transform:translateX(400px)}to{transform:translateX(0)}}"
                + "</style></head><body><span id='count'>0</span>" + body
                + "<script>" + BUTTON_JS + script + "</script></body></html>";
    }

    private void serve(String path, String html) {
        server.createContext(path, exchange -> {
            byte[] out = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
    }

    @BeforeClass
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        // A full-screen overlay that disappears after 500 ms.
        serve("/overlay", page(
                "<button id='btn' onclick='hit()' style='margin:40px'>Go</button>"
                        + "<div id='overlay' style='position:fixed;left:0;top:0;right:0;bottom:0;"
                        + "background:rgba(0,0,0,.3)'></div>",
                "setTimeout(function(){document.getElementById('overlay').remove();"
                        + "window.__settledAt=performance.now();},500);"));

        // A button that slides in over 600 ms.
        serve("/slide", page(
                "<button id='btn' onclick='hit()' style='margin:40px;animation:slide 600ms ease-out'>Go</button>",
                "document.getElementById('btn').addEventListener('animationend',"
                        + "function(){window.__settledAt=performance.now();});"));

        // A button far below the fold.
        serve("/far", page(
                "<div style='height:3000px'></div><button id='btn' onclick='hit()'>Far</button>", ""));

        // A button under a cover that never goes away.
        serve("/stuck", page(
                "<button id='btn' onclick='hit()' style='margin:40px'>Go</button>"
                        + "<div id='cover' style='position:fixed;left:0;top:0;right:0;bottom:0'></div>", ""));

        server.start();

        try {
            io.testfly.lifecycle.FrameworkBootstrap.initialize();
            DriverManager.createDriver();
            driver = DriverManager.getDriver();
            originalTimeout = TestFlyContext.getConfig().getTimeouts().getExplicit();
            TestFlyContext.getConfig().getTimeouts().setExplicit(3);
        } catch (Throwable t) {
            System.out.println("[ActionabilityIntegrationTest] Browser setup failed, tests will self-skip: "
                    + t.getMessage());
            driver = null;
        }
    }

    @AfterClass
    public void tearDown() {
        try {
            if (driver != null) {
                TestFlyContext.getConfig().getTimeouts().setExplicit(originalTimeout);
            }
        } catch (Exception ignored) {
        }
        try {
            DriverManager.quitDriver();
        } catch (Exception ignored) {
        }
        if (server != null) {
            server.stop(0);
        }
    }

    private void open(String path) {
        if (driver == null) {
            throw new SkipException("No browser available — skipping actionability integration test.");
        }
        driver.get("http://127.0.0.1:" + port + path);
    }

    private double num(String script) {
        Object v = ((JavascriptExecutor) driver).executeScript(script);
        return v instanceof Number n ? n.doubleValue() : Double.NaN;
    }

    private String count() {
        return Locator.cssSelector("#count").getText();
    }

    @Test
    public void click_waitsForOverlayToDisappear() {
        open("/overlay");

        Locator.cssSelector("#btn").click();

        assertEquals(count(), "1");
        assertTrue(num("return window.__clickedAt") >= num("return window.__settledAt"),
                "the click must happen after the overlay was removed");
    }

    @Test
    public void click_waitsForAnimationToFinish() {
        open("/slide");

        Locator.cssSelector("#btn").click();

        assertEquals(count(), "1");
        assertTrue(num("return window.__clickedAt") >= num("return window.__settledAt"),
                "the click must happen after the slide animation ended");
    }

    @Test
    public void click_scrollsOffscreenButtonIntoView() {
        open("/far");

        Locator.cssSelector("#btn").click();

        assertEquals(count(), "1");
        assertTrue(num("return window.scrollY") > 1000, "page should have scrolled to the button");
    }

    @Test
    public void click_whenCoveredForever_failsNamingTheBlocker() {
        open("/stuck");

        NotActionableException ex = expectThrows(NotActionableException.class,
                () -> Locator.cssSelector("#btn").click());

        assertTrue(ex.getMessage().contains("obscured by <div#cover>"), ex.getMessage());
        assertTrue(ex.getMessage().contains("not actionable for CLICK"), ex.getMessage());
        assertEquals(count(), "0");
    }

    @Test
    public void jsClick_goesThroughTheCover() {
        open("/stuck");

        Locator.cssSelector("#btn").jsClick();

        assertEquals(count(), "1");
    }

    @Test
    public void force_skipsTheCheck_butTheBrowserStillRefusesTheCoveredClick() {
        open("/stuck");

        LocatorException ex = expectThrows(LocatorException.class,
                () -> Locator.cssSelector("#btn").force().click());

        assertTrue(ex.getMessage().contains("3 attempts"), ex.getMessage());
        assertEquals(count(), "0");
    }

    @Test
    public void hover_alsoNeedsAHitTarget() {
        open("/stuck");

        NotActionableException ex = expectThrows(NotActionableException.class,
                () -> Locator.cssSelector("#btn").hover());

        assertTrue(ex.getMessage().contains("not actionable for HOVER"), ex.getMessage());
    }
}
