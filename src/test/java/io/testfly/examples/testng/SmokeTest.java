package io.testfly.examples.testng;

import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * Minimal smoke test used in the README quickstart.
 *
 * <p>Run explicitly with:
 * <pre>mvn test -Dtest=io.testfly.examples.testng.SmokeTest</pre>
 */
public class SmokeTest extends BaseTest {

    @Test
    public void opensThePage() {
        open();
        assertTrue(getDriver().getTitle().contains("Example Domain"));
    }
}
