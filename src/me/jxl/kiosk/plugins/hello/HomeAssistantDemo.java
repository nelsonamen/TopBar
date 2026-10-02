// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.Map;
import me.jxl.kiosk.plugins.PluginHost;

/** A selected HA entity observed through KS without handling credentials. */
final class HomeAssistantDemo {
    private PluginHost host;
    private String entity = "";

    void configure(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        Object configured = settings.get("haEntity");
        String next = configured instanceof String ? (String) configured : "";
        if (next.equals(entity)) return;
        if (!entity.isEmpty()) host.unsubscribe("ha.entity." + entity);
        entity = next;
        if (entity.isEmpty()) {
            host.removeTextSensor("ha_state");
            host.removeTextSensor("ha_details");
            return;
        }
        host.publishTextSensor("ha_state", "Home Assistant state", "Connecting");
        host.publishTextSensor("ha_details", "Home Assistant details", "Entity: " + entity);
        host.subscribe("ha.entity." + entity);
    }

    void onEvent(String event, Map<String, Object> payload) {
        if (host == null || entity.isEmpty() || !event.equals("ks.ha.entity." + entity)) return;
        String status = String.valueOf(payload.get("status"));
        Object state = payload.get("state");
        String value = "available".equals(status) && state != null ? String.valueOf(state) : status;
        host.publishTextSensor("ha_state", "Home Assistant state", value.substring(0, Math.min(512, value.length())));
        Map<?, ?> attributes = payload.get("attributes") instanceof Map ? (Map<?, ?>) payload.get("attributes") : java.util.Collections.emptyMap();
        String details = "Entity: " + entity;
        if (attributes.get("friendly_name") != null) details += "\nName: " + attributes.get("friendly_name");
        if (attributes.get("unit_of_measurement") != null) details += "\nUnit: " + attributes.get("unit_of_measurement");
        if (payload.get("lastUpdated") != null) details += "\nUpdated: " + payload.get("lastUpdated");
        host.publishTextSensor("ha_details", "Home Assistant details", details.substring(0, Math.min(512, details.length())));
    }

    void stop() { host = null; entity = ""; }
}
