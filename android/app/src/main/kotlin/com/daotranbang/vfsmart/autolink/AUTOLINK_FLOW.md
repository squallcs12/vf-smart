# AutoLink Auto-Connect Flow

## Overview

The phone runs **AutoLink Pro** (`com.link.autolink.pro`) to mirror its own screen onto
the car display over USB. This subsystem makes that connection happen
**automatically and hands-free** — when the user gets in the car, the app launches AutoLink,
drives its UI through the accessibility service to switch it to **USB mode**, then returns to
our own `MainActivity`. (Screen-capture consent is pre-granted via `appops`, so the
"Start now" dialog never appears.)

**Two classes:**

| Class | Role |
|---|---|
| `AutoLinkService` (foreground `Service`) | Detects the triggers, debounces, launches AutoLink Pro, and verifies the connection |
| `AutoLinkAccessibilityService` (`AccessibilityService`) | Drives the AutoLink Pro UI: switches it to USB mode |

> **Lifecycle:** the service runs only while the phone is plugged in — `VF3Application`
> starts it on `ACTION_POWER_CONNECTED` (and at launch if already powered) and stops it
> on `ACTION_POWER_DISCONNECTED`.

> **Target device:** the rooted Samsung Galaxy S20+ phone (see `CLAUDE.md` → Target
> Device). This flow assumes a **rooted phone** — accessibility is enabled via `su` and
> screen-capture consent is pre-granted via `appops`.

---

## Triggers — how connection starts

`AutoLinkService` listens for four independent triggers. Any of them calls
`launchAutoLink()` (directly or via `triggerLaunch()`):

| # | Source | Path | Notes |
|---|---|---|---|
| 1 | **Android Auto connects** | `CarConnection` observer → `CONNECTION_TYPE_PROJECTION` | Sets `androidAutoConnected = true` |
| 2 | **Car mode entered** | `ACTION_ENTER_CAR_MODE` broadcast | `UiModeManager` system broadcast |
| 3 | **Media double-press** | `MediaSessionManager` monitor → pause→play within 1 s | `skipCheck = true`; re-pauses music so it doesn't keep playing |
| 4 | **Explicit intent** | `onStartCommand` with `ACTION_LAUNCH_AUTOLINK` | Carries `EXTRA_SKIP_CHECK` / `EXTRA_IS_RETRY`; used by `triggerLaunch()` and the retry path |

### Double-press detection (trigger 3)

The service tracks every active `MediaController` (via the notification-listener
component `NavigationNotificationService`). When a controller goes
`PAUSED → PLAYING` inside `DOUBLE_PRESS_WINDOW_MS` (1 s), it's treated as a deliberate
double-tap of the steering-wheel play/pause button and fires the launch. The controller
is re-paused so audio doesn't resume.

---

## `launchAutoLink()` — the gate

Before doing anything, `launchAutoLink(skipCheck, isRetry)` applies two guards:

1. **Debounce** — ignores calls within `DEBOUNCE_MS` (5 s) of the last launch.
2. **Already-connected check** — unless `skipCheck`, skips if
   `NavigationNotificationService.autoLinkMirroringActive` is already `true`
   (AutoLink is mirroring, so there's nothing to do).

If it proceeds:

```
startActivity(AutoLink Pro MainActivity)   // NEW_TASK | CLEAR_TASK
   │
   ├─ accessibility service available?
   │     YES → accessibility.startConnecting(onConnected = ::scheduleConnectionCheck)
   │     NO  → after RETURN_DELAY_MS (2 s), return to our MainActivity
   │           with EXTRA_NAVIGATE_MIRROR = true
```

If the accessibility service isn't running we can't drive AutoLink's UI, so we just bounce
back to our own mirror screen.

---

## Accessibility state machine

`AutoLinkAccessibilityService.startConnecting()` runs a polling state machine
(`POLL_INTERVAL_MS` = 500 ms, overall `TIMEOUT_MS` = 30 s). It only sees windows from
`com.link.autolink.pro`.

```
SWITCHING_TO_USB
  ├─ USB button visible   (= WiFi mode active)
  │     → click USB ──────────────────► finishConnection()
  ├─ WiFi button visible  (= already USB mode)
  │     → nothing to do ──────────────► finishConnection()
  └─ neither yet visible  (UI still loading)
        → retry up to USB_SWITCH_MAX_RETRIES (6),
          then finishConnection() anyway

finishConnection()
  → state = IDLE
  → return to our MainActivity
  → invoke onConnected  (→ AutoLinkService.scheduleConnectionCheck())
```

The two buttons are mutually exclusive — AutoLink shows the toggle for the mode you're
**not** in:

- `action_button_usb` visible ⇒ currently in **WiFi mode** ⇒ tap it to switch to USB.
- `action_button_wifi` visible ⇒ **already in USB mode** ⇒ nothing to do.

No device picking, no WiFi-mode scan, and no "Start now" click: screen-capture consent is
pre-granted on the rooted phone via `appops set com.link.autolink.pro PROJECT_MEDIA allow`
(done at app start — see Android `CLAUDE.md`), so the systemui capture dialog never
appears.

### Enabling the service (root)

`AutoLinkAccessibilityService.enableViaRoot()` writes
`enabled_accessibility_services` / `accessibility_enabled` via `su` — no manual
Settings toggle needed. Called from `AutoLinkService.start()`.

---

## Connection verification & retry

After the accessibility flow finishes, the `onConnected` callback runs
`scheduleConnectionCheck()` in `AutoLinkService`:

```
poll every CONNECTION_CHECK_INTERVAL_MS (6 s), up to 5 checks (~30 s total)
  ├─ autoLinkMirroringActive == true  → success, reset retry counter
  └─ still false after 5 checks
        ├─ autoRetryCount < MAX_AUTO_RETRIES (1)
        │     → triggerLaunch(skipCheck = true, isRetry = true)   // one retry
        └─ else → give up, reset counter
```

Mirroring status is read from `NavigationNotificationService.autoLinkMirroringActive`
(the AutoLink "Mirroring" status-bar notification).

---

## Driving speed & light reminder (moved out of this service)

Vehicle speed and the night light reminder are **no longer owned by `AutoLinkService`**.
They live with the MirrorScreen speed cell so the app keeps a single GPS listener:

- `navigation/DrivingState` is the one app-wide speed source (`speedKmh` + `isMoving`,
  >5 km/h). The MirrorScreen GPS (the speed cell) is the only producer; it resets speed
  to 0 when it stops tracking. `MainActivity` reads `DrivingState.isMoving`.
- The **light reminder** runs in `OdoGpsSpeedCell`: the first time speed exceeds 5 km/h
  **at night** (hour ≥ 18 or < 6) it plays `R.raw.light_reminder` once via the shared
  `util/playLightReminder` (ducks other audio with navigation-guidance focus). It plays
  once per screen session.

---

## Exposed state (`StateFlow`)

| Flow | Meaning |
|---|---|
| `AutoLinkService.androidAutoConnected` | Android Auto projection is connected |
| `DrivingState.isMoving` | Vehicle speed > 5 km/h (produced by the MirrorScreen speed cell) |

---

## Key constants

| Constant | Value | Where | Purpose |
|---|---|---|---|
| `DEBOUNCE_MS` | 5 s | Service | Ignore repeat launch triggers |
| `DOUBLE_PRESS_WINDOW_MS` | 1 s | Service | Media pause→play double-tap window |
| `CONNECTION_CHECK_INTERVAL_MS` | 6 s | Service | Verification poll interval (×5) |
| `MAX_AUTO_RETRIES` | 1 | Service | Retries if mirroring never starts |
| `RETURN_DELAY_MS` | 2 s | Service | Delay before bouncing back when no a11y |
| `POLL_INTERVAL_MS` | 500 ms | A11y | UI poll interval |
| `TIMEOUT_MS` | 30 s | A11y | Overall connect timeout |
| `USB_SWITCH_MAX_RETRIES` | 6 | A11y | Retries waiting for the mode buttons to appear |

> **AutoLink-Pro-specific view IDs:** `action_button_usb` / `action_button_wifi` are read
> by view ID and will break if that app updates its layout.
