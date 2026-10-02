// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import java.util.LinkedHashMap;
import java.util.Map;

/** Rendering options for assets/dvd/index.html. KS owns the screensaver lifecycle. */
final class DvdScreensaver {
    private static String color(Object value, String fallback) {
        return value instanceof String && ((String) value).matches("#[a-fA-F0-9]{6}") ? (String) value : fallback;
    }

    static Map<String, Object> options(Map<String, Object> settings) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("logoColor", color(settings.get("dvdLogoColor"), "#00D4FF"));
        data.put("backgroundColor", color(settings.get("dvdBackgroundColor"), "#000000"));
        Object size = settings.get("dvdLogoSize");
        double percent = size instanceof Number ? ((Number) size).doubleValue() : 100;
        data.put("logoSize", Double.isFinite(percent) ? Math.max(50, Math.min(200, percent)) : 100);
        return data;
    }
}
