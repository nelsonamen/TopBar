<h1 align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/ks_plugin_banner_dark.svg" />
    <source media="(prefers-color-scheme: light)" srcset="assets/ks_plugin_banner_light.svg" />
    <img alt="Kiosk Satellite for Home Assistant" src="assets/ks_plugin_banner_default.svg" width="650" />
  </picture>
</h1>

This repository contains the [Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite) plugin SDK, documentation and getting started guides. Learn how plugins work, build community features and publish a plugin from your own GitHub repository. It also includes **Hello World**, a working starter template with a floating window over the Home Assistant dashboard.

All documented features use **SDK 1**, the first public plugin SDK. Plugins require a Kiosk Satellite build with SDK 1 support and Android 7.0 or newer.

## Documentation

- [Home Assistant entity reads and subscriptions](docs/home-assistant.md): use the standard entity picker and follow live states through KS-managed authentication.
- [Creating plugins](docs/creating-plugins.md): SDK interfaces, lifecycle, settings, package format, build tools and publishing.
- [Interacting with Kiosk Satellite](docs/ks-api.md): host methods, state queries, transient controls, passive events, capabilities and lifecycle limits.
- [Installing and managing plugins](docs/installing-plugins.md): installation, settings, trust, updates and the remote API.
- [Screensavers](docs/screensavers.md): custom rendering inside the stock screensaver system, with a bouncing DVD demo.
- [Shizuku](docs/shizuku.md): optional shell or root commands through KS, explicit permission, the Hello World demo and a minimal example.
- [Charts](docs/charts.md): publish line and bar charts, inspect samples and update an existing plugin to use charts.
- [Status tiles](docs/status-tiles.md): put a health verdict on the Remote Admin Overview Status panel.
- [Sensors, selects and switches](docs/entities.md): show live readings in KS, publish them to Home Assistant and handle writable controls.
- [Dashboard URLs](docs/dashboard.md): discover the configured server and current view without duplicating URL settings.
- [SDK source](sdk/src/me/jxl/kiosk/plugins): the `KioskPlugin` and `PluginHost` interfaces supplied by Kiosk Satellite at runtime.
- [Read-only example](examples/read-only): a buildable plugin that observes screen, screensaver and dashboard state.

## Get started

1. Create your own repository from this template and clone it locally. Each repository contains one plugin.
2. Read the [creating plugins guide](docs/creating-plugins.md) and the [KS interaction reference](docs/ks-api.md) to choose the features and capabilities your plugin needs.
3. Update `kiosk-satellite-plugin.json`, the Java class/package in `src/` and this README for your plugin. Keep the plugin ID stable after publication.
4. Build and test locally using the steps below, then [publish a GitHub release](#publish-your-plugin) when it is ready for users.

### Build and test locally

Install Python 3, JDK 17 or newer and an Android SDK with build-tools. Set `ANDROID_HOME` and `JAVA_HOME` as needed.

```sh
python3 tools/test.py
python3 tools/build.py
```

With the unchanged template, the build creates `dist/hello-world-1.0.1.zip`, its `.zip.sha256` checksum file and a copy of `kiosk-satellite-plugin.json` in `dist/`. The ZIP contains that same manifest. The SDK sources in `sdk/` are compile-time dependencies. Their classes are not included in the package.

For developer testing only, open **Plugin Manager > Developer Tools > Install from ZIP** on the kiosk or Remote Admin and select the ZIP from `dist/`. Confirm that you trust the code, then enable the plugin from its entry row. No GitHub release is needed.

To test an update, install the replacement ZIP directly. KS stops the plugin automatically and restores its enabled state after the update. A normal update does not require an app restart. Compatible settings are retained. If the existing plugin was installed from GitHub, uninstall it before switching to a local build. Uninstalling deletes its settings.

If you also have the Kiosk Satellite source, check that the template's SDK interfaces match the application:

```sh
python3 tools/check-sdk.py /path/to/kiosk-satellite
```

## Hello World starter template

Hello World opens a draggable greeting window over your Home Assistant dashboard. It demonstrates every supported settings control, live readings, a live chart, an Overview status tile, a floating view, a button counter and plugin actions that users can assign to gestures, the kiosk drawer or Home Assistant buttons.

Start with [HelloWorldPlugin.java](src/me/jxl/kiosk/plugins/hello/HelloWorldPlugin.java) and its [manifest](kiosk-satellite-plugin.json), then replace the greeting behavior with your own functionality.

### Install Hello World

1. Open **Settings > Plugin Manager** on the kiosk or **Plugin Manager** in Remote Admin and turn on **Enable Plugins**.
2. Choose **Add plugin**, paste this repository's GitHub URL and choose **Preview**.
3. Read the manifest and this README. Choose **Trust and install** if you trust the code.
4. Enable **Hello World** from its entry row. Return to the dashboard to see the window.

The plugin runs trusted code inside Kiosk Satellite. It can access app data and granted Android permissions. Review the source before enabling it.

### Try the template

Tap the Hello World entry to open its subpage. **Greeting** changes the message. **Show window when enabled** controls whether it opens automatically. Settings save automatically. On the kiosk, tap **Greeting** to edit it in a dialog.

The settings are grouped into **Greeting window**, **Chart demo**, **Home Assistant demo**, **Shizuku demo**, **Status tile demo** and **Screensaver demo**. The chart uses simulated values, not device measurements. Its 120-sample history covers about four minutes and updates every two seconds while **Show demo chart** is on.

| Control | Example |
| --- | --- |
| Text input | **Greeting** changes the floating window message |
| Toggle | **Show window when enabled** opens the window on startup. **Show demo chart** shows or removes the chart |
| Number slider | **Amplitude** changes the height of new simulated samples |
| Selection | **Pattern** chooses a sine or triangle wave for new samples. **Chart type** switches between Line and Bar. **Chart size** switches between Regular and Mini |
| Color picker | **Series color** changes the Wave series color |
| Live readings | Numeric values with units, confirmed boolean and select states and a multiline sample summary |
| Read-only chart | **Simulated activity** compares Wave and Reference with sample inspection as lines or grouped bars in regular and mini layouts |

Chart settings take effect on the next sample. Tap or drag the chart to inspect a sample, or double-tap to follow the latest. See the [chart API](docs/charts.md) to replace the simulated values with your own measurements.

**Show window** reopens a dismissed window and **Hide window** closes it. Assign these actions in Gestures or open their action rows to add drawer shortcuts or Home Assistant buttons. The floating **Say hello** button increments its counter. Disable or uninstall using the controls on its entry row.

The master **Enable Plugins** switch pauses all plugins and closes their windows. It keeps each plugin's enabled choice and settings, so turning it back on resumes the selected plugins.

### Try the live readings

Open **Plugin Manager > Hello World** on-device or in Remote Admin. **Readings** shows the simulated wave, demo status, chart states, selected pattern, history sample count and a multiline summary. Values update every two seconds with the chart. Turn **Show demo chart** off to see **No data** for the wave and **Off** for its states. These readings work without Home Assistant or ESPHome and come from the same entities published below.

### Try the Home Assistant entities

With ESPHome and native entities enabled in KS, Hello World also exposes **Simulated wave** as a numeric sensor, **Demo status** as a text sensor, **Demo chart active** as a binary sensor, **Demo pattern** as a select and **Demo chart** as a switch. The numeric value follows the chart and becomes unknown when the chart is hidden. Changing Demo pattern in Home Assistant updates the plugin's Pattern setting and its next samples. Demo chart controls the same Show demo chart setting used in the subpage. These chart readings are simulated. The optional Shizuku diagnostics below read the device.

Use the ESPHome entity picker to exclude any of these. The [entity guide](docs/entities.md) explains how to publish your own readings and handle select and switch requests.

### Try the status tile

Open the Remote Admin Overview. Its Status panel shows a **Hello World demo** tile after the six built-in tiles, with the plugin's name under its state line. In the **Status tile demo** group, **Tile level** moves the dot between green, amber, red and gray, **Tile text** changes the state line and **Show status tile** removes the tile or brings it back. Tapping the tile opens this subpage. The tile goes away when the plugin is disabled or Plugin Manager is off. See the [status tile guide](docs/status-tiles.md) for the API and its limits.

### Try the DVD screensaver

Enable Hello World, then select **DVD Logo (Hello World)** under **Screensaver > Screensaver mode** or in a schedule entry. KS uses its stock screensaver settings and renders the bouncing DVD logo as the content. In Hello World's **Screensaver demo** group, **Logo size**, **Logo color** and **Background color** save automatically and refresh the active screensaver. The demo loads separate HTML, CSS, JavaScript and SVG files from [assets/dvd](assets/dvd). See the [screensaver guide](docs/screensavers.md) for the rendering API and lifecycle.

### Try Shizuku

The **Shizuku demo** group demonstrates real device diagnostics through KS's SDK. Start Shizuku and grant KS access using the **Shizuku access** row, then turn on **Enable Shizuku demo**. It is off by default. The greeting window and chart work without Shizuku.

Choose **Process identity**, **Android version** or **Kernel version** in **Diagnostic**. The plugin runs a fixed read-only command every 10 seconds and shows **Shizuku demo status** and **Shizuku result** under Readings. Changing the selection requests a fresh reading. These values also appear as text sensors in Home Assistant when plugin entities are exposed through ESPHome.

The demo never requests permission, grants permissions or changes Android settings. Missing access, failed commands and lost connections clear the result and show a status message. Turning off the demo stops new commands. Results from a previous selection or stopped session are ignored. See [ShizukuDemo.java](src/me/jxl/kiosk/plugins/hello/ShizukuDemo.java) for asynchronous execution and lifecycle handling.

## Publish your plugin

1. Build and test locally during development.
2. Commit the source, README and manifest. The manifest version is the default for local ZIP builds. It does not need to match the release tag. Keep `apiVersion: 1`.
3. Publish a stable GitHub release tagged with the desired version, such as `v1.2.0`, at that commit. The included GitHub Actions workflow tests and builds the source and attaches the manifest, ZIP and checksum automatically. Wait for it to succeed before sharing the release.
4. Share the public repository URL.

The workflow takes the package version from the tag, removing an optional `v` prefix. It writes that version into both generated manifests and the ZIP filename without editing or committing the source manifest. The release title is only a display label. Local builds can also override the version with `python3 tools/build.py --version 1.2.0`.

KS discovers the latest stable GitHub release, previews its attached manifest and reads the README from the tagged commit. It downloads the ZIP only after confirmation and verifies the checksum and packaged manifest. Drafts and prereleases are excluded.

Normal installation uses **Add plugin** with the repository URL. KS rejects manually attached assets and requires the GitHub Actions uploader plus a matching GitHub SHA-256 digest. This verifies publication through Actions, not the honesty of the workflow code. See [publishing requirements](docs/creating-plugins.md#repository-and-release). **Install from ZIP** is reserved for developers testing local builds.

## License

[Apache-2.0](LICENSE). Kiosk Satellite has its own application license.
