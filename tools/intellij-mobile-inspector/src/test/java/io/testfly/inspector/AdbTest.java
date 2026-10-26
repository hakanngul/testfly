package io.testfly.inspector;

import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class AdbTest {

    @Test
    public void parsesUnauthorizedAndWirelessDevices() {
        List<Adb.Device> devices = Adb.parse("""
                List of devices attached
                emulator-5554          device product:sdk model:sdk_gphone64 transport_id:1
                R5CT1234ABC            unauthorized usb:1-1 transport_id:3
                192.168.1.20:37099     device model:Pixel_6 transport_id:4
                * daemon started successfully
                """);
        assertEquals(List.of(new Adb.Device("emulator-5554", "device", "sdk gphone64"), new Adb.Device("R5CT1234ABC", "unauthorized", null),
                new Adb.Device("192.168.1.20:37099", "device", "Pixel 6")), devices);
    }

    @Test
    public void wifiChecksItsInputAndFindsNoAdb() {
        assertEquals("adb", Adb.find(Map.of("ANDROID_HOME", "/nope"), "Linux"));
        assertThrows(IOException.class, () -> Adb.wifi("adb", "1.2.3.4:5; rm -rf /", null));
        IOException code = assertThrows(IOException.class, () -> Adb.wifi("adb", "1.2.3.4:5555", "12ab"));
        assertTrue(code.getMessage().contains("6 digits"));
        IOException missing = assertThrows(IOException.class, () -> Adb.wifi("/nonexistent/adb", "1.2.3.4:5555", ""));
        assertTrue(missing.getMessage().contains("adb was not found"));
    }
}
