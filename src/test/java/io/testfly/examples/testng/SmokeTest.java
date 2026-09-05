package io.testfly.examples.testng;

import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

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
        assertThat(getDriver()).titleContains("Swag Labs");
    }
}
