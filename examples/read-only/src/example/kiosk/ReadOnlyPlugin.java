// SPDX-License-Identifier: Apache-2.0
package example.kiosk;

import java.util.Collections;
import java.util.Map;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;

/** Observes KS without changing its screen or screensaver. */
public final class ReadOnlyPlugin implements KioskPlugin {
    private PluginHost host;
    private boolean alive;
    private int screenRevision;
    private int saverRevision;
    private int dashboardRevision;
    private String dashboardPath;
    private Boolean screenOn;
    private Boolean screensaverActive;

    @Override public void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        alive = true;
        host.subscribe("screen.state");
        host.subscribe("screensaver.state");
        host.subscribe("browser.state");
        readDashboard();
        read("isScreenOn", true);
        read("isScreensaverActive", false);
    }

    private void read(String command, boolean screen) {
        final int revision = screen ? screenRevision : saverRevision;
        host.executeCommand(command, Collections.emptyMap(), (ok, data, error) -> {
            if (!alive) return;
            if (!ok) { host.status(error, true); return; }
            if (revision != (screen ? screenRevision : saverRevision)) return;
            if (screen) screenOn = Boolean.TRUE.equals(data);
            else screensaverActive = Boolean.TRUE.equals(data);
            showStatus();
        });
    }

    private void readDashboard() {
        final int revision = ++dashboardRevision;
        host.executeCommand("getDashboardState", Collections.emptyMap(), (ok, data, error) -> {
            if (!alive || revision != dashboardRevision) return;
            if (!ok) { host.status(error, true); return; }
            Map<?, ?> state = (Map<?, ?>) data;
            dashboardPath = (String) state.get("currentPath");
            showStatus();
        });
    }

    private void showStatus() {
        if (screenOn == null || screensaverActive == null) return;
        host.status("Screen " + (screenOn ? "on" : "off") + ". Screensaver "
            + (screensaverActive ? "active." : "idle.")
            + " Dashboard: " + (dashboardPath == null ? "waiting for a page." : dashboardPath), false);
    }

    @Override public void onEvent(String event, Map<String, Object> payload) {
        if (event.equals("ks.browser.state")) { readDashboard(); return; }
        if (event.equals("ks.screen.state")) { screenRevision++; screenOn = Boolean.TRUE.equals(payload.get("on")); }
        if (event.equals("ks.screensaver.state")) { saverRevision++; screensaverActive = Boolean.TRUE.equals(payload.get("active")); }
        showStatus();
    }
    @Override public void configure(Map<String, Object> settings) {}
    @Override public void execute(String command, Map<String, Object> arguments) {
        throw new IllegalArgumentException("This example has no actions");
    }
    @Override public void stop() {
        alive = false;
        // KS has already revoked reads and subscriptions before calling stop.
    }
}
