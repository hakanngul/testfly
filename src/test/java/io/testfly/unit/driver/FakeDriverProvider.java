package io.testfly.unit.driver;

import io.testfly.driver.NamedDriverProvider;
import org.openqa.selenium.WebDriver;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.mock;

/**
 * Test double for {@link NamedDriverProvider} that hands out Mockito
 * {@link WebDriver} instances and records every driver it created.
 *
 * <p>Register it with {@code DriverProviderRegistry.register(...)} under a unique
 * {@link #browserName()} and point {@code browser.name} at the same value.
 */
class FakeDriverProvider implements NamedDriverProvider {

    private final String browserName;
    private final List<WebDriver> created = new CopyOnWriteArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();

    FakeDriverProvider(String browserName) {
        this.browserName = browserName;
    }

    @Override
    public String browserName() {
        return browserName;
    }

    @Override
    public WebDriver createDriver() {
        calls.incrementAndGet();
        WebDriver driver = mock(WebDriver.class);
        created.add(driver);
        return driver;
    }

    int providerCalls() {
        return calls.get();
    }

    List<WebDriver> created() {
        return created;
    }

    WebDriver first() {
        return created.get(0);
    }

    WebDriver last() {
        return created.get(created.size() - 1);
    }
}
