// SPDX-License-Identifier: Apache-2.0
package example.kiosk;

import java.util.Map;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;

/** A minimal Shizuku example without the other Hello World demonstrations. */
public final class ShizukuPlugin implements KioskPlugin {
    private PluginHost host;
    private boolean pending;
    public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        update(host.shizukuState());
    }
    public void configure(Map<String, Object> settings) {}
    public void execute(String command, Map<String, Object> arguments) {}
    public synchronized void onEvent(String event, Map<String, Object> payload) {
        if ("shizuku.state".equals(event)) update(payload);
    }
    private void update(Map<String, Object> state) {
        if (host == null) return;
        boolean ready = Boolean.TRUE.equals(state.get("granted"));
        host.publishBinarySensor("connected", "Shizuku access", "connectivity", ready);
        if (!ready) {
            host.publishTextSensor("identity", "Process identity", null);
            host.status("Open Shizuku access above to connect. This example only reads the process identity.", false);
            return;
        }
        if (pending) return;
        pending = true;
        try {
            host.executeShizuku(new String[] {"/system/bin/id"}, 2000, (ok, data, error) -> {
                synchronized (ShizukuPlugin.this) {
                    pending = false;
                    if (host == null) return;
                    if (!ok) {
                        host.publishTextSensor("identity", "Process identity", null);
                        host.status(error == null ? "Shizuku request failed." : error, true);
                        return;
                    }
                    Map<?, ?> result = (Map<?, ?>) data;
                    if (Boolean.TRUE.equals(result.get("timedOut")) || ((Number) result.get("exitCode")).intValue() != 0) {
                        host.publishTextSensor("identity", "Process identity", null);
                        host.status("The identity command did not finish successfully.", true);
                        return;
                    }
                    // Permission may have changed while the request was running.
                    if (!Boolean.TRUE.equals(host.shizukuState().get("granted"))) return;
                    String identity = String.valueOf(result.get("stdout")).trim();
                    host.publishTextSensor("identity", "Process identity", identity.substring(0, Math.min(512, identity.length())));
                    host.status("Read the Shizuku process identity. No device settings were changed.", false);
                }
            });
        } catch (RuntimeException error) {
            pending = false;
            host.status(error.getMessage() == null ? "Shizuku request failed." : error.getMessage(), true);
        }
    }
    public synchronized void stop() { host = null; pending = false; }
}
