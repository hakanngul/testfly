package io.testfly.integration.locator;

import com.sun.net.httpserver.HttpServer;
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
import java.util.List;

import static org.testng.Assert.assertEquals;

/**
 * Real-browser checks for {@link Locator#withinEach(By)} on an HTML table: a
 * column is selected per row, the header row is skipped, and the existing
 * {@link Locator#within(By)} still searches only the first container.
 *
 * <p>Excluded from the default {@code mvn test} run; execute with:
 *
 * <pre>
 * mvn verify -Preal-backends -Dit.test=WithinEachIntegrationTest
 * </pre>
 *
 * <p>Self-skips when no browser can be created.
 */
@Test(groups = {"integration"}, singleThreaded = true)
public class WithinEachIntegrationTest {

    private static final String TABLE = "<!DOCTYPE html><html><body>"
            + "<table id='fruits'>"
            + "<thead><tr><th>#</th><th>Price</th><th>Name</th></tr></thead>"
            + "<tbody>"
            + "<tr><td>1</td><td>$2</td><td>Apple</td></tr>"
            + "<tr><td>2</td><td>$3</td><td>Banana</td></tr>"
            + "<tr><td>3</td><td>$4</td><td>Cherry</td></tr>"
            + "</tbody></table>"
            + "<div role='grid' id='grid'>"
            + "<div role='row'><div role='gridcell'>a</div><div role='gridcell'>x</div></div>"
            + "<div role='row'><div role='gridcell'>b</div><div role='gridcell'>y</div></div>"
            + "</div>"
            + "</body></html>";

    private HttpServer server;
    private int port;
    private WebDriver driver;

    @BeforeClass
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/table", exchange -> {
            byte[] out = TABLE.getBytes(StandardCharsets.UTF_8);
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
        } catch (Throwable t) {
            System.out.println("[WithinEachIntegrationTest] Browser setup failed, tests will self-skip: "
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

    private void openTable() {
        if (driver == null) {
            throw new SkipException("No browser available — skipping withinEach integration test.");
        }
        driver.get("http://127.0.0.1:" + port + "/table");
    }

    public void roleCellNth_withinEachRow_selectsTheWholeColumn() {
        openTable();
        Locator column = Locator.byRole(Role.CELL).nth(2).withinEach(Locator.byRole(Role.ROW));
        assertEquals(column.allTexts(), List.of("Apple", "Banana", "Cherry"));
    }

    public void cssCells_withinEachRowOfOneTable() {
        openTable();
        Locator prices = Locator.cssSelector("td").nth(1).withinEach(By.cssSelector("tr")).within(By.id("fruits"));
        assertEquals(prices.allTexts(), List.of("$2", "$3", "$4"));
    }

    public void assertThat_hasTexts_onAColumn() {
        openTable();
        io.testfly.assertion.SeleniumAssert.assertThat(
                Locator.cssSelector("td").nth(1).withinEach(By.cssSelector("tr")).within(By.id("fruits")))
                .hasTexts("$2", "$3", "$4");
    }

    public void ariaGrid_columnPerRow() {
        openTable();
        Locator second = Locator.byRole(Role.CELL).last().withinEach(By.cssSelector("#grid [role='row']"));
        assertEquals(second.allTexts(), List.of("x", "y"));
    }

    public void within_stillUsesOnlyTheFirstContainer() {
        openTable();
        assertEquals(Locator.cssSelector("td").nth(2).within(By.cssSelector("tbody tr")).allTexts(),
                List.of("Apple"));
    }
}
