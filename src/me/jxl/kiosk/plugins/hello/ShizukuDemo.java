// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import me.jxl.kiosk.plugins.PluginHost;

/** Optional device diagnostics. Uses fixed read-only commands and never requests permission. */
final class ShizukuDemo {
    private final LongSupplier clock;
    private PluginHost host;
    private boolean enabled;
    private boolean pending;
    private String diagnostic = "Process identity";
    private long revision;
    private long requestId;
    private long nextRead;
    private long lastRequest;

    ShizukuDemo() { this(System::nanoTime); }
    ShizukuDemo(LongSupplier clock) { this.clock = clock; }

    synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        configure(settings);
    }

    synchronized void configure(Map<String, Object> settings) {
        boolean nextEnabled = Boolean.TRUE.equals(settings.get("shizukuDemo"));
        String nextDiagnostic = String.valueOf(settings.get("shizukuDiagnostic"));
        if (!"Android version".equals(nextDiagnostic) && !"Kernel version".equals(nextDiagnostic)) {
            nextDiagnostic = "Process identity";
        }
        if (enabled != nextEnabled || !diagnostic.equals(nextDiagnostic)) {
            revision++;
            nextRead = 0;
            clearOutput();
        }
        enabled = nextEnabled;
        diagnostic = nextDiagnostic;
        tick();
    }

    synchronized void stateChanged() {
        nextRead = 0;
        tick();
    }

    synchronized void tick() {
        if (host == null) return;
        if (!enabled) {
            state("Demo off");
            clearOutput();
            return;
        }
        try {
            Map<String, Object> connection = host.shizukuState();
            if (!Boolean.TRUE.equals(connection.get("granted"))) {
                revision++;
                nextRead = 0;
                clearOutput();
                state("permission_required".equals(connection.get("status")) || "denied".equals(connection.get("status"))
                    ? "Allow Kiosk Satellite in Shizuku access above."
                    : "Start Shizuku and grant Kiosk Satellite access.");
                return;
            }
            long now = clock.getAsLong();
            if (pending || now < nextRead || (lastRequest != 0 && now - lastRequest < TimeUnit.MILLISECONDS.toNanos(300))) return;
            final PluginHost owner = host;
            final long token = ++requestId;
            final long selectedRevision = revision;
            final String selected = diagnostic;
            String[] command = "Android version".equals(selected)
                ? new String[] {"/system/bin/getprop", "ro.build.version.release"}
                : "Kernel version".equals(selected)
                ? new String[] {"/system/bin/uname", "-r"}
                : new String[] {"/system/bin/id"};
            pending = true;
            lastRequest = now;
            nextRead = now + TimeUnit.SECONDS.toNanos(10);
            state("Reading " + selected.toLowerCase(java.util.Locale.ROOT) + "...");
            try {
                owner.executeShizuku(command, 2000, (ok, data, error) -> {
                    synchronized (ShizukuDemo.this) {
                        if (host != owner || token != requestId) return;
                        pending = false;
                        if (!enabled || revision != selectedRevision) return;
                        try {
                            if (!Boolean.TRUE.equals(owner.shizukuState().get("granted"))) {
                                stateChanged();
                                return;
                            }
                            Map<?, ?> result = data instanceof Map ? (Map<?, ?>) data : null;
                            if (!ok || result == null) {
                                failed(error == null ? "Shizuku request failed." : error);
                            } else if (Boolean.TRUE.equals(result.get("timedOut"))) {
                                failed("The diagnostic timed out.");
                            } else if (!(result.get("exitCode") instanceof Number) || ((Number) result.get("exitCode")).intValue() != 0) {
                                failed("The diagnostic command failed.");
                            } else if (Boolean.TRUE.equals(result.get("truncated"))) {
                                failed("The diagnostic output exceeded the capture limit.");
                            } else {
                                String output = String.valueOf(result.get("stdout")).trim();
                                owner.publishTextSensor("shizuku_output", "Shizuku result",
                                    output.substring(0, Math.min(512, output.length())));
                                state(selected + " read through Shizuku");
                            }
                        } catch (RuntimeException failure) {
                            failed("Shizuku is unavailable. Start it and grant Kiosk Satellite access.");
                        }
                    }
                });
            } catch (RuntimeException failure) {
                pending = false;
                failed("Shizuku could not run the diagnostic. Retrying while the demo is enabled.");
            }
        } catch (RuntimeException failure) {
            clearOutput();
            state("Shizuku is unavailable. Start it and grant Kiosk Satellite access.");
        }
    }

    private void state(String value) { host.publishTextSensor("shizuku_status", "Shizuku demo status", value); }
    private void clearOutput() { if (host != null) host.publishTextSensor("shizuku_output", "Shizuku result", null); }
    private void failed(String message) { clearOutput(); state(message); }

    synchronized void stop() {
        revision++;
        requestId++;
        host = null;
        pending = false;
        enabled = false;
        nextRead = 0;
        lastRequest = 0;
    }
}
