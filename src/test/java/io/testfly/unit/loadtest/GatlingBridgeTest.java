package io.testfly.unit.loadtest;

import io.testfly.loadtest.internal.GatlingBridge;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests {@link GatlingBridge} classpath detection.
 *
 * <p>Since Gatling is an optional dependency and IS present on the test
 * classpath (via pom.xml), {@code isAvailable()} should return {@code true}
 * in this project's own test suite.
 */
@Test(singleThreaded = true)
public class GatlingBridgeTest {

    @Test
    public void testGatlingDetectedOnClasspath() {
        // Gatling is in pom.xml as optional — available during framework's own tests
        assertTrue(GatlingBridge.isAvailable(),
                "Gatling should be detected on the test classpath");
    }

    @Test
    public void testMissingDependencyMessage() {
        String msg = GatlingBridge.missingDependencyMessage();

        assertNotNull(msg);
        assertTrue(msg.contains("gatling-charts-highcharts"),
                "Message should mention the artifact name");
        assertTrue(msg.contains("engine: jdk"),
                "Message should suggest the JDK fallback");
        assertTrue(msg.contains("<dependency>"),
                "Message should include a Maven dependency snippet");
    }
}
