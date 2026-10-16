package io.testfly.unit;

import io.testfly.config.TestFlyConfig;
import io.testfly.driver.DriverManager;
import io.testfly.internal.TestFlyContext;
import io.testfly.locator.Locator;
import io.testfly.locator.LocatorException;
import io.testfly.locator.Role;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * {@link Locator#withinEach(By)}: the chain is evaluated once per container, so
 * {@code nth()} / {@code last()} select a whole table column.
 *
 * <pre>
 * header: (th only)
 * row 1:  1  $2  Apple
 * row 2:  2  $3  Banana
 * row 3:  3  $4  Cherry
 * </pre>
 */
public class LocatorWithinEachTest {

    private static final By ROWS = By.cssSelector("tr");
    private static final By CELLS = By.cssSelector("td");

    /** Static mocks for one test; closed in {@code finally} so parallel methods never share them. */
    private static final class Table implements AutoCloseable {
        final WebDriver driver = mock(WebDriver.class, Mockito.withSettings().extraInterfaces(JavascriptExecutor.class));
        final TestFlyConfig.Locators locators = new TestFlyConfig.Locators();
        private final MockedStatic<DriverManager> driverManagerMock = mockStatic(DriverManager.class);
        private final MockedStatic<TestFlyContext> contextMock = mockStatic(TestFlyContext.class);

        Table() {
            driverManagerMock.when(DriverManager::getDriver).thenReturn(driver);
            locators.setSelfHealing(false);
            locators.setStrict(false);
            TestFlyConfig.Timeouts timeouts = new TestFlyConfig.Timeouts();
            timeouts.setExplicit(1);
            TestFlyConfig config = new TestFlyConfig();
            config.setLocators(locators);
            config.setTimeouts(timeouts);
            contextMock.when(TestFlyContext::getConfig).thenReturn(config);
            contextMock.when(TestFlyContext::getCurrentTestId).thenReturn("test-1");

            WebElement header = row(CELLS);
            WebElement row1 = row(CELLS, "1", "$2", "Apple");
            WebElement row2 = row(CELLS, "2", "$3", "Banana");
            WebElement row3 = row(CELLS, "3", "$4", "Cherry");
            when(driver.findElements(ROWS)).thenReturn(List.of(header, row1, row2, row3));
        }

        @Override
        public void close() {
            driverManagerMock.close();
            contextMock.close();
        }
    }

    /** A row whose {@code findElements(cellBy)} returns visible cells with the given texts. */
    private static WebElement row(By cellBy, String... texts) {
        WebElement row = mock(WebElement.class);
        List<WebElement> cells = new ArrayList<>();
        for (String text : texts) {
            WebElement cell = mock(WebElement.class);
            when(cell.getText()).thenReturn(text);
            when(cell.isDisplayed()).thenReturn(true);
            cells.add(cell);
        }
        when(row.findElements(cellBy)).thenReturn(cells);
        return row;
    }

    @Test
    public void nth_appliesPerContainer_selectingTheWholeColumn() {
        try (Table t = new Table()) {
            Locator column = Locator.of(CELLS).nth(2).withinEach(ROWS);
            assertEquals(column.allTexts(), List.of("Apple", "Banana", "Cherry"));
            assertEquals(column.count(), 3);
        }
    }

    @Test
    public void within_keepsItsFirstContainerBehaviour() {
        try (Table t = new Table()) {
            WebElement firstRow = row(CELLS, "1", "$2", "Apple");
            when(t.driver.findElement(ROWS)).thenReturn(firstRow);
            assertEquals(Locator.of(CELLS).nth(2).within(ROWS).allTexts(), List.of("Apple"));
        }
    }

    @Test
    public void last_appliesPerContainer() {
        try (Table t = new Table()) {
            assertEquals(Locator.of(CELLS).last().withinEach(ROWS).allTexts(), List.of("Apple", "Banana", "Cherry"));
        }
    }

    @Test
    public void first_appliesPerContainer() {
        try (Table t = new Table()) {
            assertEquals(Locator.of(CELLS).first().withinEach(ROWS).allTexts(), List.of("1", "2", "3"));
        }
    }

    @Test
    public void withoutNth_returnsEveryMatchInContainerOrder() {
        try (Table t = new Table()) {
            assertEquals(Locator.of(CELLS).withinEach(ROWS).count(), 9);
        }
    }

    @Test
    public void containersWithTooFewMatches_areSkipped() {
        try (Table t = new Table()) {
            assertEquals(Locator.of(CELLS).nth(5).withinEach(ROWS).count(), 0);
        }
    }

    @Test
    public void withText_filtersPerContainer() {
        try (Table t = new Table()) {
            assertEquals(Locator.of(CELLS).withText("$").withinEach(ROWS).allTexts(), List.of("$2", "$3", "$4"));
        }
    }

    @Test
    public void roleLocator_selectsColumnPerRow() {
        try (Table t = new Table()) {
            By cellRole = By.cssSelector(Role.CELL.cssSelector());
            WebElement row1 = row(cellRole, "1", "Apple");
            WebElement row2 = row(cellRole, "2", "Banana");
            By rowRole = By.cssSelector(Role.ROW.cssSelector());
            when(t.driver.findElements(rowRole)).thenReturn(List.of(row1, row2));

            assertEquals(Locator.byRole(Role.CELL).nth(1).withinEach(Locator.byRole(Role.ROW)).allTexts(),
                    List.of("Apple", "Banana"));
        }
    }

    @Test
    public void combinedWithWithin_searchesContainersInsideTheScope() {
        try (Table t = new Table()) {
            By table = By.id("fruits");
            WebElement tableEl = mock(WebElement.class);
            WebElement onlyRow = row(CELLS, "9", "$9", "Kiwi");
            when(t.driver.findElement(table)).thenReturn(tableEl);
            when(tableEl.findElements(ROWS)).thenReturn(List.of(onlyRow));

            assertEquals(Locator.of(CELLS).nth(2).withinEach(ROWS).within(table).allTexts(), List.of("Kiwi"));
        }
    }

    @Test
    public void strictMode_rejectsActionOnColumn_becauseNthIsPerContainer() {
        try (Table t = new Table()) {
            t.locators.setStrict(true);
            Locator column = Locator.of(CELLS).nth(2).withinEach(ROWS);
            LocatorException e = expectThrows(column::isVisible);
            assertTrue(e.getMessage().contains("Strict mode violation"), e.getMessage());
        }
    }

    @Test
    public void toString_includesWithinEach() {
        try (Table t = new Table()) {
            assertTrue(Locator.of(CELLS).nth(2).withinEach(ROWS).toString().contains("withinEach(" + ROWS + ")"));
        }
    }

    @Test
    public void withinEach_doesNotMutateOriginal() {
        try (Table t = new Table()) {
            Locator base = Locator.of(CELLS).nth(2);
            base.withinEach(ROWS);
            assertTrue(!base.toString().contains("withinEach"));
        }
    }

    @Test
    public void nullContainer_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> Locator.of(CELLS).withinEach(null));
    }

    private static LocatorException expectThrows(org.testng.Assert.ThrowingRunnable runnable) {
        return org.testng.Assert.expectThrows(LocatorException.class, runnable);
    }
}
