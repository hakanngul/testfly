package io.testfly.unit.mobile;

import io.testfly.mobile.DeviceSelectionException;
import io.testfly.mobile.MobileBounds;
import io.testfly.mobile.MobileDeviceInfo;
import io.testfly.mobile.MobileNode;
import io.testfly.mobile.MobilePlatform;
import io.testfly.mobile.MobileRole;
import io.testfly.mobile.MobileTimeoutException;
import io.testfly.mobile.internal.DeviceReservations;
import io.testfly.mobile.internal.DeviceSelector;
import io.testfly.mobile.internal.LocatorEngine;
import io.testfly.mobile.internal.LocatorEngine.Query;
import io.testfly.mobile.internal.LocatorEngine.TextMatch;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.testng.Assert.*;

/** Client-side locator evaluation, device selection and device reservation. */
public class MobileLocatorEngineTest {

    private static MobileNode node(String type, String text, String label, String id, MobileBounds b, List<MobileNode> children) {
        return new MobileNode(type, text, label, null, null, id, null, b, true, true, false, false, false, children, Map.of());
    }

    private static MobileNode leaf(String type, String text, double y) {
        return node(type, text, null, null, new MobileBounds(0, y, 100, 40), List.of());
    }

    private static List<String> texts(Query q, List<MobileNode> roots) {
        return q.evaluate(roots).stream().map(MobileNode::displayText).toList();
    }

    @Test
    public void rolesResolveAcrossPlatforms() {
        assertEquals(LocatorEngine.bareTypeName("android.widget.EditText"), "edittext");
        assertEquals(LocatorEngine.bareTypeName("XCUIElementTypeTextField"), "textfield");
        assertTrue(LocatorEngine.matchesRole(leaf("android.widget.EditText", "", 0), MobileRole.TEXTFIELD));
        assertTrue(LocatorEngine.matchesRole(leaf("XCUIElementTypeSecureTextField", "", 0), MobileRole.TEXTFIELD));
        assertTrue(LocatorEngine.matchesRole(leaf("Button", "", 0), MobileRole.BUTTON));
        assertFalse(LocatorEngine.matchesRole(leaf("android.widget.TextView", "", 0), MobileRole.BUTTON));
        MobileNode rnPlain = leaf("com.facebook.react.views.view.ReactViewGroup", "Go", 0);
        MobileNode rnClickable = new MobileNode("ReactViewGroup", "Go", null, null, null, null, null, MobileBounds.EMPTY,
                true, true, false, false, false, List.of(), Map.of("clickable", "true"));
        assertFalse(LocatorEngine.matchesRole(rnPlain, MobileRole.BUTTON));
        assertTrue(LocatorEngine.matchesRole(rnClickable, MobileRole.BUTTON));
        assertFalse(LocatorEngine.matchesRole(rnClickable, MobileRole.TEXT));
    }

    @Test
    public void textLabelTestIdAndRoleNameSemantics() {
        MobileNode save = node("Button", "Save", "Save changes", "com.app:id/save", new MobileBounds(0, 0, 100, 40), List.of());
        MobileNode title = node("StaticText", "Settings", null, null, new MobileBounds(0, 50, 100, 40), List.of());
        List<MobileNode> roots = List.of(save, title);

        assertEquals(texts(LocatorEngine.byText(TextMatch.exact("Settings")), roots), List.of("Settings"));
        assertEquals(texts(LocatorEngine.byText(TextMatch.contains("SETT")), roots), List.of("Settings"));
        assertEquals(texts(LocatorEngine.byText(TextMatch.regex(Pattern.compile("^Sa"))), roots), List.of("Save"));
        assertEquals(LocatorEngine.byLabel(TextMatch.exact("Save changes")).evaluate(roots), List.of(save));
        // Android "<package>:id/<name>" matches getByTestId("<name>").
        assertEquals(LocatorEngine.byTestId("save").evaluate(roots), List.of(save));
        assertEquals(LocatorEngine.byTestId("com.app:id/save").evaluate(roots), List.of(save));
        // Accessible name: label wins over text.
        assertEquals(LocatorEngine.byRole(MobileRole.BUTTON, TextMatch.exact("Save changes")).evaluate(roots), List.of(save));
        assertTrue(LocatorEngine.byRole(MobileRole.BUTTON, TextMatch.exact("Save")).evaluate(roots).isEmpty());
        assertEquals(LocatorEngine.byType("button").evaluate(roots), List.of(save), "getByType is case-insensitive, not normalised");
    }

    @Test
    public void positionalOrAndFilterAndWithin() {
        MobileNode a = leaf("Cell", "Alpha", 0);
        MobileNode b = leaf("Cell", "Beta", 50);
        MobileNode c = leaf("Cell", "Gamma", 100);
        // A flat dump: the list has no children, the cells lie inside its bounds.
        MobileNode list = node("RecyclerView", null, null, null, new MobileBounds(0, 0, 100, 90), List.of());
        MobileNode outside = leaf("Cell", "Alpha", 500);
        List<MobileNode> roots = List.of(list, a, b, c, outside);
        Query cells = LocatorEngine.byRole(MobileRole.LISTITEM, null);

        assertEquals(texts(cells, roots), List.of("Alpha", "Beta", "Gamma", "Alpha"));
        assertEquals(texts(LocatorEngine.nth(cells, 1), roots), List.of("Beta"));
        assertEquals(texts(LocatorEngine.nth(cells, -1), roots), List.of("Alpha"));
        assertTrue(LocatorEngine.nth(cells, 9).evaluate(roots).isEmpty());
        assertTrue(LocatorEngine.nth(cells, 0).positional());
        assertFalse(cells.positional());

        Query within = LocatorEngine.within(LocatorEngine.byText(TextMatch.exact("Alpha")), LocatorEngine.byType("RecyclerView"));
        assertEquals(within.evaluate(roots), List.of(a), "bounds fallback for flat dumps");

        Query or = LocatorEngine.or(LocatorEngine.byText(TextMatch.exact("Gamma")), LocatorEngine.byText(TextMatch.exact("Beta")));
        assertEquals(texts(or, roots), List.of("Beta", "Gamma"), "document order, no duplicates");
        assertEquals(texts(LocatorEngine.and(cells, LocatorEngine.byText(TextMatch.exact("Beta"))), roots), List.of("Beta"));

        MobileNode row1 = node("Cell", null, null, null, new MobileBounds(0, 0, 100, 40), List.of(leaf("StaticText", "Milk", 0)));
        MobileNode row2 = node("Cell", null, null, null, new MobileBounds(0, 50, 100, 40), List.of(leaf("StaticText", "Eggs", 50)));
        List<MobileNode> tree = List.of(row1, row2);
        assertEquals(LocatorEngine.filter(cells, TextMatch.contains("egg"), null, null, null).evaluate(tree), List.of(row2));
        assertEquals(LocatorEngine.filter(cells, null, TextMatch.contains("egg"), null, null).evaluate(tree), List.of(row1));
        assertEquals(LocatorEngine.filter(cells, null, null, LocatorEngine.byText(TextMatch.exact("Milk")), null).evaluate(tree), List.of(row1));
        assertEquals(LocatorEngine.filter(cells, null, null, null, LocatorEngine.byText(TextMatch.exact("Milk"))).evaluate(tree), List.of(row2));
    }

    // ── device selection ────────────────────────────────────────────────

    private static final List<MobileDeviceInfo> DEVICES = List.of(
            new MobileDeviceInfo("emulator-a", "Pixel 8", "android", "emulator", "online", "14", null),
            new MobileDeviceInfo("emulator-b", "Pixel 9 Pro", "android", "emulator", "online", "15", null),
            new MobileDeviceInfo("emulator-c", "Pixel 7", "android", "emulator", "offline", "13", null),
            new MobileDeviceInfo("udid-1", "iPhone 16", "ios", "simulator", "online", "18", null));

    @Test
    public void deviceSelectionIsDeterministic() {
        assertEquals(DeviceSelector.select(DEVICES, MobilePlatform.ANDROID, "emulator-b", "").id(), "emulator-b");
        assertEquals(DeviceSelector.select(DEVICES, MobilePlatform.ANDROID, "", "Pro").id(), "emulator-b");
        assertEquals(DeviceSelector.select(DEVICES, MobilePlatform.IOS, "", "").id(), "udid-1");

        DeviceSelectionException several = expectThrows(() -> DeviceSelector.select(DEVICES, MobilePlatform.ANDROID, "", ""));
        assertTrue(several.getMessage().contains("2 online android devices"), several.getMessage());
        assertTrue(several.getMessage().contains("emulator-c (Pixel 7, emulator, offline)"), "lists every candidate");
        DeviceSelectionException offline = expectThrows(() -> DeviceSelector.select(DEVICES, MobilePlatform.ANDROID, "emulator-c", ""));
        assertTrue(offline.getMessage().contains("No online android device matches mobile.deviceId=emulator-c"), offline.getMessage());
        assertTrue(expectThrows(() -> DeviceSelector.select(List.of(), MobilePlatform.ANDROID, "", "")).getMessage()
                .contains("emulator -avd"));
        expectThrows(() -> DeviceSelector.select(DEVICES, MobilePlatform.ANDROID, "", "("));
    }

    @Test
    public void poolCandidatesFollowTheConfiguredOrderAndSkipOfflineDevices() {
        assertEquals(DeviceSelector.candidates(DEVICES, MobilePlatform.ANDROID, "", "", List.of("emulator-b", "emulator-c", "emulator-a"), false)
                .stream().map(MobileDeviceInfo::id).toList(), List.of("emulator-b", "emulator-a"), "offline emulator-c is skipped");
        assertTrue(expectThrows(() -> DeviceSelector.candidates(DEVICES, MobilePlatform.ANDROID, "", "", List.of("emulator-c", "udid-1"), false))
                .getMessage().contains("None of the mobile.devices"));

        assertEquals(DeviceSelector.candidates(DEVICES, MobilePlatform.ANDROID, "", "", List.of(), true)
                .stream().map(MobileDeviceInfo::id).toList(), List.of("emulator-a", "emulator-b"), "pool: every online device");
        assertEquals(DeviceSelector.candidates(DEVICES, MobilePlatform.ANDROID, "", "Pro", List.of(), true)
                .stream().map(MobileDeviceInfo::id).toList(), List.of("emulator-b"));
        assertTrue(expectThrows(() -> DeviceSelector.candidates(DEVICES, MobilePlatform.ANDROID, "", "Galaxy", List.of(), true))
                .getMessage().contains("mobile.pool found no online android device matching"));

        // Without a pool the single-device rule still applies.
        expectThrows(() -> DeviceSelector.candidates(DEVICES, MobilePlatform.ANDROID, "", "", List.of(), false));
        assertEquals(DeviceSelector.candidates(DEVICES, MobilePlatform.IOS, "", "", List.of(), false).get(0).id(), "udid-1");
    }

    private static DeviceSelectionException expectThrows(Runnable r) {
        try {
            r.run();
        } catch (DeviceSelectionException e) {
            return e;
        }
        throw new AssertionError("expected DeviceSelectionException");
    }

    // ── reservation ─────────────────────────────────────────────────────

    @Test
    public void aDeviceServesOneSessionAtATime() throws Exception {
        String id = "reservation-" + System.nanoTime();
        DeviceReservations.Reservation first = DeviceReservations.acquire(id, Duration.ZERO);
        try {
            DeviceReservations.acquire(id, Duration.ofMillis(100));
            fail("second reservation must wait and time out");
        } catch (MobileTimeoutException expected) {
            assertTrue(expected.getMessage().contains("reserveMs"));
        }
        CompletableFuture<DeviceReservations.Reservation> waiter =
                CompletableFuture.supplyAsync(() -> DeviceReservations.acquire(id, Duration.ofSeconds(5)));
        Thread.sleep(100);
        assertFalse(waiter.isDone(), "the waiter blocks while the device is held");
        first.close();
        first.close(); // idempotent
        DeviceReservations.Reservation second = waiter.get(5, TimeUnit.SECONDS);
        second.close();
        DeviceReservations.acquire(id, Duration.ZERO).close();
    }

    @Test
    public void aPoolHandsOutFreeDevicesAndWakesWaitersOnRelease() throws Exception {
        String a = "pool-a-" + System.nanoTime();
        String b = "pool-b-" + System.nanoTime();
        List<String> pool = List.of(a, b);
        DeviceReservations.Reservation first = DeviceReservations.acquireAny(pool, Duration.ZERO);
        DeviceReservations.Reservation second = DeviceReservations.acquireAny(pool, Duration.ZERO);
        assertEquals(List.of(first.deviceId(), second.deviceId()), pool, "each session gets a different device");
        try {
            DeviceReservations.acquireAny(pool, Duration.ofMillis(100));
            fail("a third session must wait and time out");
        } catch (MobileTimeoutException expected) {
            assertTrue(expected.getMessage().contains("All 2 pool devices"), expected.getMessage());
        }
        CompletableFuture<DeviceReservations.Reservation> waiter =
                CompletableFuture.supplyAsync(() -> DeviceReservations.acquireAny(pool, Duration.ofSeconds(5)));
        Thread.sleep(100);
        assertFalse(waiter.isDone());
        second.close();
        DeviceReservations.Reservation third = waiter.get(5, TimeUnit.SECONDS);
        assertEquals(third.deviceId(), b, "the waiter gets the released device");
        first.close();
        third.close();
    }

    @Test
    public void manyThreadsNeverShareAPoolDevice() throws Exception {
        List<String> pool = List.of("par-a-" + System.nanoTime(), "par-b-" + System.nanoTime(), "par-c-" + System.nanoTime());
        java.util.Set<String> inUse = java.util.concurrent.ConcurrentHashMap.newKeySet();
        java.util.concurrent.atomic.AtomicInteger clashes = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(8);
        List<java.util.concurrent.Future<?>> jobs = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            jobs.add(workers.submit(() -> {
                try (DeviceReservations.Reservation r = DeviceReservations.acquireAny(pool, Duration.ofSeconds(10))) {
                    if (!inUse.add(r.deviceId())) clashes.incrementAndGet();
                    Thread.sleep(5);
                    inUse.remove(r.deviceId());
                }
                return null;
            }));
        }
        for (java.util.concurrent.Future<?> job : jobs) {
            job.get(30, TimeUnit.SECONDS);
        }
        workers.shutdown();
        assertEquals(clashes.get(), 0, "no device was held by two sessions at once");
    }
}
