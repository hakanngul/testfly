package io.testfly.client;

import org.testng.annotations.Test;

import java.net.URI;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.testng.Assert.*;

public class CookieJarTest {

    private static URI u(String s) {
        return URI.create(s);
    }

    @Test
    public void hostOnlyCookiesStayOnTheirHost() {
        CookieJar jar = new CookieJar();
        jar.capture(u("http://api.example.com/login"), List.of("sid=1; HttpOnly"));
        assertEquals(jar.headerFor(u("http://api.example.com/users")), "sid=1");
        assertNull(jar.headerFor(u("http://other.example.com/users")));
        assertNull(jar.headerFor(u("http://sub.api.example.com/users")));
    }

    @Test
    public void domainCookiesCoverSubdomainsAndForeignDomainsAreRejected() {
        CookieJar jar = new CookieJar();
        jar.capture(u("http://auth.example.com/"), List.of("d=1; Domain=.example.com", "evil=1; Domain=attacker.com"));
        assertEquals(jar.headerFor(u("http://api.example.com/")), "d=1");
        assertEquals(jar.headerFor(u("http://example.com/")), "d=1");
        assertNull(jar.headerFor(u("http://attacker.com/")));
        assertNull(jar.headerFor(u("http://notexample.com/")));
    }

    @Test
    public void pathScopingAndOrdering() {
        CookieJar jar = new CookieJar();
        jar.capture(u("http://h/api/auth/login"), List.of("a=1; Path=/api", "b=2; Path=/api/auth", "c=3"));
        assertEquals(jar.headerFor(u("http://h/api/auth/x")), "b=2; a=1; c=3");
        assertEquals(jar.headerFor(u("http://h/api/users")), "a=1; c=3");
        assertEquals(jar.headerFor(u("http://h/apiary")), "c=3", "path match needs a segment boundary");
        assertEquals(jar.headerFor(u("http://h/")), "c=3", "no Path attribute applies to /");
    }

    @Test
    public void lifetimeAndDeletion() {
        CookieJar jar = new CookieJar();
        String past = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC).minusDays(1));
        String future = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC).plusDays(1));
        jar.capture(u("http://h/"), List.of("keep=1; Expires=" + future, "old=1; Expires=" + past, "tmp=1; Max-Age=60"));
        String header = jar.headerFor(u("http://h/"));
        assertTrue(header.contains("keep=1") && header.contains("tmp=1") && !header.contains("old"), header);

        jar.capture(u("http://h/"), List.of("tmp=gone; Max-Age=0", "keep=x; Max-Age=-1"));
        assertNull(jar.headerFor(u("http://h/")));
        assertTrue(jar.isEmpty());
    }

    @Test
    public void secureCookiesOnlyOverHttpsAndValuesAreReplaced() {
        CookieJar jar = new CookieJar();
        jar.capture(u("https://h/"), List.of("s=1; Secure", "p=1"));
        assertEquals(jar.headerFor(u("http://h/")), "p=1");
        assertTrue(jar.headerFor(u("https://h/")).contains("s=1"));

        jar.capture(u("https://h/"), List.of("p=2"));
        assertEquals(jar.headerFor(u("http://h/")), "p=2");
    }

    @Test
    public void malformedHeadersAreIgnored() {
        CookieJar jar = new CookieJar();
        jar.capture(u("http://h/"), List.of("novalue", "=x", "ok=1; Max-Age=abc; Expires=garbage"));
        assertEquals(jar.headerFor(u("http://h/")), "ok=1");
    }
}
