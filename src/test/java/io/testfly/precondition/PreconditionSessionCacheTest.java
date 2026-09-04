package io.testfly.precondition;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link PreconditionSessionCache}.
 * Package-private access required — placed in src/test/java/io/testfly/precondition/.
 * Thread-safe for parallel=methods via singleThreaded.
 */
@Test(singleThreaded = true)
public class PreconditionSessionCacheTest {

    private WebDriver mockDriver;

    @BeforeMethod
    public void setup() {
        mockDriver = mock(WebDriver.class, withSettings().extraInterfaces(JavascriptExecutor.class));
        WebDriver.Options options = mock(WebDriver.Options.class);
        when(mockDriver.manage()).thenReturn(options);
        PreconditionSessionCache.clearAll();
    }

    @AfterMethod
    public void tearDown() {
        PreconditionSessionCache.clearAll();
    }

    // ── Cache hit → returns cached session ────────────────────────────────────

    @Test
    public void store_thenIsValid_returnsTrue() {
        Set<Cookie> cookies = new HashSet<>();
        cookies.add(new Cookie("session", "abc123"));
        when(mockDriver.manage().getCookies()).thenReturn(cookies);
        when(((JavascriptExecutor) mockDriver).executeScript(anyString())).thenReturn(new HashMap<>());

        PreconditionSessionCache.store("login", mockDriver);

        assertTrue(PreconditionSessionCache.isValid("login"),
                "After store, isValid should return true");
    }

    @Test
    public void restore_appliesCachedSession() {
        // Store a session
        Set<Cookie> cookies = new HashSet<>();
        cookies.add(new Cookie("session", "xyz789"));
        when(mockDriver.manage().getCookies()).thenReturn(cookies);
        when(((JavascriptExecutor) mockDriver).executeScript(anyString())).thenReturn(new HashMap<>());

        PreconditionSessionCache.store("login", mockDriver);

        // Restore it
        PreconditionSessionCache.restore("login", mockDriver);

        // Verify cookies were deleted and re-added
        verify(mockDriver.manage()).deleteAllCookies();
        verify(mockDriver.manage(), atLeastOnce()).addCookie(any(Cookie.class));
    }

    @Test
    public void store_withLocalStorage_capturesAndRestores() {
        Set<Cookie> cookies = new HashSet<>();
        cookies.add(new Cookie("token", "value"));
        when(mockDriver.manage().getCookies()).thenReturn(cookies);

        Map<String, String> localStorage = new HashMap<>();
        localStorage.put("user", "john");
        localStorage.put("theme", "dark");
        when(((JavascriptExecutor) mockDriver).executeScript(anyString())).thenReturn(localStorage);

        PreconditionSessionCache.store("login", mockDriver);

        // Restore should execute script to restore localStorage
        PreconditionSessionCache.restore("login", mockDriver);

        verify((JavascriptExecutor) mockDriver).executeScript(anyString());
        verify((JavascriptExecutor) mockDriver).executeScript(anyString(), any());
    }

    // ── Cache miss → returns null/empty ──────────────────────────────────────

    @Test
    public void isValid_noStoredSession_returnsFalse() {
        assertFalse(PreconditionSessionCache.isValid("nonExistent"),
                "isValid should return false for non-existent condition");
    }

    @Test
    public void restore_noStoredSession_doesNothing() {
        // Should not throw, just return silently
        PreconditionSessionCache.restore("nonExistent", mockDriver);

        // No cookies should be deleted
        verify(mockDriver.manage(), never()).deleteAllCookies();
    }

    @Test
    public void store_withEmptyCookies_isValidReturnsFalse() {
        when(mockDriver.manage().getCookies()).thenReturn(Collections.emptySet());
        when(((JavascriptExecutor) mockDriver).executeScript(anyString())).thenReturn(new HashMap<>());

        PreconditionSessionCache.store("login", mockDriver);

        assertFalse(PreconditionSessionCache.isValid("login"),
                "Session with no cookies should be considered invalid");
    }

    // ── Cache expiry → stale entries evicted ─────────────────────────────────

    @Test
    public void invalidate_removesCachedSession() {
        Set<Cookie> cookies = new HashSet<>();
        cookies.add(new Cookie("session", "abc"));
        when(mockDriver.manage().getCookies()).thenReturn(cookies);
        when(((JavascriptExecutor) mockDriver).executeScript(anyString())).thenReturn(new HashMap<>());

        PreconditionSessionCache.store("login", mockDriver);
        assertTrue(PreconditionSessionCache.isValid("login"));

        PreconditionSessionCache.invalidate("login");
        assertFalse(PreconditionSessionCache.isValid("login"),
                "After invalidate, isValid should return false");
    }

    @Test
    public void clearAll_removesAllCachedSessions() {
        Set<Cookie> cookies = new HashSet<>();
        cookies.add(new Cookie("session", "abc"));
        when(mockDriver.manage().getCookies()).thenReturn(cookies);
        when(((JavascriptExecutor) mockDriver).executeScript(anyString())).thenReturn(new HashMap<>());

        PreconditionSessionCache.store("login", mockDriver);
        PreconditionSessionCache.store("acceptCookies", mockDriver);

        assertTrue(PreconditionSessionCache.isValid("login"));
        assertTrue(PreconditionSessionCache.isValid("acceptCookies"));

        PreconditionSessionCache.clearAll();

        assertFalse(PreconditionSessionCache.isValid("login"),
                "After clearAll, login should be invalid");
        assertFalse(PreconditionSessionCache.isValid("acceptCookies"),
                "After clearAll, acceptCookies should be invalid");
    }

    @Test
    public void invalidate_isIdempotent() {
        // Invalidating a non-existent condition should not throw
        PreconditionSessionCache.invalidate("nonExistent");
        PreconditionSessionCache.invalidate("nonExistent");
        assertFalse(PreconditionSessionCache.isValid("nonExistent"));
    }

    @Test
    public void store_overwritesPreviousSession() {
        Set<Cookie> firstCookies = new HashSet<>();
        firstCookies.add(new Cookie("session", "first"));
        when(mockDriver.manage().getCookies()).thenReturn(firstCookies);
        when(((JavascriptExecutor) mockDriver).executeScript(anyString())).thenReturn(new HashMap<>());

        PreconditionSessionCache.store("login", mockDriver);

        Set<Cookie> secondCookies = new HashSet<>();
        secondCookies.add(new Cookie("session", "second"));
        when(mockDriver.manage().getCookies()).thenReturn(secondCookies);

        PreconditionSessionCache.store("login", mockDriver);

        // Restore and verify the second session was used
        PreconditionSessionCache.restore("login", mockDriver);
        verify(mockDriver.manage()).addCookie(argThat(c -> "second".equals(c.getValue())));
    }
}
