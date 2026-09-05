package io.testfly.integration.db;

import io.testfly.config.TestFlyConfig;
import io.testfly.db.DbClient;
import io.testfly.db.DbConnectionFactory;
import io.testfly.db.DbAssertException;
import io.testfly.internal.TestFlyContext;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

import static org.testng.Assert.*;

/**
 * Integration tests for {@link DbClient} and {@link DbConnectionFactory}
 * using a real H2 in-memory database — no mocks required.
 *
 * <p>
 * Requires H2 on the test classpath (test scope).
 *
 * <p>
 * Run with:
 * 
 * <pre>
 * mvn verify -Preal-backends -Dit.test=DbClientIntegrationTest
 * </pre>
 */
@Test(singleThreaded = true)
public class DbClientIntegrationTest {

    private static final String H2_URL = "jdbc:h2:mem:testfly_integration;DB_CLOSE_DELAY=-1";
    private static final String H2_USER = "sa";
    private static final String H2_PASS = "";

    @BeforeClass
    public void setUp() throws Exception {
        // Initialize TestFlyContext with H2 config so DbConnectionFactory can resolve
        // it
        TestFlyConfig config = new TestFlyConfig();
        TestFlyConfig.Database db = new TestFlyConfig.Database();
        db.setUrl(H2_URL);
        db.setUsername(H2_USER);
        db.setPassword(H2_PASS);
        db.setDriver("org.h2.Driver");
        config.setDatabase(db);

        // Execution block required by config validation
        TestFlyConfig.Execution exec = new TestFlyConfig.Execution();
        exec.setMode("local");
        exec.setBaseUrl("http://localhost");
        config.setExecution(exec);

        TestFlyContext.setConfig(config);

        // Create test schema via direct H2 connection
        try (Connection conn = DriverManager.getConnection(H2_URL, H2_USER, H2_PASS);
                Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS users ("
                    + "id INT PRIMARY KEY AUTO_INCREMENT, "
                    + "email VARCHAR(255) NOT NULL, "
                    + "name VARCHAR(255), "
                    + "active BOOLEAN DEFAULT TRUE)");
            stmt.execute("DELETE FROM users");
            stmt.execute("INSERT INTO users (email, name, active) VALUES "
                    + "('alice@example.com', 'Alice', TRUE), "
                    + "('bob@example.com', 'Bob', TRUE), "
                    + "('charlie@example.com', 'Charlie', FALSE)");
        }
    }

    @AfterClass
    public void tearDown() {
        DbConnectionFactory.closeAll();
        TestFlyContext.reset();
        // Drop the in-memory database
        try (Connection conn = DriverManager.getConnection(H2_URL, H2_USER, H2_PASS);
                Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS users");
            stmt.execute("SHUTDOWN");
        } catch (Exception ignored) {
        }
    }

    // ── Connection factory tests ────────────────────────────────────────

    @Test(groups = { "integration" })
    public void connectionFactory_createsConnectionFromConfig() {
        Connection conn = DbConnectionFactory.getConnection(DbConnectionFactory.DEFAULT);
        assertNotNull(conn, "Factory should return a non-null connection");
        try {
            assertFalse(conn.isClosed(), "Connection should be open");
            assertTrue(conn.isValid(2), "Connection should be valid");
        } catch (Exception e) {
            fail("Connection health check failed: " + e.getMessage());
        }
    }

    @Test(groups = { "integration" })
    public void connectionFactory_reusesCachedConnection() {
        Connection first = DbConnectionFactory.getConnection(DbConnectionFactory.DEFAULT);
        Connection second = DbConnectionFactory.getConnection(DbConnectionFactory.DEFAULT);
        assertSame(first, second, "Factory should return the same cached connection on the same thread");
    }

    // ── Query execution tests ───────────────────────────────────────────

    @Test(groups = { "integration" })
    public void query_returnsResultsFromRealDatabase() {
        DbClient db = DbClient.forDefault();
        Object email = db.query("SELECT email FROM users WHERE name = ?", "Alice")
                .value("email");
        assertEquals(String.valueOf(email), "alice@example.com");
    }

    @Test(groups = { "integration" })
    public void assertRowExists_matchingRow_passes() {
        DbClient db = DbClient.forDefault();
        db.assertRowExists("users", Map.of("email", "alice@example.com"));
        // No exception = pass
    }

    @Test(groups = { "integration" }, expectedExceptions = DbAssertException.class)
    public void assertRowExists_noMatch_throws() {
        DbClient db = DbClient.forDefault();
        db.assertRowExists("users", Map.of("email", "nobody@example.com"));
    }

    @Test(groups = { "integration" })
    public void assertNoRow_noMatch_passes() {
        DbClient db = DbClient.forDefault();
        db.assertNoRow("users", Map.of("email", "nobody@example.com"));
    }

    @Test(groups = { "integration" })
    public void assertRowCount_correctCount_passes() {
        DbClient db = DbClient.forDefault();
        db.assertRowCount("users", 3);
    }

    @Test(groups = { "integration" }, expectedExceptions = DbAssertException.class)
    public void assertRowCount_wrongCount_throws() {
        DbClient db = DbClient.forDefault();
        db.assertRowCount("users", 999);
    }

    @Test(groups = { "integration" })
    public void scalar_returnsAggregateValue() {
        DbClient db = DbClient.forDefault();
        Object count = db.scalar("SELECT COUNT(*) FROM users WHERE active = ?", true);
        assertEquals(((Number) count).longValue(), 2L);
    }

    // ── Connection cleanup on failure ───────────────────────────────────

    @Test(groups = { "integration" })
    public void connectionCleanup_closeAllClosesConnections() throws Exception {
        // Get a connection through the factory
        Connection conn = DbConnectionFactory.getConnection(DbConnectionFactory.DEFAULT);
        assertFalse(conn.isClosed(), "Connection should be open before closeAll");

        // closeAll should close it
        DbConnectionFactory.closeAll();

        assertTrue(conn.isClosed(), "Connection should be closed after closeAll");

        // Re-initialize for subsequent tests by getting a new connection
        // (the factory will create a fresh one)
        Connection fresh = DbConnectionFactory.getConnection(DbConnectionFactory.DEFAULT);
        assertNotNull(fresh, "Factory should create a new connection after closeAll");
        assertFalse(fresh.isClosed(), "New connection should be open");
    }

    @Test(groups = { "integration" })
    public void connectionFactory_invalidDatasource_throwsClearError() {
        try {
            DbConnectionFactory.getConnection("nonexistent-datasource");
            fail("Should have thrown for unknown datasource");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("nonexistent-datasource")
                    || e.getMessage().contains("No database configuration"),
                    "Error should mention the datasource name or missing config. Got: " + e.getMessage());
        }
    }
}
