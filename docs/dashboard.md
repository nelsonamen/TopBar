# Dashboard URLs and navigation

SDK 1 plugins with `host.read` can call `getDashboardState` to discover the kiosk's configured server and current dashboard view. This reads existing KS state. It does not load a page, run JavaScript, make a network request or change settings.

```java
host.executeCommand("getDashboardState", Collections.emptyMap(), (ok, data, error) -> {
    if (!ok) {
        host.status(error, true);
        return;
    }
    Map<?, ?> state = (Map<?, ?>) data;
    String server = (String) state.get("homeAssistantUrl");
    String start = (String) state.get("startUrl");
    String current = (String) state.get("currentUrl");
    String path = (String) state.get("currentPath");
    // Use these values in your plugin's own diagnostics.
});
```

The command requires an empty arguments map. It is also listed by `getHostApi` for a session with `host.read`. The SDK has other read commands too, including screen, screensaver, camera view, device and Home Assistant connection state. See the [complete API reference](ks-api.md).

| Field | Meaning |
| --- | --- |
| `homeAssistantUrl` | Configured Home Assistant base URL |
| `startUrl` | Saved Start URL, including its dashboard path |
| `currentUrl` | Last URL reported by the main WebView, including in-page navigation such as HA view changes |
| `currentPath` | Encoded path from `currentUrl`, such as `/dashboard-tablet/kitchen`. `/` for the root |

Each URL is HTTP or HTTPS and contains only its scheme, host, optional port and path. User information such as `user:password@`, the entire query string and the fragment are removed. Token settings, cookies, headers and browser storage are not read or returned. Encoded path segments are preserved and KS does not interpret application-specific path segments. Empty, invalid, unsupported-scheme or overlong input URLs return null. `currentPath` is null when `currentUrl` is null. Before the WebView reports its first URL, both current fields are null even if a Start URL is configured.

For example, `https://user:password@ha.example:8123/dashboard/main?token=secret#section` becomes `https://ha.example:8123/dashboard/main`. Query-based and fragment-based routes are intentionally not included, so this is a diagnostics URL rather than an exact navigation link.

## Secure context proxy and overlays

`currentUrl` describes the main WebView's actual URL. With KS's secure context proxy enabled, it can use a loopback address such as `http://127.0.0.1:18123/dashboard/main`. The configured server and Start URL remain separate fields. Use `homeAssistantUrl` when testing connectivity to the remote HA server. Probing a loopback URL measures the local proxy path too.

A screensaver, camera view or browser overlay can cover the main WebView. Those surfaces do not replace the current dashboard URL in this response. The current URL reflects navigation state and does not certify that the page loaded successfully or is presently visible. Measuring HTTP response headers does not measure JavaScript responsiveness, rendering speed or authenticated dashboard content.

## Follow configuration and view changes

Subscribe to `browser.state` before your initial read:

```java
host.subscribe("browser.state");
// Request the initial getDashboardState snapshot here.
```

KS delivers `onEvent("ks.browser.state", payload)` after a main WebView page load, an in-page URL change or a change to the saved Home Assistant or Start URL. The payload contains only the standard `time` field. Call `getDashboardState` again to obtain the current sanitized snapshot. Unrelated settings and their values are not exposed.

Notifications use the existing coalescing, rate limits and session revocation rules. Treat them as a signal to refresh, not a complete navigation history. Overlapping read callbacks can arrive out of order. The [read-only example](../examples/read-only/src/example/kiosk/ReadOnlyPlugin.java) uses a revision counter to ignore responses from an earlier request.

Update KS to a build containing this command, add `host.read` to your plugin manifest and use the existing SDK 1 `executeCommand`, `subscribe` and `onEvent` methods. No new Java interface or SDK version is required. Network requests remain the plugin's responsibility and should run on a plugin-owned worker with a timeout.
