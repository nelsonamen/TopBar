# Creating plugins

SDK 1 lets a plugin display one floating window, receive window events, expose commands and save settings. Start with Hello World and build a ZIP with `tools/build.py`.

## Build and check the SDK

The template includes the SDK interfaces, build tool and lifecycle tests. Copy the template into a separate repository for your plugin. Install Python 3, JDK 17 or newer and an Android SDK with a platform and build-tools. Set `ANDROID_HOME` and `JAVA_HOME` as needed.

```sh
python3 tools/test.py
python3 tools/build.py
python3 tools/check-sdk.py /path/to/kiosk-satellite
```

Release builds pass `--android-platform 35` to use the platform installed by the workflow, regardless of newer platforms already on the runner. Local builds can pass the same option or omit it to choose the highest installed numeric platform version, including dotted names such as `37.0`. Preview and extension directory names are skipped during automatic selection.

The SDK check compares this repository's interfaces and license with the application's copy. Keep the SDK as a compile-time dependency. The application supplies those interfaces at runtime.

The SDK, template, tooling and documentation use [Apache-2.0](../LICENSE). Contributors retain copyright to their work and contribute under the license of the component they change. Plugins may choose their own license and must retain any required third-party notices.

## Test a local build

For developer testing only, use **Plugin Manager > Developer Tools > Install from ZIP** on the kiosk or remote admin. Select the built ZIP from `dist/`, confirm that you trust the code and enable the installed plugin. The ZIP contains `kiosk-satellite-plugin.json`, `plugin.jar`, `LICENSE` and any bundled `assets/` files. The standalone release manifest and checksum file are only needed when publishing to GitHub.

Local packages use the same 4 MB size limit, manifest validation and DEX checks as release packages. New plugins start disabled. To test another build, install the replacement ZIP directly. KS automatically stops running plugins and restores their enabled state after the update. A normal update does not require an app restart. Compatible settings are retained. A local ZIP cannot replace a plugin installed from GitHub. Uninstall that plugin first, which also deletes its settings.

## Repository and release

Each public GitHub repository contains one plugin and these root files:

```text
README.md                     Documentation shown before installation
kiosk-satellite-plugin.json   The single plugin manifest
LICENSE                       Plugin license included in the package
src/                          Plugin source
```

Publish a release from the tagged source and let the included GitHub Actions workflow build and attach these three files from `dist/`:

```text
kiosk-satellite-plugin.json
<id>-<version>.zip
<id>-<version>.zip.sha256
```

Use `.github/workflows/build.yml` from this template. It builds on a published release or an explicit retry on an existing release tag, not on each commit. It compiles the tagged source on `ubuntu-24.04`, tests it and uploads with `${{ github.token }}`. The tag supplies the package version, optionally prefixed by `v`. The workflow passes it as `--version` to the builder, which uses it in both generated manifests and the ZIP filename. The source manifest keeps its local development version. When updating an existing workflow, also copy `tools/build.py`, `tools/android_sdk.py`, `tools/plugin_manifest.py` and `tools/plugin_assets.py` from the template so the builder supports the version argument. Keep all three assets from the same workflow build.

KS requires GitHub's uploader identity to be `github-actions[bot]` for all three assets and checks the ZIP against both GitHub's asset digest and the release checksum. A manually attached ZIP, manifest or checksum is rejected. Uploading through a personal access token is also rejected. ZIP installation is a developer escape hatch for local testing and does not count as a verified repository release.

This check establishes that GitHub Actions published the bytes. It does not cryptographically prove that the workflow compiled those bytes or ran on a GitHub-hosted runner. A repository owner can change workflow code, so users still need to review and trust the author. Full artifact attestation verification is not implemented. See GitHub's [release asset metadata](https://docs.github.com/en/rest/releases/assets) and [workflow token documentation](https://docs.github.com/en/actions/security-for-github-actions/security-guides/automatic-token-authentication).

The attached manifest is an exact copy of the generated manifest inside the ZIP. It describes the plugin without release URLs or checksums. The ZIP filename comes from its `id` and `version`. The checksum file contains one line in `sha256sum` format: the 64 lowercase hexadecimal hash, two spaces and the ZIP filename.

Commit the source, manifest and README before creating the release tag. Tags start with a letter or digit and contain only letters, digits, periods, underscores or hyphens, up to 151 characters. Publish the release as the latest stable release. KS uses GitHub's latest release endpoint. Drafts and prereleases are excluded.

KS reads the manifest and checksum attached to that release. It resolves the release tag to a commit and reads `README.md` at that commit, including the base for relative documentation links. Edits on the default branch do not change a released version's preview. The preview displays the manifest, compatibility and README without downloading or running plugin code.

A preview lasts 15 minutes. Installation downloads the selected release's ZIP, checks its required SHA-256 and compares every field of the packaged manifest with the reviewed manifest. A newer release published during review does not change the approved download. Repository metadata, release tag, commit and reviewed README are saved for offline use. README HTML is sanitized in remote admin. The device renders Markdown without executable HTML. Links and images require HTTPS.

The manifest is limited to 32 KB, the checksum file to 1 KB and the README to 128 KB. Private repositories, GitHub tokens, automatic updates and custom registries are not supported yet. A repository cannot silently replace an installed plugin with the same ID from another repository. Uninstall first to change its source.

Rebuilds use fixed ZIP timestamps, but toolchain changes can still change DEX output. Upload the manifest, ZIP and checksum from the same build. Keep the checksum outside the ZIP to avoid a circular checksum. Publish a new version when changing a release package.

## Package

A ZIP requires these files at its root:

```text
kiosk-satellite-plugin.json
plugin.jar
LICENSE
```

Optional files under `assets/` are included by the build tool for screensaver rendering. Asset contents count toward the existing 4 MiB package and expanded-size limits. See the [screensaver guide](screensavers.md) for supported paths and loading behavior.

`plugin.jar` contains `classes.dex` and optional additional `classesN.dex` files. It must not contain a copy of the SDK, Java class files or native libraries. The build tool compiles against the SDK and packages only your plugin classes. External dependencies need their own build integration and license review.

The complete ZIP, expanded files and expanded DEX content each have a 4 MB limit. The manifest has a 32 KB limit. SDK 1 permits up to eight installed plugins per kiosk.

## Manifest

See [kiosk-satellite-plugin.json](../kiosk-satellite-plugin.json) for a complete example.

| Field | Meaning |
| --- | --- |
| `schemaVersion` | Must be `1` |
| `apiVersion` | `1`, the first public SDK including all documented features |
| `id` | Stable lowercase ID with optional hyphens, at most 64 characters |
| `name` | Display name, at most 80 characters |
| `version` | Default local build version, formatted as `major.minor.patch` with optional prerelease suffix. Release builds override it from the tag |
| `minAndroidSdk` | Android API level, at least `24` |
| `entryClass` | Public class implementing `KioskPlugin` with a public no-argument constructor |
| `description` | Plain text, at most 1000 characters |
| `author` | Author name, at most 120 characters |
| `license` | License identifier, at most 120 characters |
| `capabilities` | Any required entries from `overlay`, `native`, `entities`, `host.read`, `host.control`, `shizuku` and `screensaver` |
| `settings` | Up to 20 settings |
| `commands` | Up to 20 named commands |

A setting declares `key`, `title`, `type` and `default`. Types are `string`, `boolean`, `number`, `color`, `select` and `entity`. Strings allow up to 512 characters. Keys start with a letter and contain letters, digits or underscores. A command declares `id` and `title`. Command IDs start with a lowercase letter and contain letters or digits. IDs must be unique within their respective lists.

Settings and commands are scoped to the plugin ID. Do not change that ID after publication. Increasing a plugin's own version does not increase the SDK version.

Declared commands are reusable actions. Users can select them in **Gestures > Run a plugin action**. The plugin subpage lets users opt each command into the kiosk drawer or expose it as an ESPHome button in Home Assistant. Settings configures those placements and does not run the command. These connections work with all supported SDK versions without additional plugin capabilities.

Keep command IDs stable across releases. The host retains shortcut choices for commands that survive an update and removes choices for deleted commands. New commands start without drawer or Home Assistant exposure. Commands run through `execute` only while the plugin is running. A saved gesture targeting a disabled or missing plugin reports a failure. Plugins should use hardware entities such as RGB lights for ongoing stateful control and commands for individual operations.

## Lifecycle

Implement `me.jxl.kiosk.plugins.KioskPlugin`:

| Callback | Responsibility |
| --- | --- |
| `start(host, settings)` | Save the host handle and acquire resources |
| `configure(settings)` | Apply the complete validated configuration |
| `execute(command, arguments)` | Run a declared command. SDK 1 passes an empty arguments map |
| `onEvent(event, payload)` | Handle a window event, RGB command or subscribed KS event. See the [complete interaction reference](ks-api.md) |
| `stop()` | Release timers, threads and resources |

Callbacks run serially on a worker dedicated to the plugin. They must finish within three seconds. The host disables a plugin after a callback error or timeout. Keep long work asynchronous and honor interruption. Host access is revoked after the plugin stops. SDK 1 read and subscription calls made after revocation throw. A timed-out thread can keep running if it ignores interruption, since this runtime does not isolate plugin code.

The host rejects settings with unknown keys or incorrect types. Defaults fill missing keys. First-time installation does not run plugin code and leaves the plugin disabled. Updates automatically stop the old session and restart the replacement if the plugin was enabled and the master switch is on. Disabled plugins stay disabled. Updates while the master switch is off retain the enabled choice without running code. The host calls `start` after explicit enable or at app startup for an enabled plugin while the master **Enable Plugins** switch is on. Turning the master switch off calls `stop` and revokes host callbacks without changing the plugin's saved enabled choice or settings. Turning it on starts the selected plugins again. An off master switch prevents startup and execution across app restarts.

To replace a loaded plugin, preview the same repository again and install its replacement release. KS stops the old session and restores its enabled state automatically. A normal update does not require an app restart. Compatible settings are retained. Removing a plugin deletes its saved settings.

## Floating window

The host exposes these methods:

```java
host.showWindow("Hello World", "Your message", "Say hello");
host.hideWindow();
host.log("A short diagnostic message");
```

`showWindow` creates or updates the plugin's one window. Updating its text keeps its drag position. Titles and button labels allow up to 80 characters. Messages allow up to 4096 characters. An empty button label hides the action button. All text is rendered as plain text by Flutter.

The action button sends `window.action`. Closing the window sends `window.closed`. Closing does not disable the plugin. Calling `hideWindow` does not send a close event, so plugins can manage visibility without a callback loop.

The window floats over the dashboard and is draggable by its title bar. Other kiosk surfaces, including the drawer and screensaver, can cover it. It does not create an Android system overlay. No dashboard DOM access, custom HTML or arbitrary Flutter widget loading is provided.

## Trust and compatibility

A plugin executes inside Kiosk Satellite with the application's identity. This is not a sandbox. The capabilities array identifies SDK requirements and does not restrict arbitrary Java code. Do not copy app implementation classes into your plugin or rely on internal classes discovered through reflection.

SDK 1 includes native libraries, RGB entities, explicit state queries, passive events and transient controls. See the [complete interaction reference](ks-api.md) for the exact contract and exclusions. Repository updates are reviewed from the installed entry row.

Publish source code alongside release packages. A checksum detects changed bytes but does not authenticate a publisher. Users must trust the repository author. Plugin authors remain responsible for all licenses and notices included in their packages.

## Rich settings and hardware

These features are part of SDK 1. Declare `apiVersion: 1` and the capabilities your plugin needs.

Settings can add `group` and `description` fields. A `number` setting declares finite `min`, `max`, `step` and a numeric default. Values must match that range and step. A `color` setting stores `#RRGGBB` and uses the app color picker. A `select` setting declares up to 32 unique string `options` and a default from that list. The same setting types appear in the native app and Remote Admin. On the kiosk, string settings show their current value and open a text edit dialog when tapped. Each completed edit saves automatically. Sliders save on release. Remote text fields save on blur or Enter. There is no separate Save settings row. Each save calls `configure` with the complete settings object, so plugins should avoid expensive work when unchanged values are submitted.

The `native` capability allows shared libraries under `native/ABI/libNAME.so`, with arm64-v8a, armeabi-v7a and x86_64 supported. ELF architecture and stored digests are checked. The existing 4 MB package and expanded-content limits still apply. The host supplies a separate native library path for each session, so a disabled plugin can be enabled again without reusing a library owned by a previous class loader. Files potentially referenced by loaded code remain until a later app start.

| Host method | Purpose |
| --- | --- |
| `nativeLibraryPath(name)` | Absolute path to a verified session copy of `libNAME.so`. Requires `native` |
| `packagePath()` | Verified DEX container path for a plugin-owned helper. Requires `native` |
| `status(message, error)` | Runtime status text in the plugin subpage, up to 1000 characters |
| `saveSettings(values)` | Validate and persist plugin-originated setting changes |
| `publishLight(key, name, effects, state)` | Register or update an RGB light. Requires `entities` |
| `removeLight(key)` | Remove a light from this plugin's active catalog |

RGB state contains `on`, `brightness`, `red`, `green`, `blue` and `effect`. Numeric channels use 0 to 1. A plugin can publish up to four lights and each can advertise up to 24 effects. Home Assistant commands arrive through `onEvent("light.KEY", payload)` with the fields that were supplied. Stable object IDs are namespaced by plugin ID and key. Command handlers should update their internal state, apply the change and publish the resulting state. Use `saveSettings` when the command changes persisted settings.

Entity catalog changes reconnect ESPHome. State updates do not. Disabling a plugin revokes its host and removes its entities. Plugin-owned threads and helper processes must stop before `stop()` returns. The three-second callback deadline still applies. Long permission prompts must happen on a plugin-owned worker that can be canceled during shutdown.

The Rockchip LED Control repository demonstrates these additions without putting a device driver into KS itself.

## Sensors, selects and switches

The `entities` capability also supports numeric sensors, text sensors, binary sensors, writable selects and switches. See the [entity API](entities.md) for signatures, metadata, lifecycle rules and migration steps. Hello World demonstrates all five with simulated readings, a select for its Pattern setting and a switch for Show demo chart.

## Charts

Publish line or bar charts with `host.publishSeries(key, chart)` and remove it with `host.removeSeries(key)`. Charts are read-only runtime displays, separate from settings. They render in the plugin subpage on-device and in Remote Admin. No extra capability or SDK version is required. The [chart reference](charts.md) covers the schema, limits, lifecycle and steps for updating an existing repository. Hello World demonstrates every settings control alongside a live simulated chart.

## Status tiles

Put a verdict on the Remote Admin Overview Status panel with `host.publishStatusTile(key, title, level, text)` and take it down with `host.removeStatusTile(key)`. Levels are `on`, `warn`, `off` and `""`, the same vocabulary the built-in tiles use. Plugin tiles follow the built-in six, name their plugin and open the plugin's subpage. No extra capability or SDK version is required. The [status tile reference](status-tiles.md) covers the limits and lifecycle. Hello World publishes one tile driven by its Status tile demo settings.

## KS state and transient controls

Declare `host.read` to inspect supported KS state and subscribe to passive events. Declare `host.control` for transient controls such as dismissing the screensaver or showing and hiding camera views and Now Playing. The [complete KS interaction reference](ks-api.md) lists every command, event, payload, capability and limit and includes a buildable example.

## Shizuku

Declare `shizuku` for optional privileged command execution through KS. The [Shizuku guide](shizuku.md) documents setup, user permission, state events, command results and lifecycle limits. Use the separate example to test access without changing the standard Hello World template.

## Screensavers

Declare `screensaver`, bundle an HTML entry and its resources under `assets/` and register it with `host.publishScreensaverAsset(key, title, entry, data)`. Files load on demand from the verified package. Small inline renderers can still use `host.publishScreensaver(key, title, html)`. It becomes a selectable stock screensaver mode. KS owns all screensaver policy and removes the renderer when the plugin session ends. Hello World includes a bouncing DVD logo with color and size settings. The [screensaver reference](screensavers.md) covers the contract, document limits, lifecycle and fleet behavior.

## Home Assistant entity settings and reads

Use the `entity` setting type for KS's searchable Home Assistant entity picker in both interfaces. The value is an entity ID or an empty string. Declare `host.read` to read its state or subscribe to changes. See the [Home Assistant entity guide](home-assistant.md) and the Hello World demo.

## Group settings, charts and readings

An optional top-level `groups` array orders settings groups and places their charts and readings directly below their settings. Use the same title as the settings' `group` field:

```json
"groups": [
  {
    "title": "Chart demo",
    "charts": ["demo"],
    "readingsTitle": "Chart readings",
    "readings": ["sensor.wave", "text_sensor.summary"]
  },
  {
    "title": "Home Assistant demo",
    "readingsTitle": "Home Assistant readings",
    "readings": ["text_sensor.ha_state", "text_sensor.ha_details"]
  }
]
```

Charts appear after the group's settings, followed by a separate readings section. `readingsTitle` defaults to `Readings`. Chart references use the key passed to `publishSeries`. Reading references use `type.key`, where type is `sensor`, `text_sensor`, `binary_sensor`, `select` or `switch`. Each reference can belong to only one group. References may name readings or charts that have not been published yet. Empty output sections stay hidden.

Declare at most 20 groups, with unique titles matching existing settings groups. A group can reference up to four charts and 32 readings. Settings groups omitted from `groups` follow in their original manifest order. Unassigned charts and readings appear after the settings groups. These placements affect only the plugin subpage, not Home Assistant entities or their names. Runtime updates preserve settings edits and chart interaction state. All fields remain part of SDK 1.
