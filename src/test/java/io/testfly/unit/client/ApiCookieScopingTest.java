package io.testfly.unit.client;

import io.testfly.client.ApiClient;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

import static org.testng.Assert.assertEquals;

@Test(singleThreaded = true)
public class ApiCookieScopingTest extends ApiClientTestBase {

    @Test
    public void cookiesAreNotSentToAnotherHost() throws Exception {
        String ip = server(ex -> {
            if (ex.getRequestURI().getPath().endsWith("/login")) {
                reply(ex, 200, "in", Map.of("Set-Cookie", "session=abc; Path=/"));
            } else {
                reply(ex, 200, Objects.toString(ex.getRequestHeaders().getFirst("Cookie"), "none"));
            }
        });
        String byName = "http://localhost:" + URI.create(ip).getPort();

        ApiClient.post(ip + "/api/auth/login").withCookies().send();

        assertEquals(ApiClient.get(ip + "/api/users").withCookies().send().body(), "session=abc");
        assertEquals(ApiClient.get(byName + "/api/users").withCookies().send().body(), "none",
                "a cookie set by 127.0.0.1 must not leak to localhost");
        assertEquals(ApiClient.get(ip + "/api/users").send().body(), "none", "cookies are opt-in per request");
    }
}
