// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins.hello;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.PluginHost;

public final class TopBarPlugin implements KioskPlugin {
    private PluginHost host;
    private WindowManager windowManager;
    private FrameLayout container;
    private WebView webView;
    private WindowManager.LayoutParams params;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<String, Object> currentSettings;

    private Application.ActivityLifecycleCallbacks lifecycleCallbacks;
    private boolean isAppInForeground = true;
    private boolean isScreensaverActive = false;
    private boolean isDimMode = false;
    private Set<String> subscribedEntities = new HashSet<>();
    private Map<String, String> entityStates = new java.util.concurrent.ConcurrentHashMap<>();

    private void setupLifecycleCallbacks(Context context) {
        if (context == null || lifecycleCallbacks != null) return;
        
        Context appContext = context.getApplicationContext();
        if (appContext instanceof Application) {
            lifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
                private int activityReferences = 0;
                private boolean isActivityChangingConfigurations = false;

                @Override
                public void onActivityStarted(Activity activity) {
                    if (++activityReferences == 1 && !isActivityChangingConfigurations) {
                        isAppInForeground = true;
                    }
                }

                @Override
                public void onActivityStopped(Activity activity) {
                    isActivityChangingConfigurations = activity.isChangingConfigurations();
                    if (--activityReferences == 0 && !isActivityChangingConfigurations) {
                        isAppInForeground = false;
                        mainHandler.post(() -> hideMenu());
                    }
                }
                
                @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
                @Override
                public void onActivityResumed(Activity activity) {
                    isAppInForeground = true;
                    if (!isScreensaverActive) {
                        mainHandler.post(() -> showMenu());
                    }
                }
                @Override public void onActivityPaused(Activity activity) {}
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
                @Override public void onActivityDestroyed(Activity activity) {}
            };
            ((Application) appContext).registerActivityLifecycleCallbacks(lifecycleCallbacks);
        }
    }

    private void updateSubscriptions() {
        if (host == null) return;
        java.util.Set<String> newEntities = new java.util.HashSet<>();
        for (int i = 1; i <= 5; i++) {
            String target = "";
            String compactVal = getSetting("btn" + i, "");
            if (!compactVal.trim().isEmpty() && compactVal.contains("|")) {
                String[] parts = compactVal.split("\\|");
                if (parts.length >= 3) target = parts[2].trim();
            } else {
                target = getSetting("btn" + i + "_target", "").trim();
            }

            if (!target.isEmpty() && target.contains(".")) {
                String domain = target.split("\\.")[0].toLowerCase();
                if (!domain.equals("script") && !domain.equals("scene") && !domain.equals("automation")) {
                    newEntities.add(target);
                }
            }
        }
        
        for (String oldEnt : subscribedEntities) {
            if (!newEntities.contains(oldEnt)) {
                try { host.unsubscribe("ha.entity." + oldEnt); } catch (Exception e) {}
            }
        }
        
        for (String newEnt : newEntities) {
            if (!subscribedEntities.contains(newEnt)) {
                try { host.subscribe("ha.entity." + newEnt); } catch (Exception e) {}
            }
        }
        subscribedEntities = newEntities;
    }

    @Override
    public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        this.currentSettings = settings;
        if (host != null) {
            host.log("Plugin TopBar started.");
            try {
                host.subscribe("screensaver.state");
                host.subscribe("screensaver.view");
            } catch (Exception e) {
                host.log("Warning: could not subscribe to screensaver events.");
            }
            updateSubscriptions();
        }
        mainHandler.post(() -> {
            setupLifecycleCallbacks(getAndroidContext());
            if (isAppInForeground && !isScreensaverActive) {
                showMenu();
            }
        });
    }

    @Override
    public synchronized void configure(Map<String, Object> settings) {
        this.currentSettings = settings;
        updateSubscriptions();
        mainHandler.post(() -> {
            hideMenu();
            if (isAppInForeground && !isScreensaverActive) {
                showMenu();
            }
        });
    }

    @Override
    public synchronized void execute(String command, Map<String, Object> arguments) {
        if ("show".equals(command)) {
            mainHandler.post(() -> {
                if (isAppInForeground && !isScreensaverActive) showMenu();
            });
        } else if ("hide".equals(command)) {
            mainHandler.post(this::hideMenu);
        }
    }

    @Override
    public synchronized void onEvent(String event, Map<String, Object> payload) {
        if ("ks.screensaver.state".equals(event)) {
            if (payload != null && payload.containsKey("active")) {
                boolean active = Boolean.TRUE.equals(payload.get("active"));
                isScreensaverActive = active;
                mainHandler.post(() -> {
                    if (active) {
                        hideMenu();
                    } else {
                        if (isAppInForeground) {
                            showMenu();
                        }
                    }
                });
            }
        } else if ("ks.screensaver.view".equals(event)) {
            // "dim" view = modo noite/escuro; null = ecrã normal/dia
            Object viewObj = payload != null ? payload.get("view") : null;
            boolean nowDim = "dim".equals(viewObj);
            if (nowDim != isDimMode) {
                isDimMode = nowDim;
                // Refrescar a pill com as cores corretas
                if (isAppInForeground && !isScreensaverActive) {
                    mainHandler.post(() -> { hideMenu(); showMenu(); });
                }
            }
        } else if (event.startsWith("ks.ha.entity.")) {
            String entityId = event.substring("ks.ha.entity.".length());
            Object stateObj = payload != null ? payload.get("state") : null;
            if (stateObj != null) {
                String stateStr = stateObj.toString().toLowerCase();
                entityStates.put(entityId, stateStr);
                mainHandler.post(() -> {
                    if (webView != null) {
                        webView.evaluateJavascript("if (typeof updateEntityState === 'function') { updateEntityState('" + entityId + "', '" + stateStr + "'); }", null);
                    }
                });
            }
        }
    }

    private void fetchAndApplyEntityStates() {
        if (host == null) return;
        for (int i = 1; i <= 5; i++) {
            String target = "";
            String compactVal = getSetting("btn" + i, "");
            if (!compactVal.trim().isEmpty() && compactVal.contains("|")) {
                String[] parts = compactVal.split("\\|");
                if (parts.length >= 3) target = parts[2].trim();
            } else {
                target = getSetting("btn" + i + "_target", "").trim();
            }

            if (!target.isEmpty() && target.contains(".")) {
                String domain = target.split("\\.")[0].toLowerCase();
                if (!domain.equals("script") && !domain.equals("scene") && !domain.equals("automation")) {
                    final String entityId = target;

                    // 1. Immediately apply cached state if available
                    String cachedState = entityStates.get(entityId);
                    if (cachedState != null && webView != null) {
                        final String st = cachedState;
                        mainHandler.post(() -> {
                            if (webView != null) {
                                webView.evaluateJavascript("if (typeof updateEntityState === 'function') { updateEntityState('" + entityId + "', '" + st + "'); }", null);
                            }
                        });
                    }

                    // 2. Query live state snapshot from Kiosk Satellite
                    try {
                        host.executeCommand("getHaEntityState", java.util.Collections.singletonMap("entityId", entityId), (ok, data, error) -> {
                            if (ok && data instanceof Map) {
                                Map<?, ?> map = (Map<?, ?>) data;
                                Object stObj = map.get("state");
                                if (stObj != null) {
                                    String stStr = stObj.toString().toLowerCase();
                                    entityStates.put(entityId, stStr);
                                    mainHandler.post(() -> {
                                        if (webView != null) {
                                            webView.evaluateJavascript("if (typeof updateEntityState === 'function') { updateEntityState('" + entityId + "', '" + stStr + "'); }", null);
                                        }
                                    });
                                }
                            }
                        });
                    } catch (Exception e) {}
                }
            }
        }
    }

    @Override
    public synchronized void stop() {
        mainHandler.post(() -> {
            hideMenu();
            Context context = getAndroidContext();
            if (context != null && lifecycleCallbacks != null) {
                Context appContext = context.getApplicationContext();
                if (appContext instanceof Application) {
                    ((Application) appContext).unregisterActivityLifecycleCallbacks(lifecycleCallbacks);
                }
                lifecycleCallbacks = null;
            }
        });
        host = null;
    }

    private void hideMenu() {
        Runnable task = () -> {
            if (container != null && windowManager != null) {
                try {
                    if (webView != null) {
                        webView.stopLoading();
                        webView.loadUrl("about:blank");
                        webView.clearHistory();
                        webView.removeAllViews();
                        webView.destroy();
                    }
                    windowManager.removeView(container);
                } catch (Exception e) {
                    if (host != null) {
                        host.log("Error removing window: " + e.getMessage());
                    }
                }
                container = null;
                webView = null;
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            mainHandler.post(task);
        }
    }

    private int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    private boolean checkDarkMode(Context context) {
        String themeSetting = getSetting("theme", "Auto (System)").toLowerCase();
        if (themeSetting.contains("light") || themeSetting.contains("claro")) {
            return false;
        }
        if (themeSetting.contains("dark") || themeSetting.contains("escuro")) {
            return true;
        }
        if (isDimMode) {
            return true;
        }
        if (context != null) {
            try {
                int uiMode = context.getResources().getConfiguration().uiMode;
                if ((uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                    return true;
                }
            } catch (Exception e) {}
        }
        return false;
    }

    @SuppressLint({"SetJavaScriptEnabled"})
    private void showMenu() {
        hideMenu();

        Context context = getAndroidContext();
        if (context == null) return;

        boolean isDark = checkDarkMode(context);

        String pillBg, pillBorder, iconBtnBg, iconBtnActive, iconBtnOutline, offIconColor;
        if (isDark) {
            pillBg         = "rgba(18, 18, 20, 0.92)";
            pillBorder     = "rgba(255, 255, 255, 0.14)";
            iconBtnBg      = "rgba(255, 255, 255, 0.08)";
            iconBtnActive  = "rgba(255, 255, 255, 0.25)";
            iconBtnOutline = "rgba(255, 255, 255, 0.08)";
            offIconColor   = "rgba(255, 255, 255, 0.65)";
        } else {
            pillBg         = "rgba(255, 255, 255, 0.94)";
            pillBorder     = "rgba(0, 0, 0, 0.08)";
            iconBtnBg      = "rgba(0, 0, 0, 0.03)";
            iconBtnActive  = "rgba(0, 0, 0, 0.12)";
            iconBtnOutline = "rgba(0, 0, 0, 0.06)";
            offIconColor   = "#757575";
        }

        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        container = new FrameLayout(context);
        container.setBackgroundColor(Color.TRANSPARENT);
        webView = new WebView(context);
        webView.setBackgroundColor(Color.TRANSPARENT);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        webSettings.setDomStorageEnabled(true);

        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        webView.addJavascriptInterface(new WebAppInterface(), "Android");

        // Build dynamic buttons (1 to 5)
        StringBuilder buttonsHtml = new StringBuilder();
        int activeButtons = 0;

        for (int i = 1; i <= 5; i++) {
            String compactVal = getSetting("btn" + i, "");
            String iconVal = "";
            String colorVal = "";
            String targetVal = "";

            if (!compactVal.trim().isEmpty() && compactVal.contains("|")) {
                String[] parts = compactVal.split("\\|");
                if (parts.length >= 1) iconVal = parts[0].trim();
                if (parts.length >= 2) colorVal = parts[1].trim();
                if (parts.length >= 3) targetVal = parts[2].trim();
            } else {
                String defaultIcon = "";
                String defaultTarget = "";
                if (i == 1) {
                    defaultIcon = "mdi-home-import-outline";
                    defaultTarget = "script.ligar_casa_diogo";
                } else if (i == 2) {
                    defaultIcon = "mdi-home-export-outline";
                    defaultTarget = "script.desligar_tudo_teste";
                } else if (i == 3) {
                    defaultIcon = "mdi-spotify";
                    defaultTarget = "script.abrir_spotify_no_tablet";
                }

                String defaultColor = i == 1 ? "#4CAF50" : (i == 2 ? "#F44336" : "#1DB954");

                iconVal = getSetting("btn" + i + "_icon", defaultIcon);
                colorVal = getSetting("btn" + i + "_color", defaultColor);
                targetVal = getSetting("btn" + i + "_target", defaultTarget);
            }

            if (colorVal.isEmpty()) {
                colorVal = "#4CAF50";
            }

            boolean isActive = !iconVal.trim().isEmpty() || !targetVal.trim().isEmpty();

            if (isActive) {
                activeButtons++;
                String iconClass = formatMdiIcon(iconVal.trim().isEmpty() ? "mdi-home" : iconVal);
                String targetTrim = targetVal.trim();
                String entityAttr = "";
                if (!targetTrim.isEmpty() && targetTrim.contains(".")) {
                    String domain = targetTrim.split("\\.")[0].toLowerCase();
                    if (!domain.equals("script") && !domain.equals("scene") && !domain.equals("automation")) {
                        entityAttr = targetTrim;
                    }
                }
                boolean isStateful = !entityAttr.isEmpty();
                String initialColor = isStateful ? offIconColor : colorVal;

                buttonsHtml.append("<div class=\"icon-btn\" id=\"btn").append(i).append("\" ")
                         .append("data-entity=\"").append(entityAttr).append("\" ")
                         .append("data-color=\"").append(colorVal).append("\" ")
                         .append("onclick=\"handleClick('btn").append(i).append("')\">")
                         .append("<i class=\"mdi ").append(iconClass).append("\" style=\"color: ").append(initialColor).append(";\"></i>")
                         .append("</div>");
            }
        }

        if (activeButtons == 0) {
            activeButtons = 1;
            buttonsHtml.append("<div class=\"icon-btn\" onclick=\"handleClick('btn1')\">")
                     .append("<i class=\"mdi mdi-home-import-outline\" style=\"color: #4CAF50;\"></i>")
                     .append("</div>");
        }

        // Always horizontal
        String align = getSetting("menu_position", "Right");
        boolean isLeft  = align.contains("Left")  || align.contains("Esquerda");
        boolean isRight = align.contains("Right") || align.contains("Direita");

        int gravity;
        int xOffset = 0;
        int yOffset = dpToPx(context, 30);

        if (isLeft) {
            gravity = Gravity.BOTTOM | Gravity.START;
            xOffset = dpToPx(context, 12);
        } else if (isRight) {
            gravity = Gravity.BOTTOM | Gravity.END;
            xOffset = dpToPx(context, 12);
        } else {
            gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        }

        // Scale
        String scaleStr = getSetting("scale", "100%");
        float scaleFactor = 1.0f;
        if ("80%".equals(scaleStr)) scaleFactor = 0.8f;
        else if ("120%".equals(scaleStr)) scaleFactor = 1.2f;

        String html = "<html><head>" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1, user-scalable=0\"/>" +
                "<link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/@mdi/font@7.4.47/css/materialdesignicons.min.css\">" +
                "<style>" +
                "html, body { margin: 0; padding: 0; width: 100%; height: 100%; display: flex; justify-content: center; align-items: center; background: transparent; overflow: hidden; -webkit-tap-highlight-color: transparent; transition: opacity 0.5s ease; }" +
                ".pill { background: " + pillBg + "; backdrop-filter: blur(16px); -webkit-backdrop-filter: blur(16px); border: 1px solid " + pillBorder + "; border-radius: 36px; padding: 10px 14px; display: flex; flex-direction: row; gap: 18px; box-shadow: 0 6px 20px rgba(0,0,0,0.22); transform: scale(" + scaleFactor + "); transform-origin: center; transition: background 0.4s ease; }" +
                ".icon-btn { width: 52px; height: 52px; border-radius: 26px; display: flex; justify-content: center; align-items: center; font-size: 28px; background: " + iconBtnBg + "; box-shadow: inset 0 0 0 1px " + iconBtnOutline + "; transition: background 0.2s, transform 0.1s; cursor: pointer; }" +
                ".icon-btn:active { background: " + iconBtnActive + "; transform: scale(0.90); }" +
                ".mdi { line-height: 1; }" +
                "</style>" +
                "<script>" +
                "let lastClick = 0;" +
                "function handleClick(btn) {" +
                "  let now = Date.now();" +
                "  if (now - lastClick < 1000) return;" +
                "  lastClick = now;" +
                "  Android.onClick(btn);" +
                "}" +
                "function updateEntityState(entityId, state) {" +
                "  let btns = document.querySelectorAll('.icon-btn[data-entity=\"' + entityId + '\"]');" +
                "  btns.forEach(btn => {" +
                "    let icon = btn.querySelector('i');" +
                "    let activeColor = btn.getAttribute('data-color');" +
                "    if (state === 'on' || state === 'playing' || state === 'home' || state === 'open') {" +
                "      icon.style.color = activeColor;" +
                "    } else if (state === 'unavailable' || state === 'unknown') {" +
                "      icon.style.color = '" + (isDark ? "rgba(255, 255, 255, 0.3)" : "rgba(150, 150, 150, 0.4)") + "';" +
                "    } else {" +
                "      icon.style.color = '" + offIconColor + "';" +
                "    }" +
                "  });" +
                "}" +
                "</script>" +
                "</head><body>" +
                "<div class=\"pill\">" +
                buttonsHtml.toString() +
                "</div>" +
                "</body></html>";

        webView.loadDataWithBaseURL("https://localhost", html, "text/html", "UTF-8", null);
        fetchAndApplyEntityStates();
        container.addView(webView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        // Button size: 52dp icon-btn + 18dp gap + 14dp*2 padding
        int btnSize = 52;
        int gapSize = 18;
        int paddingH = 28; // 14dp each side
        int paddingV = 20; // 10dp each side

        int basePillWidth  = (btnSize * activeButtons) + (gapSize * (activeButtons - 1)) + paddingH * 2;
        int basePillHeight = btnSize + paddingV * 2;

        int finalWidth  = Math.round(dpToPx(context, basePillWidth)  * scaleFactor);
        int finalHeight = Math.round(dpToPx(context, basePillHeight) * scaleFactor);

        params = new WindowManager.LayoutParams(
                finalWidth,
                finalHeight,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = gravity;
        params.x = xOffset;
        params.y = yOffset;

        try {
            windowManager.addView(container, params);
            mainHandler.post(this::fetchAndApplyEntityStates);
        } catch (Exception e) {
            if (host != null) {
                host.log("Error adding plugin window: " + e.getMessage());
            }
        }
    }

    private String getSetting(String key, String defValue) {
        if (currentSettings != null && currentSettings.containsKey(key)) {
            Object val = currentSettings.get(key);
            if (val != null) {
                String str = val.toString().trim();
                if (key.startsWith("btn")) {
                    return str;
                }
                if (!str.isEmpty()) {
                    return str;
                }
            }
        }
        return defValue;
    }

    private String formatMdiIcon(String icon) {
        if (icon == null || icon.isEmpty()) return "mdi-circle";
        icon = icon.trim();
        if (icon.startsWith("mdi:")) {
            icon = icon.replace("mdi:", "mdi-");
        }
        if (!icon.startsWith("mdi-")) {
            icon = "mdi-" + icon;
        }
        return icon;
    }

    private class WebAppInterface {
        @JavascriptInterface
        public void onClick(String btn) {
            mainHandler.post(() -> {
                String target = "";
                String iconVal = "";
                String compactVal = getSetting(btn, "");
                if (!compactVal.trim().isEmpty() && compactVal.contains("|")) {
                    String[] parts = compactVal.split("\\|");
                    if (parts.length >= 1) iconVal = parts[0].trim();
                    if (parts.length >= 3) target = parts[2].trim();
                } else {
                    iconVal = getSetting(btn + "_icon", "");
                    target = getSetting(btn + "_target", "");
                }

                if (iconVal.toLowerCase().contains("spotify") || target.toLowerCase().contains("spotify")) {
                    hideMenu();
                }
                if (!target.isEmpty()) {
                    executeHomeAssistantAction(target);
                } else if (host != null) {
                    host.log("Warning: " + btn + " was pressed but has no target configured.");
                }
            });
        }
    }

    private void executeHomeAssistantAction(String target) {
        String haUrl = getSetting("ha_url", "http://192.168.1.225:8123");
        String haToken = getSetting("ha_token", "");
        final String finalUrl = haUrl.replaceAll("/$", "");

        if (haToken.isEmpty()) {
            if (host != null) {
                host.log("Home Assistant error: token is not configured.");
            }
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String trimmedTarget = target.trim();
                String[] parts = trimmedTarget.split("\\.");
                if (parts.length < 2) {
                    if (host != null) {
                        host.log("Home Assistant error: invalid target format '" + trimmedTarget + "'. Use domain.entity.");
                    }
                    return;
                }

                String domain = parts[0].toLowerCase();
                String serviceOrEntity = parts[1];
                String serviceUrl;
                String jsonBody = "";

                if ("script".equals(domain)) {
                    // Executa o script diretamente pelo endpoint do serviço
                    serviceUrl = finalUrl + "/api/services/script/" + serviceOrEntity;
                } else if ("scene".equals(domain)) {
                    // Ativa a cena
                    serviceUrl = finalUrl + "/api/services/scene/turn_on";
                    jsonBody = "{\"entity_id\": \"" + trimmedTarget + "\"}";
                } else if ("automation".equals(domain)) {
                    // Dispara a automação
                    serviceUrl = finalUrl + "/api/services/automation/trigger";
                    jsonBody = "{\"entity_id\": \"" + trimmedTarget + "\"}";
                } else if ("turn_on".equals(serviceOrEntity) || "turn_off".equals(serviceOrEntity) || "toggle".equals(serviceOrEntity)) {
                    // Caso o utilizador tenha especificado um serviço direto como light.toggle
                    serviceUrl = finalUrl + "/api/services/" + domain + "/" + serviceOrEntity;
                } else {
                    // Para qualquer outra entidade (light.sala, switch.tomada, etc.), faz toggle no Home Assistant
                    serviceUrl = finalUrl + "/api/services/homeassistant/toggle";
                    jsonBody = "{\"entity_id\": \"" + trimmedTarget + "\"}";
                }

                URL url = new URL(serviceUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + haToken);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);

                if (!jsonBody.isEmpty()) {
                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = jsonBody.getBytes("utf-8");
                        os.write(input, 0, input.length);
                    }
                }

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    if (host != null) {
                        host.log("Home Assistant action executed: " + trimmedTarget + " (HTTP " + code + ")");
                    }
                    mainHandler.post(this::fetchAndApplyEntityStates);
                } else {
                    if (host != null) {
                        host.log("Home Assistant error (" + code + ") executing " + trimmedTarget + " at " + serviceUrl);
                    }
                }
            } catch (Exception e) {
                if (host != null) {
                    host.log("Home Assistant API exception: " + e.getMessage());
                }
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    @SuppressLint("PrivateApi")
    private Context getAndroidContext() {
        try {
            if (host instanceof Context) return (Context) host;
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = activityThread.getMethod("currentActivityThread").invoke(null);
            Object app = activityThread.getMethod("getApplication").invoke(thread);
            return (Context) app;
        } catch (Exception e) { return null; }
    }
}