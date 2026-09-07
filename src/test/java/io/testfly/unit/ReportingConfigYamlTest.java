package io.testfly.unit;

import io.testfly.config.TestFlyConfig;
import org.testng.annotations.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.testng.Assert.*;

/**
 * Verifies that the {@code reporting} section of {@code testfly.yml} accepts
 * both the documented nested format and the legacy flat format for Allure,
 * and that ReportPortal accepts both {@code reportPortal} and the
 * all-lowercase {@code reportportal} YAML key used in the documentation.
 */
@Test(singleThreaded = true)
public class ReportingConfigYamlTest {

    private static final String BASE_YAML =
            "browser:\n" +
            "  name: chrome\n" +
            "execution:\n" +
            "  mode: local\n" +
            "  baseUrl: http://localhost\n" +
            "timeouts:\n" +
            "  explicit: 10\n" +
            "  pageLoad: 30\n";

    // ── Allure: nested format (documented) ──────────────────────────────────

    @Test
    public void allure_nestedEnabled_true() {
        String yaml = BASE_YAML +
                "reporting:\n" +
                "  allure:\n" +
                "    enabled: true\n";

        TestFlyConfig config = parse(yaml);
        assertTrue(config.getReporting().isAllureEnabled(),
                "reporting.allure.enabled: true should enable Allure");
    }

    @Test
    public void allure_nestedEnabled_false() {
        String yaml = BASE_YAML +
                "reporting:\n" +
                "  allure:\n" +
                "    enabled: false\n";

        TestFlyConfig config = parse(yaml);
        assertFalse(config.getReporting().isAllureEnabled(),
                "reporting.allure.enabled: false should leave Allure disabled");
    }

    // ── Allure: flat format (backward compatibility) ────────────────────────

    @Test
    public void allure_flatEnabled_true() {
        String yaml = BASE_YAML +
                "reporting:\n" +
                "  allureEnabled: true\n";

        TestFlyConfig config = parse(yaml);
        assertTrue(config.getReporting().isAllureEnabled(),
                "reporting.allureEnabled: true should still enable Allure (backward compat)");
    }

    @Test
    public void allure_flatEnabled_false() {
        String yaml = BASE_YAML +
                "reporting:\n" +
                "  allureEnabled: false\n";

        TestFlyConfig config = parse(yaml);
        assertFalse(config.getReporting().isAllureEnabled());
    }

    // ── Allure: default (no config) ─────────────────────────────────────────

    @Test
    public void allure_default_isDisabled() {
        TestFlyConfig config = parse(BASE_YAML);
        assertFalse(config.getReporting().isAllureEnabled(),
                "Allure should be disabled by default");
    }

    // ── Allure: both formats set — flat true wins ───────────────────────────

    @Test
    public void allure_bothFormats_flatTrueWins() {
        String yaml = BASE_YAML +
                "reporting:\n" +
                "  allureEnabled: true\n" +
                "  allure:\n" +
                "    enabled: false\n";

        TestFlyConfig config = parse(yaml);
        assertTrue(config.getReporting().isAllureEnabled(),
                "When flat allureEnabled is true, isAllureEnabled() should return true");
    }

    @Test
    public void allure_bothFormats_nestedTrueWins() {
        String yaml = BASE_YAML +
                "reporting:\n" +
                "  allureEnabled: false\n" +
                "  allure:\n" +
                "    enabled: true\n";

        TestFlyConfig config = parse(yaml);
        assertTrue(config.getReporting().isAllureEnabled(),
                "When nested allure.enabled is true, isAllureEnabled() should return true");
    }

    // ── ReportPortal: lowercase YAML key (documented) ───────────────────────

    @Test
    public void reportPortal_lowercaseYamlKey_isAccepted() {
        String yaml = BASE_YAML +
                "reporting:\n" +
                "  reportportal:\n" +
                "    enabled: true\n" +
                "    endpoint: http://localhost:8080\n" +
                "    apiKey: test-key\n" +
                "    project: my-project\n";

        TestFlyConfig config = parse(yaml);
        TestFlyConfig.Reporting.ReportPortal rp = config.getReporting().getReportPortal();
        assertTrue(rp.isEnabled(), "reportportal (lowercase) should map to ReportPortal config");
        assertEquals(rp.getEndpoint(), "http://localhost:8080");
        assertEquals(rp.getApiKey(), "test-key");
        assertEquals(rp.getProject(), "my-project");
    }

    // ── ReportPortal: camelCase YAML key (backward compat) ──────────────────

    @Test
    public void reportPortal_camelCaseYamlKey_stillWorks() {
        String yaml = BASE_YAML +
                "reporting:\n" +
                "  reportPortal:\n" +
                "    enabled: true\n" +
                "    endpoint: http://rp.example.com\n";

        TestFlyConfig config = parse(yaml);
        TestFlyConfig.Reporting.ReportPortal rp = config.getReporting().getReportPortal();
        assertTrue(rp.isEnabled(), "reportPortal (camelCase) should still work");
        assertEquals(rp.getEndpoint(), "http://rp.example.com");
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private static TestFlyConfig parse(String yamlContent) {
        LoaderOptions opts = new LoaderOptions();
        Constructor ctor = new Constructor(TestFlyConfig.class, opts);
        Yaml yaml = new Yaml(ctor);
        InputStream is = new ByteArrayInputStream(yamlContent.getBytes(StandardCharsets.UTF_8));
        return yaml.load(is);
    }
}
