package io.testfly.test.support;

import io.testfly.api.TestFlyApi;
import io.testfly.browser.ClipboardHelper;
import io.testfly.browser.GeoLocation;
import io.testfly.browser.StorageHelper;
import io.testfly.network.NetworkMock;

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
