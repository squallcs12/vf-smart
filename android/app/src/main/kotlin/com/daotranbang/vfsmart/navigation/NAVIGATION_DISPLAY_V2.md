# Navigation Cell Display Logic — V2 (BLE GATT Server)

## Overview

V2 replaces the Google Maps notification listener (v1) with a **BLE GATT server**.
The Android phone advertises as a BLE peripheral. A BLE client — such as the
ESP32 in the car, or another phone running a nav app — connects and writes
turn-by-turn data directly to the phone.

**GATT server:** `NavigationGattServer`
**Display composable:** `OdoNavCell` — `MirrorScreen.kt` (unchanged from v1)
**State:** `NavigationGattServer.navigationState: StateFlow<NavigationState>`

---

## Architecture

```
┌────────────────────────┐         BLE          ┌───────────────────┐
│  BLE Client            │  ──── WRITE ────────▶ │  Android Phone    │
│  (ESP32 / nav app)     │   "LEFT|300 m"         │  GATT Server      │
│                        │                        │  NavigationGatt   │
│  Reads from:           │                        │  Server.kt        │
│  - Google Maps API     │◀──── Connect ────────  │                   │
│  - Any nav source      │   (phone advertises)   └────────┬──────────┘
└────────────────────────┘                                 │ StateFlow
                                                           ▼
                                                   OdoNavCell (UI)
```

The phone does **not** read Google Maps or any navigation app directly.
The source of navigation data is entirely up to the BLE client.

---

## BLE Identifiers

| Item | UUID |
|------|------|
| GATT Service | `A1B2C3D4-E5F6-7890-ABCD-EF1234567890` |
| Nav Characteristic | `A1B2C3D4-E5F6-7890-ABCD-EF1234567891` |

Characteristic properties: **WRITE** + **WRITE_NO_RESPONSE**

---

## Wire Protocol

The client writes a **UTF-8 string** to the nav characteristic.

### Format

```
DIRECTION|DISTANCE
```

| Field | Values | Example |
|-------|--------|---------|
| `DIRECTION` | `LEFT`, `RIGHT`, `STRAIGHT`, `U_TURN`, `ROUNDABOUT` | `LEFT` |
| `DISTANCE` | Any string (passed through to display) | `300 m`, `1.2 km` |

### Examples

| Payload | Meaning |
|---------|---------|
| `LEFT\|300 m` | Turn left in 300 m |
| `RIGHT\|1.2 km` | Turn right in 1.2 km |
| `STRAIGHT\|500 m` | Continue straight for 500 m |
| `U_TURN\|100 m` | U-turn in 100 m |
| `ROUNDABOUT\|200 m` | Enter roundabout in 200 m |
| `` (empty) | Navigation inactive — clears the display |

Unknown `DIRECTION` tokens default to `STRAIGHT`.

---

## Server Lifecycle

`NavigationGattServer` is instantiated and controlled inside `MirrorScreen`:

```kotlin
val gattServer = remember { NavigationGattServer(context) }
DisposableEffect(Unit) {
    gattServer.start()
    onDispose { gattServer.stop() }
}
```

| Event | What happens |
|-------|-------------|
| Mirror screen enters composition | `start()` — opens GATT server, begins advertising |
| BLE client connects | `onConnectionStateChange` logged, no state change |
| Client writes nav data | `onCharacteristicWriteRequest` → `parsePayload()` → `StateFlow` updated |
| Client writes empty string | State reset to `NavigationState()` (inactive) |
| Mirror screen leaves composition | `stop()` — advertising halted, server closed, state reset |

`start()` is **idempotent** — calling it twice does nothing if already running.

---

## Advertising

The phone advertises:
- **Mode**: `ADVERTISE_MODE_BALANCED`
- **Connectable**: yes
- **Timeout**: 0 (indefinite — stops only when `stop()` is called)
- **TX power**: medium
- **Advertise data**: device name + service UUID

The client scans for `SERVICE_UUID` to find the phone without needing to know
its Bluetooth address in advance.

---

## Permissions

| API level | Permissions needed |
|-----------|--------------------|
| < 31 (Android 11) | `BLUETOOTH`, `BLUETOOTH_ADMIN` (manifest only, no runtime request needed) |
| 31+ (Android 12+) | `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` (runtime grant required) |

If permissions are not yet granted when `start()` is called, the server logs
a warning and returns without crashing. The display shows `"NO NAVIGATION"`.

---

## Display (OdoNavCell)

`OdoNavCell` is **identical to v1** — it only reads `NavigationState`, regardless
of how that state was populated (notification vs BLE). See `NAVIGATION_DISPLAY.md`
for the full visual layout, icon mapping, colour logic, and state transitions.

---

## ESP32 Client Example

Minimal Arduino/ESP-IDF write to the nav characteristic:

```cpp
// Connect to the phone's GATT server (address discovered by scanning for SERVICE_UUID)
// Then write the navigation payload to NAV_CHAR_UUID:

const char* SERVICE_UUID = "A1B2C3D4-E5F6-7890-ABCD-EF1234567890";
const char* NAV_CHAR_UUID = "A1B2C3D4-E5F6-7890-ABCD-EF1234567891";

void sendNavUpdate(BLERemoteCharacteristic* navChar,
                   const char* direction, const char* distance) {
    char payload[64];
    snprintf(payload, sizeof(payload), "%s|%s", direction, distance);
    navChar->writeValue(payload, strlen(payload));
}

// Example usage:
sendNavUpdate(navChar, "LEFT", "300 m");    // turn left in 300 m
sendNavUpdate(navChar, "STRAIGHT", "1 km"); // continue straight
sendNavUpdate(navChar, "", "");             // clear (nav ended)
```

---

## Differences from V1

| Aspect | V1 (Notification) | V2 (GATT Server) |
|--------|-------------------|------------------|
| Data source | Google Maps persistent notification | Any BLE client |
| Permission | Notification Access (special, user must enable in settings) | `BLUETOOTH_ADVERTISE` + `BLUETOOTH_CONNECT` (standard runtime) |
| Maps dependency | Yes — breaks if Maps changes notification format | None |
| Direction detection | Icon pixel analysis + text keyword fallback | Explicit token in wire protocol |
| U-turn / roundabout | Text fallback only (icon is symmetric) | Full support via `U_TURN` / `ROUNDABOUT` tokens |
| Language dependency | English keywords only | None — direction is a fixed token |
| Service lifecycle | Managed by Android (bound when permission granted) | Managed by `MirrorScreen` `DisposableEffect` |
