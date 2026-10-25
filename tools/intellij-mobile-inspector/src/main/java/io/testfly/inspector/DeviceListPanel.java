package io.testfly.inspector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The "TestFly Mobile" tool window: every Android emulator and device, iOS simulator and iPhone that mobilecli
 * knows, online or not, with Inspect, Boot and Shut down; phones adb sees but mobilecli leaves out (unauthorized)
 * are listed too. The list is read again every 10 seconds while the tool window is shown.
 */
final class DeviceListPanel extends JPanel implements Disposable {

    private static final Color READY = new JBColor(0x2E8B57, 0x5FB97A);
    private static final Color WARN = new JBColor(0xB07800, 0xD9A43B);
    private static final Color BUSY = new JBColor(0x2F6FD6, 0x4C9BF0);

    private final Project project;
    private final ToolWindow toolWindow;
    private final JPanel list = new JPanel();
    private final JBLabel server = new JBLabel();
    private final Timer poll;
    private final Map<String, String> busy = new HashMap<>(); // device id -> "Booting" / "Shutting down"
    private final Set<String> warned = new HashSet<>();
    private MobileCliClient cli;
    private volatile boolean listing;

    DeviceListPanel(Project project, ToolWindow toolWindow) {
        super(new BorderLayout());
        this.project = project;
        this.toolWindow = toolWindow;
        setBorder(JBUI.Borders.empty(10, 12));

        JPanel head = new JPanel(new BorderLayout());
        JBLabel title = new JBLabel("DEVICES");
        title.setFont(JBUI.Fonts.smallFont().asBold());
        title.setForeground(UIUtil.getContextHelpForeground());
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.add(title);
        left.add(server);
        head.add(left, BorderLayout.WEST);
        head.add(new ActionLink("Reload", (java.awt.event.ActionListener) e -> reload(false)), BorderLayout.EAST);
        head.setBorder(JBUI.Borders.emptyBottom(6));
        add(head, BorderLayout.NORTH);

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        JPanel holder = new JPanel(new BorderLayout());
        holder.add(list, BorderLayout.NORTH);
        JBScrollPane scroll = new JBScrollPane(holder);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        JPanel foot = new JPanel();
        foot.setLayout(new BoxLayout(foot, BoxLayout.Y_AXIS));
        foot.setBorder(JBUI.Borders.emptyTop(10));
        JButton open = new JButton("Open Mobile Inspector", InspectorFile.ICON);
        open.putClientProperty("JButton.buttonType", "default");
        open.addActionListener(e -> OpenInspectorAction.open(project));
        ActionLink wifi = new ActionLink("Connect Android over Wi-Fi…", (java.awt.event.ActionListener) e -> connectWifi());
        ActionLink help = new ActionLink("A device is missing?", (java.awt.event.ActionListener) e -> Messages.showInfoMessage(project, HELP, "A Device Is Missing"));
        ActionLink settings = new ActionLink("Settings", (java.awt.event.ActionListener) e ->
                ShowSettingsUtil.getInstance().showSettingsDialog(project, InspectorConfigurable.class));
        for (JComponent c : new JComponent[] {open, wifi, help, settings}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            foot.add(c);
            foot.add(Box.createVerticalStrut(6));
        }
        add(foot, BorderLayout.SOUTH);

        message("Looking for devices…");
        poll = new Timer(10_000, e -> {
            if ((toolWindow == null || toolWindow.isVisible()) && busy.isEmpty()) {
                reload(true);
            }
        });
        poll.start();
        reload(false);
    }

    private static final String HELP = """
            Android phone: Developer options → USB debugging on, then accept "Allow USB debugging" on the phone. \
            A phone waiting for that is listed as UNAUTHORIZED.

            iPhone: unlock it, tap "Trust This Computer" and turn on Settings → Privacy & Security → Developer Mode. \
            Inspecting needs mobilecli's agent once: mobilecli agent install --device <udid> --provisioning-profile <file>.

            iPhone over Wi-Fi: connect it by cable once and enable "Connect via network" in Xcode → Devices and Simulators; \
            then it is listed on the same network.

            Simulators are listed after their first boot; emulators from the Android SDK's AVDs.""";

    private MobileCliClient client() {
        InspectorSettings.Data s = InspectorSettings.get().getState();
        if (cli == null) {
            cli = new MobileCliClient(s.serverUrl, s.mobilecliPath, s.autoStartServer);
        }
        return cli;
    }

    /** quiet: a poll, which never starts the server. */
    private void reload(boolean quiet) {
        if (listing) {
            return;
        }
        listing = true;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                MobileCliClient c = client();
                String version = quiet ? c.ensureServer(Duration.ofSeconds(20), false) : c.ensureServer(Duration.ofSeconds(20));
                JsonArray devices = c.allDevices();
                addUnauthorized(devices);
                ApplicationManager.getApplication().invokeLater(() -> show(devices, version, null));
            } catch (RuntimeException e) {
                ApplicationManager.getApplication().invokeLater(() -> show(new JsonArray(), "", e.getMessage()));
            } finally {
                listing = false;
            }
        });
    }

    private static void addUnauthorized(JsonArray devices) {
        try {
            Set<String> known = new HashSet<>();
            devices.forEach(d -> known.add(MobileCliClient.str(d.getAsJsonObject(), "id")));
            for (Adb.Device a : Adb.devices(Adb.find())) {
                if (!a.state().equals("device") && !known.contains(a.serial())) {
                    JsonObject d = new JsonObject();
                    d.addProperty("id", a.serial());
                    d.addProperty("name", a.model() != null ? a.model() : a.serial());
                    d.addProperty("platform", "android");
                    d.addProperty("type", a.serial().startsWith("emulator-") ? "emulator" : "real");
                    d.addProperty("state", a.state());
                    devices.add(d);
                }
            }
        } catch (java.io.IOException e) {
            // no adb here: mobilecli's list is all there is
        }
    }

    private void show(JsonArray devices, String version, String error) {
        server.setIcon(error == null ? AllIcons.General.InspectionsOK : AllIcons.General.Error);
        server.setToolTipText(error == null ? "mobilecli server " + version + " is running" : error);
        list.removeAll();
        if (error != null) {
            message("<html>" + escape(error) + "</html>").setForeground(JBColor.RED);
        }
        List<JsonObject> all = new ArrayList<>();
        devices.forEach(d -> all.add(d.getAsJsonObject()));
        if (error == null && all.isEmpty()) {
            message("<html>No device, emulator or simulator found. See <i>A device is missing?</i></html>");
        }
        group("Android", all, d -> "android".equals(MobileCliClient.str(d, "platform")));
        group("iOS simulators", all, d -> "ios".equals(MobileCliClient.str(d, "platform")) && "simulator".equals(MobileCliClient.str(d, "type")));
        group("iOS devices", all, d -> "ios".equals(MobileCliClient.str(d, "platform")) && !"simulator".equals(MobileCliClient.str(d, "type")));
        list.revalidate();
        list.repaint();
    }

    private JBLabel message(String text) {
        JBLabel l = new JBLabel(text);
        l.setForeground(UIUtil.getContextHelpForeground());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        list.add(l);
        return l;
    }

    private void group(String title, List<JsonObject> all, Predicate<JsonObject> test) {
        List<JsonObject> items = all.stream().filter(test)
                .sorted(Comparator.comparingInt(DeviceListPanel::rank).thenComparing(d -> MobileCliClient.str(d, "name"))).toList();
        if (items.isEmpty()) {
            return;
        }
        JBLabel h = new JBLabel(title);
        h.setForeground(UIUtil.getContextHelpForeground());
        h.setBorder(JBUI.Borders.empty(8, 0, 2, 0));
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        list.add(h);
        items.forEach(d -> list.add(row(d)));
    }

    private static int rank(JsonObject d) {
        String state = MobileCliClient.str(d, "state");
        return state.equals("online") ? 0 : state.equals("offline") ? 2 : 1;
    }

    private JComponent row(JsonObject d) {
        String id = MobileCliClient.str(d, "id");
        String name = MobileCliClient.str(d, "name");
        String platform = MobileCliClient.str(d, "platform");
        String type = MobileCliClient.str(d, "type");
        String state = MobileCliClient.str(d, "state");
        String version = MobileCliClient.str(d, "version");
        boolean online = state.equals("online");
        boolean virtual = type.equals("emulator") || type.equals("simulator");
        boolean wifi = platform.equals("android") && (id.matches(".*:\\d+$") || id.contains("_adb-tls-connect"));
        String doing = busy.get(id);

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBorder(JBUI.Borders.empty(4, 2));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        Icon icon = platform.equals("android") ? AllIcons.Nodes.Plugin : type.equals("simulator") ? AllIcons.Nodes.Desktop : AllIcons.Nodes.Module;
        row.add(new JBLabel(icon), BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JBLabel nameLabel = new JBLabel(name.isEmpty() ? id : name);
        String kind = type.equals("emulator") ? "Emulator" : type.equals("simulator") ? "Simulator" : "Device";
        String status = doing != null ? doing.toUpperCase() + "…" : online ? "READY" : state.equals("offline") ? "" : state.toUpperCase();
        JBLabel sub = new JBLabel((status.isEmpty() ? "" : status + "  ") + (wifi && online ? "Wi-Fi  " : "")
                + (platform.equals("ios") ? "iOS" : "Android") + (version.isEmpty() ? "" : " " + version) + " · " + kind + " · " + id);
        sub.setFont(JBUI.Fonts.smallFont());
        sub.setForeground(doing != null ? BUSY : online ? READY : state.equals("offline") ? UIUtil.getContextHelpForeground() : WARN);
        if (!online) {
            nameLabel.setForeground(UIUtil.getContextHelpForeground());
        }
        text.add(nameLabel);
        text.add(sub);
        text.setToolTipText(state.equals("unauthorized") ? "Accept \"Allow USB debugging\" on the phone" : name + " · " + id);
        row.add(text, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        if (online) {
            actions.add(button("Inspect", null, doing, () -> OpenInspectorAction.open(project, id)));
            if (virtual) {
                actions.add(button(null, AllIcons.Actions.Suspend, doing, () -> act(id, "Shutting down", () -> client().shutdown(id))));
            }
            if (wifi) {
                actions.add(button(null, AllIcons.Actions.Cancel, doing, () -> act(id, "Disconnecting", () -> {
                    try {
                        Adb.disconnect(Adb.find(), id);
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException(e.getMessage(), e);
                    }
                })));
            }
        } else if (state.equals("offline") && virtual) {
            actions.add(button("Boot", null, doing, () -> act(id, "Booting", () -> client().boot(id))));
        }
        row.add(actions, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private static JButton button(String label, Icon icon, String doing, Runnable action) {
        JButton b = label != null ? new JButton(label) : new JButton(icon);
        if (label == null) {
            b.setToolTipText(icon == AllIcons.Actions.Suspend ? "Shut down" : "Disconnect (adb disconnect)");
            b.setPreferredSize(new Dimension(JBUI.scale(28), b.getPreferredSize().height));
        }
        b.setEnabled(doing == null);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void act(String id, String label, Runnable action) {
        busy.put(id, label);
        reload(true);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String error = null;
            try {
                action.run();
            } catch (RuntimeException e) {
                error = e.getMessage();
            }
            String failure = error;
            ApplicationManager.getApplication().invokeLater(() -> {
                busy.remove(id);
                if (failure != null) {
                    Messages.showErrorDialog(project, failure, label + " " + id + " failed");
                }
                reload(true);
            });
        });
    }

    private void connectWifi() {
        String address = Messages.showInputDialog(project,
                "On the phone: Developer options → Wireless debugging (Android 11+), on the same network.\n"
                        + "To pair: the address under \"Pair device with pairing code\". To connect: the address on the Wireless debugging screen.",
                "Connect Android over Wi-Fi", null, "192.168.1.20:37099", null);
        if (address == null || address.isBlank()) {
            return;
        }
        String code = Messages.showInputDialog(project, "Pairing code (6 digits) to pair; leave empty to connect.", "Connect Android over Wi-Fi", null);
        if (code == null) {
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String result;
            boolean ok = true;
            try {
                result = Adb.wifi(Adb.find(), address, code);
                if (!code.isBlank()) {
                    result += "\nNow connect with the address on the Wireless debugging screen (without a code).";
                }
            } catch (java.io.IOException e) {
                result = e.getMessage();
                ok = false;
            }
            String text = result;
            boolean success = ok;
            ApplicationManager.getApplication().invokeLater(() -> {
                if (success) {
                    Messages.showInfoMessage(project, text, "Connect Android over Wi-Fi");
                } else {
                    Messages.showErrorDialog(project, text, "Connect Android over Wi-Fi");
                }
                reload(true);
            });
        });
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public void dispose() {
        poll.stop();
        if (cli != null) {
            cli.close();
        }
    }
}
