package io.testfly.unit.config;

import io.testfly.config.DotEnvLoader;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

@Test(singleThreaded = true)
public class DotEnvLoaderTest {

    @BeforeMethod
    public void setup() {
        System.clearProperty("TEST_DOTENV_KEY");
        System.clearProperty("TEST_DOTENV_OTHER");
        System.clearProperty("TEST_DOTENV_NONEXISTENT");
    }

    @AfterMethod
    public void cleanup() {
        System.clearProperty("TEST_DOTENV_KEY");
        System.clearProperty("TEST_DOTENV_OTHER");
        System.clearProperty("TEST_DOTENV_NONEXISTENT");
    }

    @Test
    public void resolve_returnsRawString_whenNoPlaceholder() {
        assertEquals(DotEnvLoader.resolve("plain-value"), "plain-value");
    }

    @Test
    public void resolve_returnsNull_whenInputIsNull() {
        assertNull(DotEnvLoader.resolve(null));
    }

    @Test
    public void resolve_resolvesFromSystemProperty() {
        System.setProperty("TEST_DOTENV_KEY", "from-property");
        assertEquals(DotEnvLoader.resolve("${TEST_DOTENV_KEY}"), "from-property");
    }

    @Test
    public void resolve_returnsDefault_whenVarNotSet() {
        System.clearProperty("TEST_DOTENV_KEY");
        assertEquals(DotEnvLoader.resolve("${TEST_DOTENV_KEY:-fallback}"), "fallback");
    }

    @Test
    public void resolve_prefersSystemProperty_overDefault() {
        System.setProperty("TEST_DOTENV_KEY", "real-value");
        assertEquals(DotEnvLoader.resolve("${TEST_DOTENV_KEY:-fallback}"), "real-value");
    }

    @Test
    public void resolve_returnsNull_whenVarNotSetAndNoDefault() {
        System.clearProperty("TEST_DOTENV_NONEXISTENT");
        assertNull(DotEnvLoader.resolve("${TEST_DOTENV_NONEXISTENT}"));
    }

    @Test
    public void resolve_handlesWhitespaceInPlaceholder() {
        System.setProperty("TEST_DOTENV_KEY", "trimmed");
        assertEquals(DotEnvLoader.resolve("${ TEST_DOTENV_KEY }"), "trimmed");
    }

    @Test
    public void resolve_returnsRawString_whenPartialPlaceholder() {
        assertEquals(DotEnvLoader.resolve("${INCOMPLETE"), "${INCOMPLETE");
        assertEquals(DotEnvLoader.resolve("INCOMPLETE}"), "INCOMPLETE}");
        assertEquals(DotEnvLoader.resolve("prefix${TEST_DOTENV_KEY}suffix"), "prefix${TEST_DOTENV_KEY}suffix");
    }
}
