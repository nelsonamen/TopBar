// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.*;
import me.jxl.kiosk.plugins.PluginHost;

public final class HomeAssistantDemoTest {
    static final class Host implements PluginHost {
        final Set<String> subscriptions = new HashSet<>();
        final Map<String, String> values = new HashMap<>();
        public void subscribe(String event) { subscriptions.add(event); }
        public void unsubscribe(String event) { subscriptions.remove(event); }
        public void publishTextSensor(String key, String name, String value) { assert key.matches("[a-z][a-z0-9_]{0,39}"); assert value.length() <= 512; values.put(key, value); }
        public void removeTextSensor(String key) { values.remove(key); }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}
        public void status(String message, boolean error) {}
    }
    public static void main(String[] args) {
        Host host = new Host();
        HomeAssistantDemo demo = new HomeAssistantDemo();
        demo.configure(host, Collections.singletonMap("haEntity", "sensor.room"));
        assert host.subscriptions.contains("ha.entity.sensor.room");
        Map<String, Object> state = new HashMap<>();
        state.put("status", "available"); state.put("state", "21");
        state.put("attributes", Collections.singletonMap("unit_of_measurement", "C"));
        demo.onEvent("ks.ha.entity.sensor.room", state);
        assert "21".equals(host.values.get("ha_state"));
        demo.configure(host, Collections.singletonMap("haEntity", "sensor.other"));
        assert !host.subscriptions.contains("ha.entity.sensor.room");
        demo.onEvent("ks.ha.entity.sensor.room", state);
        assert "Connecting".equals(host.values.get("ha_state"));
        state.put("status", "disconnected"); state.put("state", null);
        demo.onEvent("ks.ha.entity.sensor.other", state);
        assert "disconnected".equals(host.values.get("ha_state"));
        demo.configure(host, Collections.singletonMap("haEntity", ""));
        assert host.values.isEmpty() && host.subscriptions.isEmpty();
        demo.stop();
        demo.onEvent("ks.ha.entity.sensor.other", state);
        assert host.values.isEmpty();
    }
}
