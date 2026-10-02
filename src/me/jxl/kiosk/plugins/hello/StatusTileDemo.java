// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.Map;
import me.jxl.kiosk.plugins.PluginHost;

/** One tile on the Remote Admin Overview Status panel, driven by three settings. */
final class StatusTileDemo {
    static final String KEY = "demo";
    static final String TITLE = "Hello World demo";
    private PluginHost host;
    private String published;

    /** Maps the Tile level selection to the SDK level vocabulary. */
    static String level(Object selection) {
        if ("Healthy".equals(selection)) return "on";
        if ("Attention".equals(selection)) return "warn";
        if ("Problem".equals(selection)) return "off";
        return "";
    }

    void configure(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        if (!Boolean.TRUE.equals(settings.get("showStatusTile"))) {
            // Removing counts toward the change budget, so only do it once.
            if (published != null) host.removeStatusTile(KEY);
            published = null;
            return;
        }
        String level = level(settings.get("statusLevel"));
        Object configured = settings.get("statusText");
        String text = configured instanceof String ? ((String) configured).trim() : "";
        if (text.length() > 80) text = text.substring(0, 80);
        String next = level + "\n" + text;
        // Every save calls configure with the full settings, so skip unchanged tiles.
        if (next.equals(published)) return;
        host.publishStatusTile(KEY, TITLE, level, text);
        published = next;
    }

    /** KS revokes the host and removes the tile before stop, so nothing is removed here. */
    void stop() { host = null; published = null; }
}
