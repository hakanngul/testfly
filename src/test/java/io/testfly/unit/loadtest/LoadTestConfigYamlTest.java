package io.testfly.unit.loadtest;

import io.testfly.config.TestFlyConfig;
import org.testng.annotations.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.StringReader;

import static org.testng.Assert.*;

/**
 * Tests YAML binding for the {@code loadtest:} config block.
 */
@Test(singleThreaded = true)
public class LoadTestConfigYamlTest {

    private TestFlyConfig.LoadTest parse(String yaml) {
        String full = "browser:\n  name: chrome\nexecution:\n  mode: local\n  baseUrl: http://localhost\ntimeouts:\n  explicit: 10\n  pageLoad: 30\n" + yaml;
        LoaderOptions opts = new LoaderOptions();
        Constructor ctor = new Constructor(TestFlyConfig.class, opts);
        Yaml y = new Yaml(ctor);
        TestFlyConfig config = y.load(new StringReader(full));
        return config.getLoadTest();
    }

    @Test
    public void testDefaultsWhenBlockAbsent() {
        TestFlyConfig.LoadTest lt = parse("");
        assertNull(lt, "loadtest block should be null when absent from YAML");
    }

    @Test
    public void testFullBlock() {
        TestFlyConfig.LoadTest lt = parse(
                "loadtest:\n" +
                "  enabled: true\n" +
                "  baseUrl: https://api.example.com\n" +
                "  engine: gatling\n" +
                "  users: 200\n" +
                "  rampUp: 30s\n" +
                "  hold: 2m\n" +
                "  cooldown: 10s\n" +
                "  maxUsers: 500\n" +
                "  resultsDir: target/lt\n" +
                "  reportEnabled: false\n" +
                "  requestTimeoutSeconds: 60\n");

        assertNotNull(lt);
        assertTrue(lt.isEnabled());
        assertEquals(lt.getBaseUrl(), "https://api.example.com");
        assertEquals(lt.getEngine(), "gatling");
        assertEquals(lt.getUsers(), 200);
        assertEquals(lt.getRampUp(), "30s");
        assertEquals(lt.getHold(), "2m");
        assertEquals(lt.getCooldown(), "10s");
        assertEquals(lt.getMaxUsers(), 500);
        assertEquals(lt.getResultsDir(), "target/lt");
        assertFalse(lt.isReportEnabled());
        assertEquals(lt.getRequestTimeoutSeconds(), 60);
    }

    @Test
    public void testLowercaseAliasKey() {
        // SnakeYAML binds "loadtest" (all lowercase) via setLoadtest() alias
        TestFlyConfig.LoadTest lt = parse(
                "loadtest:\n" +
                "  users: 42\n");

        assertNotNull(lt);
        assertEquals(lt.getUsers(), 42);
    }

    @Test
    public void testDefaultValues() {
        TestFlyConfig.LoadTest lt = parse("loadtest:\n  enabled: true\n");

        assertNotNull(lt);
        assertTrue(lt.isEnabled());
        assertEquals(lt.getUsers(), 10);
        assertEquals(lt.getRampUp(), "10s");
        assertEquals(lt.getHold(), "30s");
        assertEquals(lt.getCooldown(), "5s");
        assertEquals(lt.getEngine(), "auto");
        assertEquals(lt.getMaxUsers(), 1000);
        assertEquals(lt.getResultsDir(), "target/loadtest");
        assertTrue(lt.isReportEnabled());
        assertEquals(lt.getRequestTimeoutSeconds(), 30);
    }

    @Test
    public void testNullNormalization() {
        TestFlyConfig.LoadTest lt = new TestFlyConfig.LoadTest();
        lt.setEngine(null);
        assertEquals(lt.getEngine(), "auto");

        lt.setRampUp(null);
        assertEquals(lt.getRampUp(), "10s");

        lt.setHold(null);
        assertEquals(lt.getHold(), "30s");

        lt.setCooldown(null);
        assertEquals(lt.getCooldown(), "5s");

        lt.setResultsDir(null);
        assertEquals(lt.getResultsDir(), "target/loadtest");

        lt.setUsers(0);
        assertEquals(lt.getUsers(), 10);

        lt.setMaxUsers(-1);
        assertEquals(lt.getMaxUsers(), 1000);

        lt.setRequestTimeoutSeconds(0);
        assertEquals(lt.getRequestTimeoutSeconds(), 30);
    }
}
