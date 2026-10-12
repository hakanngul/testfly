package io.testfly.integration.accessibility;

import com.sun.net.httpserver.HttpServer;
import io.testfly.accessibility.AriaSnapshot;
import io.testfly.assertion.SeleniumAssert;
import io.testfly.driver.DriverManager;
import io.testfly.locator.Locator;
import io.testfly.locator.Role;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Real-browser checks for the accessibility assertions and ARIA snapshots: the probe script
 * runs in Chrome and the results are compared with what the page declares.
 *
 * <p>Excluded from the default {@code mvn test} run; execute with:
 *
 * <pre>
 * mvn verify -Preal-backends -Dit.test=AriaSnapshotIntegrationTest
 * </pre>
 *
 * <p>Self-skips when no browser can be created.
 */
@Test(groups = {"integration"}, singleThreaded = true)
public class AriaSnapshotIntegrationTest {

    private static final String PAGE = """
            <!DOCTYPE html><html><body>
            <header><nav aria-label="Main"><a href="/">Home</a><a href="/docs">Docs</a></nav></header>
            <main>
              <h1>Welcome</h1>
              <h2 aria-hidden="true">Hidden heading</h2>
              <p>Hello <b>world</b></p>
              <ul>
                <li><label><input type="checkbox" checked> Milk</label></li>
                <li><label><input type="checkbox"> Eggs</label></li>
              </ul>
              <label for="email">Email</label>
              <input id="email" type="email" aria-describedby="hint">
              <span id="hint">We never share it</span>
              <button disabled>Pay</button>
              <div style="display:none"><button>Never shown</button></div>
              <img alt="" src="data:image/gif;base64,R0lGODlhAQABAAAAACw=">
              <img alt="Logo" src="data:image/gif;base64,R0lGODlhAQABAAAAACw=">
            </main>
            <footer>Copyright</footer>
            </body></html>
            """;

    private HttpServer server;
    private WebDriver driver;

    @BeforeClass
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] out = PAGE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        try {
            io.testfly.lifecycle.FrameworkBootstrap.initialize();
            DriverManager.createDriver();
            driver = DriverManager.getDriver();
            driver.get("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        } catch (Throwable t) {
            System.out.println("[AriaSnapshotIntegrationTest] Browser setup failed, tests will self-skip: "
                    + t.getMessage());
            driver = null;
        }
    }

    @AfterClass
    public void tearDown() {
        try {
            DriverManager.quitDriver();
        } catch (Exception ignored) {
        }
        if (server != null) {
            server.stop(0);
        }
    }

    private void requireBrowser() {
        if (driver == null) {
            throw new SkipException("No browser available — skipping ARIA snapshot integration test.");
        }
    }

    @Test
    public void ariaSnapshot_ofNav_isExact() {
        requireBrowser();
        assertEquals(Locator.cssSelector("nav").ariaSnapshot(),
                "- navigation \"Main\":\n  - link \"Home\"\n  - link \"Docs\"\n");
    }

    @Test
    public void ariaSnapshot_ofList_showsCheckedStateAndLabelText() {
        requireBrowser();
        String yaml = Locator.cssSelector("ul").ariaSnapshot();
        assertTrue(yaml.startsWith("- list:\n  - listitem:\n    - checkbox \"Milk\" [checked]\n"), yaml);
        assertTrue(yaml.contains("- checkbox \"Eggs\"\n"), yaml);
        assertFalse(yaml.contains("\"Eggs\" [checked]"), yaml);
    }

    @Test
    public void ariaSnapshot_ofPage_skipsHiddenContentAndDecorativeImages() {
        requireBrowser();
        String yaml = AriaSnapshot.toYaml(AriaSnapshot.capture(driver, null));

        assertTrue(yaml.contains("- banner:"), yaml);
        assertTrue(yaml.contains("- main:"), yaml);
        assertTrue(yaml.contains("- contentinfo"), yaml);
        assertTrue(yaml.contains("- heading \"Welcome\" [level=1]"), yaml);
        assertFalse(yaml.contains("Hidden heading"), "aria-hidden content must be skipped: " + yaml);
        assertFalse(yaml.contains("Never shown"), "display:none content must be skipped: " + yaml);
        assertEquals(yaml.lines().filter(l -> l.trim().startsWith("- img")).count(), 1L,
                "only the image with alt text is reported: " + yaml);
        assertTrue(yaml.contains("- paragraph \"Hello world\""), yaml);
    }

    @Test
    public void generatedSnapshot_matchesItself() {
        requireBrowser();
        String yaml = Locator.cssSelector("main").ariaSnapshot();
        SeleniumAssert.assertThat(By.cssSelector("main")).matchesAriaSnapshot(yaml);
    }

    @Test
    public void matchesAriaSnapshot_partialTemplate_passes() {
        requireBrowser();
        SeleniumAssert.assertThat(By.cssSelector("main")).matchesAriaSnapshot("""
                - heading "Welcome" [level=1]
                - list:
                  - listitem:
                    - checkbox "Milk" [checked]
                - button "Pay" [disabled]
                """);
    }

    @Test
    public void matchesAriaSnapshot_wrongState_failsWithDiff() {
        requireBrowser();
        AssertionError e = expectThrows(AssertionError.class,
                () -> SeleniumAssert.assertThat(By.cssSelector("main")).within(Duration.ofSeconds(1))
                        .matchesAriaSnapshot("- list:\n  - listitem:\n    - checkbox \"Eggs\" [checked]"));
        assertTrue(e.getMessage().contains("to match ARIA snapshot"), e.getMessage());
        assertTrue(e.getMessage().contains("checkbox \"Eggs\" [checked]"), e.getMessage());
        assertTrue(e.getMessage().contains("--- actual (captured) ---"), e.getMessage());
    }

    @Test
    public void hasRole_readsImplicitRoles() {
        requireBrowser();
        SeleniumAssert.assertThat(By.cssSelector("nav")).hasRole(Role.NAVIGATION);
        SeleniumAssert.assertThat(By.cssSelector("h1")).hasRole(Role.HEADING);
        SeleniumAssert.assertThat(By.cssSelector("button")).hasRole(Role.BUTTON);
        SeleniumAssert.assertThat(By.id("email")).hasRole(Role.TEXTBOX);
        AssertionError e = expectThrows(AssertionError.class,
                () -> SeleniumAssert.assertThat(By.cssSelector("h1")).within(Duration.ofSeconds(1))
                        .hasRole(Role.BUTTON));
        assertTrue(e.getMessage().contains("actual: [heading]"), e.getMessage());
    }

    @Test
    public void hasAccessibleName_followsTheSamePrecedenceAsWithName() {
        requireBrowser();
        SeleniumAssert.assertThat(By.cssSelector("nav")).hasAccessibleName("Main");
        SeleniumAssert.assertThat(By.id("email")).hasAccessibleName("Email");
        SeleniumAssert.assertThat(By.cssSelector("img[alt='Logo']")).hasAccessibleName("Logo");
        // Locator.withName() resolves the same elements.
        assertEquals(Locator.byRole(Role.NAVIGATION).withName("Main").count(), 1);
        assertEquals(Locator.byLabel("Email").count(), 1);
    }

    @Test
    public void hasAccessibleDescription_readsAriaDescribedBy() {
        requireBrowser();
        SeleniumAssert.assertThat(By.id("email")).hasAccessibleDescription("We never share it");
    }
}
