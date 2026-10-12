package io.testfly.unit;

import io.testfly.locator.Locator;
import io.testfly.locator.LocatorException;
import io.testfly.locator.LocatorRepository;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.*;

/**
 * Unit tests for {@link LocatorRepository}. They check the synthesized locator
 * descriptions and the fail-fast rules — no browser is required.
 */
public class LocatorRepositoryTest {

    private static LocatorRepository parse(String yaml) {
        return LocatorRepository.load(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "test.yaml");
    }

    private static void assertRejects(String yaml, String... fragments) {
        try {
            parse(yaml);
            fail("expected LocatorException");
        } catch (LocatorException e) {
            for (String fragment : fragments) {
                assertTrue(e.getMessage().contains(fragment),
                        "message should contain '" + fragment + "' but was: " + e.getMessage());
            }
        }
    }

    // ---- loading ---------------------------------------------------------

    @Test
    public void load_classpathResource_exposesPageAndKeys() {
        LocatorRepository repo = LocatorRepository.load("locators/register.yaml");

        assertEquals(repo.page(), "register");
        assertTrue(repo.contains("register_form"));
        assertTrue(repo.contains("register_form.email"));
        assertTrue(repo.contains("login_link"));
        assertFalse(repo.contains("email"), "children are only addressable through their container");
    }

    @Test
    public void load_filePath_fallsBackToFileSystem() throws IOException {
        Path file = Files.createTempFile("testfly-locators", ".yaml");
        try {
            Files.writeString(file, "elements:\n  save:\n    id: save\n");
            LocatorRepository repo = LocatorRepository.load(file.toString());
            assertTrue(repo.contains("save"));
            assertNull(repo.page());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void load_missingFile_namesSearchedLocations() {
        try {
            LocatorRepository.load("locators/does-not-exist.yaml");
            fail("expected LocatorException");
        } catch (LocatorException e) {
            assertTrue(e.getMessage().contains("does-not-exist.yaml"));
            assertTrue(e.getMessage().contains("classpath"));
            assertTrue(e.getMessage().contains("file system"));
        }
    }

    @Test
    public void load_malformedYaml_throwsLocatorException() {
        assertRejects("elements: [unclosed", "test.yaml");
    }

    // ---- strategy mapping ------------------------------------------------

    @Test
    public void strategies_mapToTheMatchingFactory() {
        LocatorRepository repo = parse("""
                elements:
                  by_css: {css: "#a"}
                  by_xpath: {xpath: "//a"}
                  by_id: {id: "b"}
                  by_name: {byName: "c"}
                  by_class: {className: "d"}
                  by_text: {text: "Hello"}
                  by_label: {label: "Email"}
                  by_placeholder: {placeholder: "Search"}
                  by_test_id: {testId: "x"}
                  by_alt: {altText: "Logo"}
                  by_title: {title: "Close"}
                """);

        assertEquals(repo.get("by_css").toString(), Locator.cssSelector("#a").toString());
        assertEquals(repo.get("by_xpath").toString(), Locator.xpath("//a").toString());
        assertEquals(repo.get("by_id").toString(), Locator.id("b").toString());
        assertEquals(repo.get("by_name").toString(), Locator.name("c").toString());
        assertEquals(repo.get("by_class").toString(), Locator.className("d").toString());
        assertEquals(repo.get("by_text").toString(), Locator.byText("Hello").toString());
        assertEquals(repo.get("by_label").toString(), Locator.byLabel("Email").toString());
        assertEquals(repo.get("by_placeholder").toString(), Locator.byPlaceholder("Search").toString());
        assertEquals(repo.get("by_test_id").toString(), Locator.byTestId("x").toString());
        assertEquals(repo.get("by_alt").toString(), Locator.byAltText("Logo").toString());
        assertEquals(repo.get("by_title").toString(), Locator.byTitle("Close").toString());
    }

    @Test
    public void role_withAccessibleNameAndExact_mapsToChain() {
        LocatorRepository repo = parse("""
                elements:
                  login:
                    role: Button
                    accessibleName: "Login"
                    exact: true
                """);

        assertEquals(repo.get("login").toString(),
                Locator.byRole(io.testfly.locator.Role.BUTTON).withName("Login").exact().toString());
    }

    @Test
    public void role_heading_withLevel() {
        LocatorRepository repo = parse("elements:\n  h:\n    role: heading\n    level: 2\n");
        assertEquals(repo.get("h").toString(),
                Locator.byRole(io.testfly.locator.Role.HEADING).withLevel(2).toString());
    }

    @Test
    public void numericIdValue_isAcceptedAsText() {
        assertEquals(parse("elements:\n  n:\n    id: 123\n").get("n").toString(),
                Locator.id("123").toString());
    }

    @Test
    public void position_nthFirstLast() {
        LocatorRepository repo = parse("""
                elements:
                  second: {css: ".row", nth: 1}
                  top: {css: ".row", first: true}
                  bottom: {css: ".row", last: true}
                  plain: {css: ".row", first: false}
                """);

        assertEquals(repo.get("second").toString(), Locator.cssSelector(".row").nth(1).toString());
        assertEquals(repo.get("top").toString(), Locator.cssSelector(".row").first().toString());
        assertEquals(repo.get("plain").toString(), Locator.cssSelector(".row").toString());
        assertNotNull(repo.get("bottom"));
    }

    // ---- containers ------------------------------------------------------

    @Test
    public void container_scopesChildrenWithWithin() {
        LocatorRepository repo = parse("""
                elements:
                  form:
                    css: "#register-form"
                    elements:
                      email: {css: "input[type=email]"}
                      submit: {role: button, accessibleName: "Register"}
                """);

        assertEquals(repo.get("form").toString(), Locator.cssSelector("#register-form").toString());
        assertEquals(repo.get("form.email").toString(),
                Locator.cssSelector("input[type=email]")
                        .within(Locator.cssSelector("#register-form").toBy()).toString());
        assertTrue(repo.get("form.submit").toString().contains("within("));
        assertEquals(repo.keys().size(), 3);
    }

    @Test
    public void container_withSemanticStrategy_isRejected() {
        assertRejects("""
                elements:
                  form:
                    role: form
                    elements:
                      a: {css: "x"}
                """, "form", "container strategy");
    }

    @Test
    public void container_withModifier_isRejected() {
        assertRejects("""
                elements:
                  form:
                    css: "#f"
                    first: true
                    elements:
                      a: {css: "x"}
                """, "form", "no modifiers");
    }

    @Test
    public void container_nestedTwoLevels_isRejected() {
        assertRejects("""
                elements:
                  form:
                    css: "#f"
                    elements:
                      fieldset:
                        css: "fieldset"
                        elements:
                          a: {css: "x"}
                """, "form.fieldset", "one level");
    }

    @Test
    public void container_emptyChildren_isRejected() {
        assertRejects("elements:\n  form:\n    css: \"#f\"\n    elements: {}\n", "form", "non-empty");
    }

    // ---- fail-fast rules -------------------------------------------------

    @Test
    public void conflictingStrategies_areRejected() {
        assertRejects("elements:\n  x:\n    css: \"#a\"\n    role: button\n",
                "test.yaml", "'x'", "conflicting", "css", "role");
    }

    @Test
    public void missingStrategy_isRejected() {
        assertRejects("elements:\n  x:\n    exact: true\n", "'x'", "no locator strategy");
    }

    @Test
    public void unknownElementKey_isRejected() {
        assertRejects("elements:\n  x:\n    css: \"#a\"\n    within: other\n", "'x'", "unknown key 'within'");
    }

    @Test
    public void unknownTopLevelKey_isRejected() {
        assertRejects("elemnts:\n  x: {css: \"#a\"}\n", "unknown top-level key 'elemnts'");
    }

    @Test
    public void missingOrEmptyElements_isRejected() {
        assertRejects("page: p\n", "'elements'");
        assertRejects("elements: {}\n", "'elements'");
    }

    @Test
    public void rootNotAMapping_isRejected() {
        assertRejects("- a\n- b\n", "mapping");
    }

    @Test
    public void unknownRole_listsValidRoles() {
        assertRejects("elements:\n  x:\n    role: buton\n", "'x'", "unknown role 'buton'", "button");
    }

    @Test
    public void accessibleName_withoutRole_isRejected() {
        assertRejects("elements:\n  x:\n    css: \"#a\"\n    accessibleName: \"A\"\n",
                "'accessibleName' requires the 'role' strategy");
    }

    @Test
    public void level_requiresHeadingRole() {
        assertRejects("elements:\n  x:\n    role: button\n    level: 1\n", "level", "heading");
        assertRejects("elements:\n  x:\n    role: heading\n    level: 7\n", "between 1 and 6");
    }

    @Test
    public void exact_withUnsupportedStrategy_isRejected() {
        assertRejects("elements:\n  x:\n    css: \"#a\"\n    exact: true\n", "'exact' is not supported");
    }

    @Test
    public void multiplePositions_areRejected() {
        assertRejects("elements:\n  x:\n    css: \".r\"\n    first: true\n    nth: 2\n",
                "only one of nth, first, last");
    }

    @Test
    public void invalidValues_areRejected() {
        assertRejects("elements:\n  x:\n    css: \"\"\n", "non-blank");
        assertRejects("elements:\n  x:\n    css: \".r\"\n    nth: -1\n", "non-negative");
        assertRejects("elements:\n  x:\n    css: \".r\"\n    first: \"yes\"\n", "true or false");
    }

    @Test
    public void duplicateKeys_areRejected() {
        assertRejects("elements:\n  x: {css: \"a\"}\n  x: {css: \"b\"}\n", "test.yaml");
    }

    @Test
    public void yamlBooleanElementName_isRejectedWithQuoteHint() {
        assertRejects("elements:\n  off: {css: \"x\"}\n", "keys must be strings", "quote the key");
    }

    @Test
    public void yamlBooleanValue_isRejectedWithQuoteHint() {
        assertRejects("elements:\n  confirm:\n    text: Yes\n", "'confirm'", "'text' must be a non-blank string",
                "quote the value", "text: \"Yes\"");
        assertRejects("elements:\n  toggle:\n    text: off\n", "quote the value");
        assertRejects("elements:\n  nothing:\n    text:\n", "quote the value");
    }

    @Test
    public void quotedBooleanLookingValue_isAccepted() {
        LocatorRepository repo = parse("elements:\n  confirm:\n    text: \"Yes\"\n");
        assertEquals(repo.get("confirm").toString(), Locator.byText("Yes").toString());
    }

    @Test
    public void blankValue_hasNoQuoteHint() {
        try {
            parse("elements:\n  x:\n    css: \"\"\n");
            fail("expected LocatorException");
        } catch (LocatorException e) {
            assertFalse(e.getMessage().contains("quote the value"), e.getMessage());
        }
    }

    @Test
    public void dottedElementName_isRejected() {
        assertRejects("elements:\n  a.b: {css: \"x\"}\n", "invalid element name");
    }

    @Test
    public void unknownKey_listsAvailableKeys() {
        LocatorRepository repo = parse("elements:\n  known: {css: \"x\"}\n");
        try {
            repo.get("unknown");
            fail("expected LocatorException");
        } catch (LocatorException e) {
            assertTrue(e.getMessage().contains("unknown"));
            assertTrue(e.getMessage().contains("known"));
        }
    }
}
