// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import me.jxl.kiosk.plugins.PluginHost;

public final class ShizukuDemoTest {
    static final class Host implements PluginHost {
        boolean ready;
        boolean reject;
        int requests;
        String[] command;
        CommandCallback callback;
        final Map<String, String> readings = new HashMap<>();
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}
        public void publishTextSensor(String key, String name, String value) { readings.put(key, value); }
        public Map<String, Object> shizukuState() {
            Map<String, Object> state = new HashMap<>();
            state.put("granted", ready);
            state.put("status", ready ? "ready" : "permission_required");
            return state;
        }
        public void executeShizuku(String[] command, int timeout, CommandCallback callback) {
            assert ready && timeout == 2000;
            requests++;
            if (reject) throw new IllegalStateException("Busy");
            this.command = command;
            this.callback = callback;
        }
        String output() { return readings.get("shizuku_output"); }
        String state() { return readings.get("shizuku_status"); }
    }
    static Map<String, Object> settings(boolean enabled, String diagnostic) {
        Map<String, Object> settings = new HashMap<>();
        settings.put("shizukuDemo", enabled); settings.put("shizukuDiagnostic", diagnostic);
        return settings;
    }
    static Map<String, Object> result(String output, int exit, boolean timeout, boolean truncated) {
        Map<String, Object> result = new HashMap<>();
        result.put("stdout", output); result.put("exitCode", exit);
        result.put("timedOut", timeout); result.put("truncated", truncated);
        return result;
    }
    public static void main(String[] args) {
        Host host = new Host();
        AtomicLong clock = new AtomicLong(TimeUnit.SECONDS.toNanos(1));
        ShizukuDemo demo = new ShizukuDemo(clock::get);
        demo.start(host, settings(false, "Process identity"));
        assert host.requests == 0 && "Demo off".equals(host.state());
        demo.configure(settings(true, "Process identity"));
        assert host.requests == 0 && host.state().contains("Allow Kiosk Satellite");
        host.ready = true; demo.stateChanged();
        assert host.requests == 1 && Arrays.equals(host.command, new String[] {"/system/bin/id"});
        demo.tick(); assert host.requests == 1 : "Started overlapping commands";
        host.callback.onResult(true, result("uid=2000(shell)\n", 0, false, false), null);
        assert "uid=2000(shell)".equals(host.output());
        clock.addAndGet(TimeUnit.SECONDS.toNanos(9)); demo.tick(); assert host.requests == 1;
        clock.addAndGet(TimeUnit.SECONDS.toNanos(1)); demo.tick(); assert host.requests == 2;
        PluginHost.CommandCallback old = host.callback;
        demo.configure(settings(true, "Android version"));
        old.onResult(true, result("stale identity", 0, false, false), null);
        assert host.output() == null;
        clock.addAndGet(TimeUnit.SECONDS.toNanos(1)); demo.tick();
        assert Arrays.equals(host.command, new String[] {"/system/bin/getprop", "ro.build.version.release"});
        host.callback.onResult(true, result("16", 0, false, false), null);
        assert "16".equals(host.output());
        clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
        demo.configure(settings(true, "Kernel version"));
        assert Arrays.equals(host.command, new String[] {"/system/bin/uname", "-r"});
        host.callback.onResult(true, result("partial", 0, true, false), null);
        assert host.output() == null && host.state().contains("timed out");
        for (int kind = 0; kind < 3; kind++) {
            clock.addAndGet(TimeUnit.SECONDS.toNanos(10)); demo.tick();
            host.callback.onResult(kind != 2, result("partial", kind == 0 ? 1 : 0, false, kind == 1), "Service lost");
            assert host.output() == null;
        }
        clock.addAndGet(TimeUnit.SECONDS.toNanos(10)); demo.tick();
        old = host.callback;
        demo.configure(settings(false, "Kernel version"));
        old.onResult(true, result("late output", 0, false, false), null);
        assert host.output() == null && "Demo off".equals(host.state());
        clock.addAndGet(TimeUnit.SECONDS.toNanos(10));
        demo.configure(settings(true, "Kernel version"));
        host.callback.onResult(true, result("kernel", 0, false, false), null);
        host.ready = false; demo.stateChanged();
        assert host.output() == null;
        host.ready = true; clock.addAndGet(TimeUnit.SECONDS.toNanos(1)); demo.stateChanged();
        old = host.callback;
        demo.stop();
        demo.start(host, settings(true, "Process identity"));
        int requests = host.requests;
        old.onResult(true, result("previous session", 0, false, false), null);
        demo.tick(); assert host.requests == requests && host.output() == null;
        host.callback.onResult(true, result("new session", 0, false, false), null);
        assert "new session".equals(host.output());
        clock.addAndGet(TimeUnit.SECONDS.toNanos(10)); host.reject = true; demo.tick();
        assert host.output() == null && host.state().contains("Retrying");
        host.reject = false; clock.addAndGet(TimeUnit.SECONDS.toNanos(10)); demo.tick();
        host.callback.onResult(true, result("recovered", 0, false, false), null);
        assert "recovered".equals(host.output());
        demo.stop(); requests = host.requests; demo.tick(); assert host.requests == requests;
        System.out.println("Shizuku opt-in, diagnostics, throttling, failures, stale callbacks and reconnects passed.");
    }
}
