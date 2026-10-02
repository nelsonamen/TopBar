# Status tiles

SDK 1 plugins can put a tile on the Status panel of the Remote Admin Overview, next to Home Assistant, Voice Satellite, ESPHome, Media Player, Service and App Version. That panel is where someone looks to answer "is this kiosk healthy?", so use it for the one or two verdicts a plugin knows that belong there. A tile is a title, a one-line state, a colored dot and a link to the plugin's own subpage.

The kiosk screen has no Overview by design, so tiles appear in Remote Admin only. Readings and entities remain the way to show values on-device and in Home Assistant.

## Publish a tile

Call `publishStatusTile(String key, String title, String level, String text)` on the host. No manifest capability is required. Keep `apiVersion: 1`.

```java
host.publishStatusTile("webview", "WebView responsiveness", "on", "smooth");
```

Each call replaces the tile at that key. Publish again whenever the verdict changes and call `removeStatusTile(String key)` when the plugin no longer has an answer, for example when the feature behind it is switched off.

| Field | Contract |
| --- | --- |
| `key` | Stable tile ID matching `[a-z][a-z0-9_]{0,39}`. Scoped to the plugin |
| `title` | 1 to 40 characters, no control characters. The tile's name |
| `level` | `"on"` for a green dot, `"warn"` for amber, `"off"` for red or `""` for a muted gray dot |
| `text` | Up to 80 characters, no control characters. The state line under the title |

Use `""` for a state that is not a problem and not a success, such as a feature that is idle or switched off. The built-in tiles use the same vocabulary: Media Player is muted while idle and amber only when something needs a person.

## Provenance

Plugin tiles sit after the six built-in tiles and carry the plugin's name under the state line, so a tile never reads as a claim Kiosk Satellite itself is making. Tapping a plugin tile opens that plugin's subpage in Plugin Manager.

## Updates and lifetime

- Each plugin session can own at most two tiles. Updating an existing key does not add another tile.
- At most eight tile changes, including removals, are accepted per plugin per one-second rate window. Invalid input throws `IllegalArgumentException`. A stopped session or exhausted rate limit throws `IllegalStateException`. Rejected operations leave the previous tile intact.
- Live notifications are coalesced over 250 milliseconds. Remote Admin reads the tiles with the rest of the Status panel, every 30 seconds while the Overview is in view. Publish the current verdict, not a stream of intermediate states.
- Disabling, uninstalling, updating or stopping the plugin, stopping the app or turning off **Enable Plugins** removes the session's tiles. Tiles are never persisted, exposed as Home Assistant entities or included in fleet sync.
- KS revokes the host before calling `stop`, so do not call `removeStatusTile` there. Late publications are rejected.

Every save calls `configure` with the complete settings object. Track what you last published and skip unchanged tiles so repeated saves do not spend the change budget.

## Hello World demo

The **Status tile demo** group publishes one tile titled **Hello World demo**. **Show status tile** publishes or removes it, **Tile level** picks Healthy, Attention, Problem or Muted for the dot and **Tile text** sets the state line. Open the Remote Admin Overview to see the tile move as you change them. See [StatusTileDemo.java](../src/me/jxl/kiosk/plugins/hello/StatusTileDemo.java).

## Updating an existing plugin repository

1. Update Kiosk Satellite to a build that includes status tile support.
2. Copy the current files from [`sdk/src/me/jxl/kiosk/plugins/`](../sdk/src/me/jxl/kiosk/plugins) into the same directory in your plugin repository. Do not package the SDK classes in your plugin ZIP.
3. Keep `apiVersion: 1`. Call `host.publishStatusTile(...)` where your plugin settles on a verdict and `host.removeStatusTile(...)` when it stops having one.
4. Update test hosts to implement `publishStatusTile` and `removeStatusTile` if your tests exercise tile publication. Build and test with your repository's tools. The template uses `python3 tools/test.py` and `python3 tools/build.py`.
5. Test the local ZIP through **Plugin Manager > Developer Tools > Install from ZIP**, then publish a release as described in [Creating plugins](creating-plugins.md).

## Remote Admin API

Authenticated administrators can call `getPluginStatusTiles` with no parameters. The result lists every running plugin's tiles with `key`, `title`, `level`, `text`, `pluginId` and `pluginName`. It returns an empty list while Plugin Manager is off or no running plugin has published a tile. Fleet credentials cannot call it.
