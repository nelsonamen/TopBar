# Reading Home Assistant entities

SDK 1 plugins can read Home Assistant entity states and subscribe to changes through `host.read`. KS manages authentication using its configured Home Assistant URL and token. The plugin receives entity data, never the connection credentials. This API does not call services or change Home Assistant state.

## Choose an entity in Settings

Declare `host.read` in `capabilities` and add an `entity` setting:

```json
{
  "key": "roomEntity",
  "type": "entity",
  "title": "Room temperature",
  "default": "",
  "group": "Home Assistant",
  "description": "Choose the entity to display."
}
```

Both settings interfaces use KS's existing searchable entity picker. The value is the selected entity ID, such as `sensor.living_room_temperature`. An empty string means no selection. Clear removes the selection. Changes save automatically and arrive in `configure` like other plugin settings.

Entity IDs must match `domain.object_id`, with lowercase letters, digits or underscores and at most 255 characters. Wildcards and URL paths are rejected. Entity settings are a convenience for choosing IDs, not an access restriction. `host.read` allows reading any entity available to KS's configured HA account. Plugins run inside KS and are trusted code.

## Read the current state

```java
host.executeCommand("getHaEntityState",
    Collections.singletonMap("entityId", "sensor.living_room_temperature"),
    (ok, data, error) -> {
        if (!ok) {
            host.status(error, true);
            return;
        }
        Map<?, ?> snapshot = (Map<?, ?>) data;
        host.log("Entity status: " + snapshot.get("status"));
    });
```

| Field | Meaning |
| --- | --- |
| `entityId` | The requested entity ID |
| `status` | Availability status listed below |
| `state` | HA's state string, including literal `unknown` or `unavailable`. Null when no current state is available |
| `attributes` | Detached JSON object of the entity's attributes, including nested values. Empty when no current state is available |
| `lastChanged` | HA's last state-change timestamp in UTC, or null |
| `lastUpdated` | HA's last-update timestamp in UTC, or null |

Status is one of `available`, `unknown`, `unavailable`, `missing`, `not_configured`, `connecting`, `disconnected` or `too_large`. A valid read can return `ok=true` with one of these statuses. `ok=false` means the SDK request itself failed, such as invalid arguments or an expired plugin session.

`missing` means HA did not return that entity for its authenticated account. `disconnected` also covers authentication failures. Old state and attributes are cleared while disconnected. Do not treat a missing or unavailable value as zero, off or a successful measurement.

Attributes contain the data supplied by HA integrations. They may include personal information or URLs with their own credentials. Treat them as untrusted data and avoid logging complete attribute objects. KS omits the HA state context and does not add its own access token to the payload.

## Subscribe to changes

```java
host.subscribe("ha.entity.sensor.living_room_temperature");

// Inside onEvent:
if (event.equals("ks.ha.entity.sensor.living_room_temperature")) {
    String status = (String) payload.get("status");
    Object state = payload.get("state");
    // Update the plugin's display or hardware using the confirmed state.
}

// When configure switches to a different entity:
host.unsubscribe("ha.entity.sensor.living_room_temperature");
```

A subscription delivers the initial snapshot and subsequent changes, including attribute-only updates and removal. The payload uses the read format above plus `time`, the KS event delivery timestamp. KS merges HA's compressed updates, including deleted attributes, before delivering each snapshot.

KS opens a shared, filtered WebSocket for the entities requested by active plugins using the existing HA connection configuration. It does not subscribe to every entity or reuse the dashboard's filtered event stream. It reconnects with backoff, refreshes the initial states and restarts when the HA connection settings change. `connecting` and `disconnected` events distinguish transport availability from an entity's `unknown` or `unavailable` state.

Each plugin session can subscribe to at most 16 distinct entities. Duplicate subscriptions are harmless. Subscriptions use the existing serialized plugin callback worker and are coalesced per entity over 100 ms. Slow plugins receive the latest pending state for each entity. This is a current-state feed, not an event history or exact change counter.

Unsubscribing discards pending events for that entity. Disabling, uninstalling, updating or stopping a plugin revokes all its subscriptions. Subscribe again in `start` or the initial `configure` call. Do not unsubscribe in `stop` because KS has already revoked the host. If a user changes the selected entity, unsubscribe from the old ID and ignore callbacks for it.

Snapshots are limited to 30,000 UTF-8 JSON bytes to fit the existing SDK response and event limits. An oversized entity returns `too_large` with cleared values. A live entity that exceeds the limit remains `too_large` until a fresh full state is received, such as after resubscribing. Regular reads obtain a fresh full state. Existing callback, command-rate and session limits still apply.

`getHostApi` lists `getHaEntityState` in `commands` and describes dynamic entity events in `entitySubscriptions` as `{eventPrefix: "ha.entity.", maxEntities: 16}`. These events are not individual entries in the fixed `events` list. All of this remains SDK 1.

## Hello World demo

Open **Home Assistant demo > Home Assistant entity** in the Hello World plugin and select an entity. The plugin publishes live state and detail readings in its subpage. Change that entity in HA to see updates without polling. Clear the selection to remove those demo readings. The greeting, chart, Shizuku and DVD screensaver demos continue to work independently.

See [HomeAssistantDemo.java](../src/me/jxl/kiosk/plugins/hello/HomeAssistantDemo.java) for the complete example.
