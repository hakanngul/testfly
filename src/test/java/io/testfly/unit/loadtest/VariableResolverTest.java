package io.testfly.unit.loadtest;

import io.testfly.loadtest.internal.VariableResolver;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.*;

/**
 * Tests {@link VariableResolver} — ${var} substitution in strings and bodies.
 */
@Test(singleThreaded = true)
public class VariableResolverTest {

    @Test
    public void testSimpleSubstitution() {
        assertEquals(
                VariableResolver.resolve("/api/users/${userId}", Map.of("userId", 42)),
                "/api/users/42");
    }

    @Test
    public void testMultipleVariables() {
        assertEquals(
                VariableResolver.resolve("${greeting} ${name}!", Map.of("greeting", "Hello", "name", "World")),
                "Hello World!");
    }

    @Test
    public void testUnresolvedVariableLeftAsIs() {
        assertEquals(
                VariableResolver.resolve("/api/${missing}/data", Map.of("other", "x")),
                "/api/${missing}/data");
    }

    @Test
    public void testNullTemplate() {
        assertNull(VariableResolver.resolve(null, Map.of("a", "b")));
    }

    @Test
    public void testNullVariables() {
        assertEquals(VariableResolver.resolve("/api/${id}", null), "/api/${id}");
    }

    @Test
    public void testEmptyVariables() {
        assertEquals(VariableResolver.resolve("/api/${id}", Map.of()), "/api/${id}");
    }

    @Test
    public void testNoVariablesInTemplate() {
        assertEquals(VariableResolver.resolve("/api/health", Map.of("id", "1")), "/api/health");
    }

    @Test
    public void testStringBody() {
        Object result = VariableResolver.resolveBody("{\"id\": \"${userId}\"}", Map.of("userId", "42"));
        assertEquals(result, "{\"id\": \"42\"}");
    }

    @Test
    public void testMapBody() {
        Map<String, Object> body = Map.of("user", "${username}", "pass", "${password}");
        Map<String, Object> vars = Map.of("username", "alice", "password", "secret");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) VariableResolver.resolveBody(body, vars);

        assertEquals(result.get("user"), "alice");
        assertEquals(result.get("pass"), "secret");
    }

    @Test
    public void testNestedMapBody() {
        Map<String, Object> inner = Map.of("token", "${auth}");
        Map<String, Object> body = Map.of("header", inner);
        Map<String, Object> vars = Map.of("auth", "Bearer xyz");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) VariableResolver.resolveBody(body, vars);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultInner = (Map<String, Object>) result.get("header");

        assertEquals(resultInner.get("token"), "Bearer xyz");
    }

    @Test
    public void testNonStringValuesUnchanged() {
        Map<String, Object> body = Map.of("count", 5, "active", true);
        Map<String, Object> vars = Map.of("x", "y");

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) VariableResolver.resolveBody(body, vars);

        assertEquals(result.get("count"), 5);
        assertEquals(result.get("active"), true);
    }

    @Test
    public void testNullBody() {
        assertNull(VariableResolver.resolveBody(null, Map.of("a", "b")));
    }

    @Test
    public void testSpecialCharactersInValue() {
        assertEquals(
                VariableResolver.resolve("Bearer ${token}", Map.of("token", "abc$123\\xyz")),
                "Bearer abc$123\\xyz");
    }
}
