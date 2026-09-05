# Feature Plan: Declarative Network Interception & Mocking DSL

- **Feature Name**: `testfly-network-mocking`
- **Target Version**: `v1.2.0`
- **Status**: Proposed / Architecture Design
- **Author**: TestFly Core Team

---

## 1. Executive Summary

Modern web applications are heavily asynchronous and API-driven. Testing edge cases like backend 500 errors, slow network latency (3G/throttling), rate limiting (429), or mock payments typically requires setting up external HTTP proxy servers (e.g., WireMock, MockServer) which introduces network latency, port conflicts in parallel CI builds, and brittle localhost URL redirects.

The **TestFly Network Mocking DSL** leverages Selenium 4's native **W3C WebDriver BiDi (Bidirectional) and Chrome DevTools Protocol (CDP)** network interception capabilities. It allows QA and developers to mock, modify, inspect, and delay HTTP network requests and responses directly in Java with a clean, expressive, fluent DSL—with zero external proxy processes and full parallel test thread isolation.

---

## 2. Motivation & Problem Statement

### The Problem Today
1. **External Mock Servers are Heavy**: Running WireMock or MockServer requires starting separate local processes, configuring random ports to avoid collisions in parallel CI runs, and modifying frontend app configurations to point to `localhost:xxxx`.
2. **Third-Party API Testing is Dangerous/Expensive**: Testing Stripe, PayPal, SMS verification, or OAuth flows against live third-party services causes unwanted side-effects, costs money, or triggers rate limits.
3. **Flaky Network State Reproduction**: Simulating a slow 10-second backend delay or sporadic 503 Service Unavailable errors is cumbersome and error-prone without native browser-level interception.

### The Solution: Playwright-like Native Network Routing for Java
Inspired by Playwright's `page.route()`, TestFly provides an embedded, in-browser network routing API:
```java
mockRoute("/api/v1/user/profile", Response.json(200, "{ \"name\": \"John Doe\", \"role\": \"ADMIN\" }"));
mockRoute("**/analytics.google.com/**", Response.abort());
mockRoute("/api/checkout", Response.delay(Duration.ofSeconds(5), Response.status(504)));
```

---

## 3. Architecture & Technical Design

```
+-----------------------------------------------------------------------------------+
|                                 Test Method (Java)                                |
|                                                                                   |
|  mockRoute("**/api/cart", Response.json(200, "{\"items\": []}"))                  |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                        io.testfly.network.NetworkManager                          |
|  - RouteRegistry (ThreadLocal<List<RouteRule>>)                                   |
|  - Glob / Regex URL Pattern Matcher                                               |
|  - Lifecycle auto-reset (@AfterMethod / test completion)                          |
+-----------------------------------------------------------------------------------+
                                          |
                        +-----------------+-----------------+
                        |                                   |
                        v (Chrome/Edge/Brave)               v (Firefox/Safari/W3C)
+-----------------------------------------------+ +---------------------------------+
| Selenium 4 CDP Network Interceptor            | | Selenium 4 BiDi Network Handler |
| - Fetch.enable / Network.enable               | | - network.beforeRequestSent     |
| - Fetch.fulfillRequest / Fetch.failRequest    | | - network.responseStarted       |
+-----------------------------------------------+ +---------------------------------+
                        |                                   |
                        +-----------------+-----------------+
                                          |
                                          v
                        +-----------------------------------+
                        |       Target Browser Engine       |
                        | (Interception occurs inside browser|
                        | without proxy server overhead)    |
                        +-----------------------------------+
```

### Core Components

1. **`io.testfly.network.NetworkManager`**:
   - Manages active route rules per test execution thread (`ThreadLocal`).
   - Hooks into the current `WebDriver` session using `HasAuthentication`, `HasBiDi`, or `Augmenter().augment(driver)`.
   - Automatically unregisters all mocks and restores clean network state after every test method.

2. **`io.testfly.network.RouteRule`**:
   - Encapsulates URL pattern (Ant-style glob `**/api/**` or regex `Pattern`), HTTP method filter (`GET`, `POST`, `*`), and request handler callback.

3. **`io.testfly.network.Response` Builder**:
   - Factory for creating simulated responses:
     - `Response.json(int status, Object jsonOrPojo)`
     - `Response.text(int status, String body)`
     - `Response.status(int status)`
     - `Response.abort(AbortReason reason)` (e.g. `CONNECTION_FAILED`, `TIMED_OUT`)
     - `Response.delay(Duration duration, Response delegate)`
     - `Response.passthrough()` (allows inspect & assert without modifying)

4. **`io.testfly.network.NetworkAssert`**:
   - Fluent assertions for verifying intercepted traffic:
     ```java
     assertThatNetwork().request("/api/checkout")
         .hasCount(1)
         .hasMethod("POST")
         .hasHeader("Authorization", startingWith("Bearer "))
         .hasJsonBody("$.currency", equalTo("USD"));
     ```

---

## 4. User-Facing Configuration & API

### 1. Basic JSON Response Mocking
```java
public class UserProfileTest extends BaseTest {

    @Test
    public void displaysAdminBadgeForAdminRole() {
        // Mock backend API before navigating
        mockRoute("/api/users/me", Response.json(200, """
            {
                "id": 42,
                "name": "Alex Mercer",
                "role": "SUPER_ADMIN"
            }
            """));

        open("/profile");

        assertThat(find(".badge-role")).hasText("SUPER_ADMIN");
    }
}
```

### 2. Simulating Network Delays & Loading Spinners
```java
@Test
public void showsSkeletonLoaderDuringSlowFetch() {
    mockRoute("/api/products", Response.delay(
        Duration.ofMillis(1500), 
        Response.json(200, "[]")
    ));

    open("/products");

    // Verify skeleton appears immediately
    assertThat(find(".product-skeleton")).isVisible();

    // After 1.5s, empty state is displayed
    assertThat(find(".empty-catalog-message")).isVisible();
}
```

### 3. Blocking Third-Party Trackers & Ads for Test Speed
```yaml
# testfly.yml - Globally block bloatware during tests
network:
  blockUrls:
    - "**/*google-analytics.com/**"
    - "**/*doubleclick.net/**"
    - "**/*hotjar.com/**"
    - "**/*facebook.net/**"
```

### 4. Modifying Live Responses (Mutation / Spy)
```java
@Test
public void injectDiscountBannerIntoLiveApiResponse() {
    mockRoute("/api/settings", route -> {
        // Fetch actual response from live server
        var response = route.fetchOriginal();
        // Modify JSON on the fly before browser sees it
        String modifiedBody = response.getBody().replace("\"discountActive\": false", "\"discountActive\": true");
        route.fulfill(Response.json(200, modifiedBody));
    });

    open("/home");
    assertThat(find(".special-discount-banner")).isVisible();
}
```

---

## 5. Phased Implementation Plan

### Phase 1: Core BiDi/CDP Abstraction & Mock Routes (Sprint 1)
- [ ] Create `io.testfly.network` module and base interfaces (`RouteHandler`, `MockResponse`, `RequestPattern`).
- [ ] Implement CDP-based `Fetch` domain handler for Chromium browsers (Chrome, Edge).
- [ ] Implement glob matcher (`**/api/v1/**` matching).
- [ ] Integrate with `BaseTest`: ensure `NetworkManager.cleanup()` executes in `@AfterMethod`.

### Phase 2: Response Mutators & Latency Emulation (Sprint 2)
- [ ] Add `Response.delay()` simulation via scheduled executor or CDP latency emulation.
- [ ] Add `Response.abort()` for testing offline modes and connection loss.
- [ ] Add `route.fetchOriginal()` to allow request/response spy and mutation.
- [ ] Add BiDi `Network` protocol support for Firefox.

### Phase 3: Traffic Assertion DSL & Tracing Integration (Sprint 3)
- [ ] Implement `NetworkAssert` for asserting requested endpoints, query params, and JSON payloads.
- [ ] Stream intercepted requests to `TraceCollector` (Feature #01) so all mocked/intercepted calls appear in the Trace Viewer.
- [ ] Provide global URL blocklist in `testfly.yml`.

---

## 6. Performance & Parallel Execution

- **Zero Port Collisions**: Unlike WireMock which binds to TCP ports on localhost, CDP and BiDi network interception operates directly within the browser process memory over the existing WebDriver remote debugging connection.
- **Thread Safety**: Each test thread binds exclusively to its own `WebDriver` session's CDP devtools target. No cross-test leakage in parallel TestNG executions.
- **Low Overhead**: Interception is only activated for routes registered by the user. If no mocks are declared, network interception domains remain inactive with zero performance degradation.
