package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.browser.ClipboardHelper;
import io.testfly.browser.GeoLocation;
import io.testfly.browser.StorageHelper;
import io.testfly.network.NetworkAssert;
import io.testfly.network.NetworkMock;
import io.testfly.network.Response;
import io.testfly.network.Route;

import java.util.function.Consumer;

/**
 * Shared browser helpers — single source of truth for network/storage/geo/clipboard.
 *
 * <p>Implemented by {@code BaseTest} and {@code BasePage} so the delegation to
 * {@link NetworkMock}, {@link StorageHelper}, {@link GeoLocation} and
 * {@link ClipboardHelper} lives in one place.
 */
@TestFlyApi(since = "1.10.0")
public interface BrowserSupport {

    /** Network interception — stub API responses via CDP. */
    default NetworkMock networkMock() {
        return NetworkMock.get();
    }

    /** Mock any-method requests matching {@code pattern} with a fixed {@link Response}. */
    @TestFlyApi(since = "1.6.0")
    default NetworkMock mockRoute(String pattern, Response response) {
        return NetworkMock.get().mockRoute(pattern, response);
    }

    /** Mock any-method requests matching {@code pattern} with a programmatic handler. */
    @TestFlyApi(since = "1.6.0")
    default NetworkMock mockRoute(String pattern, Consumer<Route> handler) {
        return NetworkMock.get().mockRoute(pattern, handler);
    }

    /** Mock requests matching {@code method} + {@code pattern} with a fixed {@link Response}. */
    @TestFlyApi(since = "1.6.0")
    default NetworkMock mockRoute(String method, String pattern, Response response) {
        return NetworkMock.get().mockRoute(method, pattern, response);
    }

    /** Mock requests matching {@code method} + {@code pattern} with a programmatic handler. */
    @TestFlyApi(since = "1.6.0")
    default NetworkMock mockRoute(String method, String pattern, Consumer<Route> handler) {
        return NetworkMock.get().mockRoute(method, pattern, handler);
    }

    /** Entry point for fluent assertions over recorded network traffic. */
    @TestFlyApi(since = "1.6.0")
    default NetworkAssert assertThatNetwork() {
        return NetworkMock.get().assertThat();
    }

    /** localStorage read/write helpers. */
    default StorageHelper.LocalStorage localStorage() {
        return StorageHelper.localStorage();
    }

    /** sessionStorage read/write helpers. */
    default StorageHelper.SessionStorage sessionStorage() {
        return StorageHelper.sessionStorage();
    }

    /** Cookie read/write helpers. */
    default StorageHelper.Cookies cookies() {
        return StorageHelper.cookies();
    }

    /** Geolocation mock — override browser location via CDP or JS. */
    default GeoLocation mockLocation() {
        return GeoLocation.instance();
    }

    /** Clipboard read/write helpers. */
    default ClipboardHelper clipboard() {
        return ClipboardHelper.instance();
    }
}
