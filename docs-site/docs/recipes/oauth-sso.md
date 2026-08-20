---
description: "Test OAuth 2.0 / SSO flows in Selenium: obtain tokens via ApiClient, bypass the browser login where appropriate, and use MultiSessionManager for admin-and-user scenarios."
id: oauth-sso
title: OAuth / SSO login
sidebar_label: OAuth / SSO
---

# OAuth / SSO login

Testing OAuth 2.0 or SAML single sign-on in a browser can be slow and brittle: the identity provider's UI changes, MFA popups block automation, and tests spend most of their time on login pages instead of your application.

The practical approach is to **separate authentication from authorization testing**: use the API or a pre-seeded session to get a token, then inject it into the browser. Reserve full browser-based SSO flows for one or two explicit regression tests.

---

## Option 1: API-first login (fast and stable)

If your app accepts an access token in `localStorage`, a cookie, or an `Authorization` header, call the token endpoint directly with `ApiClient` and inject the result:

```java title="OAuthLoginTest.java"
import io.testfly.api.ApiResponse;
import io.testfly.client.ApiClient;
import io.testfly.test.BaseTest;
import org.testng.annotations.Test;

import java.util.Map;

public class OAuthLoginTest extends BaseTest {

    @Test
    public void logsInViaTokenInjection() {
        // 1. Get token from the identity provider
        ApiResponse tokenResponse = ApiClient.post("https://idp.example.com/oauth/token")
            .body(Map.of(
                "grant_type", "password",
                "client_id", "test-client",
                "username", "testuser",
                "password", "testpass"))
            .send();

        String accessToken = tokenResponse.json("$.access_token");

        // 2. Open the app and inject the token
        open("/");
        sessionStorage().set("access_token", accessToken);

        // 3. Navigate to a protected route
        open("/dashboard");

        assertThat(find("h1")).hasText("Dashboard");
    }
}
```

:::info Keep credentials out of tests
Use environment variables or `${VAR}` placeholders in `testfly.yml` for client secrets and test passwords.
:::

---

## Option 2: Full browser SSO flow

When you genuinely need to test the browser redirect dance, follow the redirects explicitly and assert the URL changes:

```java title="SsoFlowTest.java"
public class SsoFlowTest extends BaseTest {

    @Test
    public void ssoRedirectReturnsToAppWithCode() {
        open("/login");
        find("#sso-login").click();   // triggers redirect to IdP

        // IdP login page
        getWait().waitForUrlContains("idp.example.com");
        find("#username").type("testuser");
        find("#password").type("testpass");
        find("#submit").click();

        // Redirect back to app with authorization code
        getWait().waitForUrlContains("/callback?code=");

        assertThat(find("h1")).hasText("Dashboard");
    }
}
```

:::tip Avoid MFA in test tenants
Create a dedicated test IdP tenant that skips MFA, or use a test user with MFA disabled. Automating TOTP/SMS is possible but adds fragility.
:::

---

## Option 3: Multi-session admin + user

Some SSO scenarios require two browsers: an admin provisioning a user, and that user logging in. Use `MultiSessionManager.withSession(...)`:

```java
@Test
public void adminInviteCreatesLoginForUser() {
    open("/admin/users");
    find("#invite-user").click();
    find("#email").type("newuser@example.com");
    find("#send-invite").click();
    assertThat(find("#toast")).hasText("Invitation sent");

    MultiSessionManager.withSession("newuser", () -> {
        open("/signup?token=" + inviteToken);
        find("#password").type("Welcome123!");
        find("#complete").click();
        assertThat(find("h1")).hasText("Welcome");
    });
}
```

---

## Which option to choose

| Scenario | Recommended approach |
|---|---|
| Everyday functional tests of authenticated features | **API-first login** — inject token, skip the IdP UI |
| SSO integration / redirect regression | **Full browser flow** — one or two tests max |
| Admin-and-user cross-browser flows | **Multi-session** — keep both contexts isolated |
| Testing token expiry / refresh logic | **API-first** with `TestClock` to advance time |

---

## Common pitfalls

- **Hard-coding IdP selectors.** Identity-provider UIs change. Prefer API-first login for routine tests.
- **Storing real passwords in tests.** Use config placeholders or environment variables.
- **Not waiting for redirect.** Use `getWait().waitForUrlContains(...)` instead of `Thread.sleep`.
- **Forgetting third-party cookies.** Some IdPs reject cookies in headless/CI mode; add `--disable-features=SameSiteByDefaultCookies` only if needed, and test it.

---

**Deeper reference:**
- [API Testing](/docs/guides/api-testing) — `ApiClient` and `ApiResponse`
- [Multi-Session Testing](/docs/guides/browser-lifecycle) — running two browsers in one test
- [Clock Mocking](/docs/clock-mocking) — testing token expiry without waiting
