package io.testfly.inspector;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** adb for what mobilecli leaves out: unauthorized phones, and Wi-Fi pairing. As src/adb.ts in the VS Code extension. */
final class Adb {

    record Device(String serial, String state, String model) {
    }

    private static final Pattern ADDRESS = Pattern.compile("^[\\w.:\\-\\[\\]%]+$");

    private Adb() {
    }

    /** adb from ANDROID_HOME / ANDROID_SDK_ROOT, the SDK's default location, or PATH. */
    static String find(Map<String, String> env, String os) {
        boolean windows = os.toLowerCase().contains("win");
        String exe = windows ? "adb.exe" : "adb";
        String home = System.getProperty("user.home");
        List<String> sdks = new ArrayList<>();
        sdks.add(env.get("ANDROID_HOME"));
        sdks.add(env.get("ANDROID_SDK_ROOT"));
        if (os.toLowerCase().contains("mac")) {
            sdks.add(home + "/Library/Android/sdk");
        } else if (windows) {
            sdks.add(env.get("LOCALAPPDATA") == null ? null : env.get("LOCALAPPDATA") + "\\Android\\Sdk");
        } else {
            sdks.add(home + "/Android/Sdk");
        }
        for (String sdk : sdks) {
            if (sdk != null && new File(sdk, "platform-tools" + File.separator + exe).isFile()) {
                return new File(sdk, "platform-tools" + File.separator + exe).getPath();
            }
        }
        return exe;
    }

    static String find() {
        return find(System.getenv(), System.getProperty("os.name"));
    }

    /** Parses {@code adb devices -l}. */
    static List<Device> parse(String output) {
        List<Device> out = new ArrayList<>();
        String[] lines = output.split("\\n");
        for (int i = 1; i < lines.length; i++) {
            String[] parts = lines[i].trim().split("\\s+");
            if (parts.length < 2 || parts[0].isEmpty() || parts[0].startsWith("*")) {
                continue;
            }
            String model = null;
            for (String p : parts) {
                if (p.startsWith("model:")) {
                    model = p.substring(6).replace('_', ' ');
                }
            }
            out.add(new Device(parts[0], parts[1], model));
        }
        return out;
    }

    static List<Device> devices(String adb) throws IOException {
        return parse(run(adb, 15, "devices", "-l"));
    }

    /** {@code adb pair host:port code} with a pairing code, else {@code adb connect host:port}. */
    static String wifi(String adb, String address, String code) throws IOException {
        String addr = address.trim();
        if (!ADDRESS.matcher(addr).matches()) {
            throw new IOException("'" + address + "' is not a host:port address");
        }
        if (code != null && !code.isBlank()) {
            if (!code.trim().matches("\\d{6}")) {
                throw new IOException("The pairing code has 6 digits");
            }
            String out = run(adb, 30, "pair", addr, code.trim());
            if (!out.toLowerCase().contains("success")) {
                throw new IOException(out.isEmpty() ? "adb pair failed" : out);
            }
            return out;
        }
        String out = run(adb, 20, "connect", addr.contains(":") ? addr : addr + ":5555");
        if (out.toLowerCase().matches("(?s).*(cannot|failed|refused|unable).*")) {
            throw new IOException(out);
        }
        return out;
    }

    static String disconnect(String adb, String serial) throws IOException {
        return run(adb, 15, "disconnect", serial);
    }

    private static String run(String adb, int timeoutSeconds, String... args) throws IOException {
        List<String> command = new ArrayList<>(List.of(adb));
        command.addAll(List.of(args));
        Process p;
        try {
            p = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new IOException("adb was not found: install the Android SDK platform-tools or set ANDROID_HOME", e);
        }
        try {
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("adb " + args[0] + " did not finish in " + timeoutSeconds + " s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    }
}
