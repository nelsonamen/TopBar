# Plugin charts

SDK 1 plugins can publish read-only line and bar charts in their own subpage. Kiosk Satellite renders the same data on-device and in Remote Admin, with a title, legend, units, time labels and sample inspection. Charts are separate from saved settings and floating windows.

Hello World is a complete [working example](../src/me/jxl/kiosk/plugins/hello/HelloWorldPlugin.java). Its text, toggle, number, selection and color settings demonstrate every supported settings control. The chart shows simulated data and requires no device probes or extra permissions.

## Publish a chart

Call `publishSeries(String key, Map<String, Object> chart)` on the host. No additional manifest capability is required. Keep `apiVersion: 1`.

```java
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

long now = System.currentTimeMillis();
Map<String, Object> cpu = new LinkedHashMap<>();
cpu.put("name", "Renderer CPU");
cpu.put("color", "#1976D2");
cpu.put("values", Arrays.asList(12.0, 38.0));

Map<String, Object> chart = new LinkedHashMap<>();
chart.put("title", "Dashboard renderer activity");
chart.put("unit", "%");
chart.put("timestamps", Arrays.asList(now - 10000, now));
chart.put("series", Arrays.asList(cpu));
host.publishSeries("renderer_cpu", chart);
```

Each call replaces the entire chart at that key. Keep your history in a bounded buffer in the plugin and publish a copy. KS copies and validates the input synchronously, so changing your lists after a successful call cannot change the published chart.

Use multiple series to compare readings with the same unit. Use separate charts for different units, such as CPU percentage and memory in MB. Line charts connect series with straight lines. There is no smoothing, resampling or interpolation across missing readings.

| Field | Contract |
| --- | --- |
| `key` argument | Stable chart ID matching `[a-z][a-z0-9_]{0,39}`. Scoped to the plugin |
| `title` | Required string, 1 to 80 characters |
| `unit` | Optional string, at most 16 characters. Defaults to no unit |
| `type` | Optional `"line"` or `"bar"`. Defaults to `"line"` |
| `compact` | Optional boolean. `true` renders a mini chart with a 56-pixel plot and no axes. Defaults to `false` for the full 160-pixel plot |
| `timestamps` | Required list of up to 240 integer Unix timestamps in milliseconds, strictly increasing. Range 0 to 253402300799999 |
| `series` | Required list of 1 to 4 series sharing those timestamps |
| Series `name` | Required string, 1 to 80 characters. Unique within the chart |
| Series `color` | Optional `#RRGGBB` string. KS uses its chart palette when omitted |
| Series `values` | Required list with exactly one entry per timestamp. Each entry is a finite number between -1e12 and 1e12 or `null` for a gap |

Only the listed fields are accepted. Labels are plain text. HTML, SVG and image payloads are not part of this API. Empty timestamps with empty value lists show a waiting state. All-null data shows no readings. A single sample renders as a point or bar and constant values receive a usable vertical range.

## Bar charts

Add `chart.put("type", "bar")` to draw vertical bars. Use `"line"` or omit the field for lines. The timestamps, series and limits stay the same, and the type can change on an existing chart key.

Bars for different series sit side by side at each timestamp. They are not stacked. The time axis preserves the spacing between timestamps. KS reserves space at both edges and sizes groups using the shortest interval so adjacent groups do not overlap. For dense histories, publish fewer samples or aggregate them in your plugin for wider bars.

The vertical range always includes zero. Positive bars extend upward and negative bars extend downward from the zero baseline. Null readings leave an empty slot and zero readings have a thin baseline marker. Tapping, dragging, hovering and keyboard inspection work as they do for lines. These are timestamped charts, not categorical charts with arbitrary horizontal labels.

Hello World's **Chart type** selector switches between **Line** and **Bar**. Combine it with **Chart size** to try full and mini bar charts.

## Mini charts

Add `chart.put("compact", true)` to use a compact chart. Line charts render as sparklines and bar charts render as mini bars. It keeps the title, series legend, current values and sample inspection but hides the axes and instructional text. Use `false` or omit the field for a regular chart. Both layouts use the same data format and limits and can be changed by publishing another snapshot at the same key.

Hello World's **Chart size** selector switches between **Regular** and **Mini** so you can try both presentations.

## Updates and lifetime

- Each plugin session can own at most four charts. Updating an existing key does not add another chart.
- At most eight chart changes, including removals, are accepted per plugin per one-second rate window. Invalid input throws `IllegalArgumentException` or `IllegalStateException`. A stopped session or exhausted rate limit throws `IllegalStateException`. Rejected operations leave the previous chart intact.
- Live notifications are coalesced over one second. Remote Admin polls only the visible plugin subpage once per second. Intermediate snapshots can be skipped. Publish the full history each time and do not rely on every notification being displayed.
- Only the charts refresh. Updates do not replace settings fields, steal focus or save unfinished edits.
- Tap, drag or hover to inspect the nearest sample. Double-tap to follow the latest sample again. Keyboard users can use the left and right arrow keys when the plot has focus. Remote Admin also supports End to return to the latest. A selected timestamp stays selected while it remains in the published history.
- `removeSeries(String key)` removes that chart. Removing a missing key is allowed and counts toward the change limit.
- Disabling, uninstalling, updating, stopping the app or turning off **Enable Plugins** clears the session's charts. Charts and history are never persisted by KS, exposed as Home Assistant entities or included in fleet sync.
- Release sampling timers and workers in `stop()`. KS revokes the host before calling `stop`, so do not call `removeSeries` there. Late publications are rejected and queued notifications from an old session are discarded.

Avoid sampling on the Android main thread. KS lifecycle callbacks must still finish within three seconds. Hello World uses a plugin-owned sampler and stops it during shutdown. CPU usage, temperatures and other measurements remain the plugin's responsibility.

## Updating an existing plugin repository

1. Update Kiosk Satellite to a build that includes chart support.
2. Copy the current files from [`sdk/src/me/jxl/kiosk/plugins/`](../sdk/src/me/jxl/kiosk/plugins) into the same directory in your plugin repository. Keep these as compile-time SDK dependencies. Do not package the SDK classes in your plugin ZIP.
3. Keep `apiVersion: 1` in `kiosk-satellite-plugin.json`. Add calls to `host.publishSeries(...)` where your sampler publishes its history. Use `host.removeSeries(...)` when a running plugin intentionally hides a chart.
4. Update test hosts to implement `publishSeries` and `removeSeries` if your tests exercise chart publication. Build and test with your repository's tools. The template uses `python3 tools/test.py` and `python3 tools/build.py`. If you have a KS source checkout, run `python3 tools/check-sdk.py /path/to/kiosk-satellite`.
5. Test the local ZIP through **Plugin Manager > Developer Tools > Install from ZIP**. If the plugin is already installed from GitHub, use a separate development kiosk or uninstall it before switching sources. Uninstalling removes its settings.
6. Commit the changes and publish a stable GitHub release tagged `v<version>` with the desired new version. The workflow uses the tag as the package version, so no source manifest version edit is required. Let the included GitHub Actions workflow build and attach the release assets. Users install or update through the repository URL.

For renderer diagnostics, publish the timestamped CPU or main-thread activity readings you already collect. Label the metric according to what you measure. Renderer CPU busy time alone is not a frame duration or input latency measurement.

## Remote Admin API

Authenticated administrators can call `getPluginCharts` with `{"id": "plugin-id"}`. The result is a list of current chart snapshots with their `key` included. It returns an empty list for an unknown plugin or a session with no charts. This command does not refresh or modify settings. Fleet credentials cannot call it.
