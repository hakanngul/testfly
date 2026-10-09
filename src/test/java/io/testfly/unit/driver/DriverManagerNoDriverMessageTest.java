package io.testfly.unit.driver;

import io.testfly.driver.DriverManager;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * ARCH-005 (option b): calling {@code getDriver()} from a TestNG configuration
 * method, before the test driver exists, must explain what to do instead.
 */
public class DriverManagerNoDriverMessageTest {

    @Test
    public void getDriverWithoutDriver_throwsActionableMessage() throws InterruptedException {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        // A fresh thread guarantees no driver is bound, whatever other tests did.
        Thread thread = new Thread(() -> {
            try {
                DriverManager.getDriver();
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        thread.start();
        thread.join();

        Throwable error = thrown.get();
        assertNotNull(error, "getDriver() must throw when no driver is bound");
        assertTrue(error instanceof IllegalStateException);
        String message = error.getMessage();
        assertTrue(message.contains("WebDriver not initialized"), message);
        assertTrue(message.contains("@BeforeMethod"), message);
        assertTrue(message.contains("@PreCondition"), message);
    }
}
