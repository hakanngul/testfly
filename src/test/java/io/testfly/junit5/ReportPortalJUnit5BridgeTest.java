package io.testfly.junit5;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link ReportPortalJUnit5Bridge}.
 *
 * <p>
 * The ReportPortal agent ({@code agent-java-junit5}) IS on the test classpath
 * for this project, so the bridge reports {@code isAvailable() == true} and
 * delegates lifecycle calls. When the RP server is not configured, the
 * delegated calls fail internally but the bridge catches exceptions and logs
 * warnings — no exception propagates to the caller.
 *
 * <p>
 * Placed in the {@code io.testfly.junit5} package to access the
 * package-private {@code ReportPortalJUnit5Bridge} class.
 */
@Test(singleThreaded = true)
public class ReportPortalJUnit5BridgeTest {

    // ----------------------------------------------------------
    // Constructor
    // ----------------------------------------------------------

    @Test
    public void constructor_doesNotThrow() {
        ReportPortalJUnit5Bridge bridge = new ReportPortalJUnit5Bridge();

        assertNotNull(bridge);
    }

    @Test
    public void isAvailable_returnsTrue_whenRpAgentOnClasspath() {
        ReportPortalJUnit5Bridge bridge = new ReportPortalJUnit5Bridge();

        // agent-java-junit5 is on the test classpath in this project
        assertTrue(bridge.isAvailable(),
                "RP bridge should report available when agent-java-junit5 is on classpath");
    }

    // ----------------------------------------------------------
    // Lifecycle delegation — methods must not throw even when
    // the RP server is not configured (exception is caught internally)
    // ----------------------------------------------------------

    @Test
    public void beforeAll_doesNotThrow_whenRpServerNotConfigured() {
        ReportPortalJUnit5Bridge bridge = new ReportPortalJUnit5Bridge();
        ExtensionContext mockContext = mock(ExtensionContext.class);

        // RP extension will fail internally (no server URL), but bridge catches it
        bridge.beforeAll(mockContext);
    }

    @Test
    public void beforeEach_doesNotThrow_whenRpServerNotConfigured() {
        ReportPortalJUnit5Bridge bridge = new ReportPortalJUnit5Bridge();
        ExtensionContext mockContext = mock(ExtensionContext.class);

        bridge.beforeEach(mockContext);
    }

    @Test
    public void afterTestExecution_doesNotThrow_whenRpServerNotConfigured() {
        ReportPortalJUnit5Bridge bridge = new ReportPortalJUnit5Bridge();
        ExtensionContext mockContext = mock(ExtensionContext.class);

        bridge.afterTestExecution(mockContext);
    }

    @Test
    public void testFailed_doesNotThrow_whenRpServerNotConfigured() {
        ReportPortalJUnit5Bridge bridge = new ReportPortalJUnit5Bridge();
        ExtensionContext mockContext = mock(ExtensionContext.class);

        bridge.testFailed(mockContext, new RuntimeException("test failure"));
    }

    @Test
    public void testSuccessful_doesNotThrow_whenRpServerNotConfigured() {
        ReportPortalJUnit5Bridge bridge = new ReportPortalJUnit5Bridge();
        ExtensionContext mockContext = mock(ExtensionContext.class);

        bridge.testSuccessful(mockContext);
    }

    @Test
    public void testDisabled_doesNotThrow_whenRpServerNotConfigured() {
        ReportPortalJUnit5Bridge bridge = new ReportPortalJUnit5Bridge();
        ExtensionContext mockContext = mock(ExtensionContext.class);

        bridge.testDisabled(mockContext, Optional.of("reason"));
    }

    @Test
    public void testDisabled_doesNotThrow_withEmptyReason() {
        ReportPortalJUnit5Bridge bridge = new ReportPortalJUnit5Bridge();
        ExtensionContext mockContext = mock(ExtensionContext.class);

        bridge.testDisabled(mockContext, Optional.empty());
    }

    @Test
    public void afterAll_doesNotThrow_whenRpServerNotConfigured() {
        ReportPortalJUnit5Bridge bridge = new ReportPortalJUnit5Bridge();
        ExtensionContext mockContext = mock(ExtensionContext.class);

        bridge.afterAll(mockContext);
    }

    // ----------------------------------------------------------
    // Full lifecycle — complete pass must not throw
    // ----------------------------------------------------------

    @Test
    public void fullLifecycle_failure_doesNotThrow_whenRpServerNotConfigured() {
        ReportPortalJUnit5Bridge bridge = new ReportPortalJUnit5Bridge();
        ExtensionContext mockContext = mock(ExtensionContext.class);
        Throwable cause = new AssertionError("failed assertion");

        assertTrue(bridge.isAvailable());

        // Run the entire lifecycle — none should throw even though RP server is not
        // configured
        bridge.beforeAll(mockContext);
        bridge.beforeEach(mockContext);
        bridge.testFailed(mockContext, cause);
        bridge.afterTestExecution(mockContext);
        bridge.afterAll(mockContext);
    }

    @Test
    public void fullLifecycle_success_doesNotThrow_whenRpServerNotConfigured() {
        ReportPortalJUnit5Bridge bridge = new ReportPortalJUnit5Bridge();
        ExtensionContext mockContext = mock(ExtensionContext.class);

        bridge.beforeAll(mockContext);
        bridge.beforeEach(mockContext);
        bridge.testSuccessful(mockContext);
        bridge.afterTestExecution(mockContext);
        bridge.afterAll(mockContext);
    }

    // ----------------------------------------------------------
    // Null context handling
    // ----------------------------------------------------------

    @Test
    public void beforeAll_doesNotThrow_withNullContext() {
        ReportPortalJUnit5Bridge bridge = new ReportPortalJUnit5Bridge();

        // The bridge should handle null gracefully via the invoke() method
        // which catches exceptions internally
        bridge.beforeAll(null);
    }
}
