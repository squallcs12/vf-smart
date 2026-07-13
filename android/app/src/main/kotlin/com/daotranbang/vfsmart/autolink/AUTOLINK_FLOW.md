# AutoLink Auto-Connect Flow

## Overview

The phone runs **AutoLink Pro** (`com.link.autolink.pro`) to mirror its own screen onto
the car display over USB. This subsystem makes that connection happen
**automatically and hands-free** — when the user gets in the car, the app launches AutoLink
(which is always in **USB mode** and auto-mirrors on launch), then returns to our own
`MainActivity`. (Screen-capture consent is pre-granted via `appops`, so the "Start now"
dialog never appears.)

**Two classes:**

| Class | Role |
|---|---|
| `AutoLinkService` (foreground `Service`) | Detects the triggers, debounces, launches AutoLink Pro, and verifies the connection |
| `AutoLinkAccessibilityService` (`AccessibilityService`) | Returns to our `MainActivity` after launch (AutoLink Pro is always in USB mode, so no UI to drive) |

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

Both branches end up back on our own mirror screen; the difference is that the
accessibility branch also arms the post-launch verification (`scheduleConnectionCheck`).

---

## Accessibility service — wait for mirroring, then return to app

AutoLink Pro is **always in USB mode** and auto-mirrors on launch, so there is no toggle
to drive. `AutoLinkAccessibilityService.startConnecting()` therefore does no UI automation
— it stays on AutoLink and **polls until AutoLink's "Mirroring" notification appears**
(`NavigationNotificationService.autoLinkMirroringActive`), then returns to our
`MainActivity` so our screen is what gets mirrored:

```
startConnecting()
  → poll every POLL_INTERVAL_MS (500 ms) for the Mirroring notification
      ├─ notification seen ──────────────► finishConnection()
      └─ not seen after MIRRORING_WAIT_MAX_POLLS (≈30 s)
            → finishConnection() anyway

finishConnection()
  → return to our MainActivity  (after a short settle delay)
  → invoke onConnected  (→ AutoLinkService.scheduleConnectionCheck())
```

Screen-capture consent is pre-granted on the rooted phone via
`appops set com.link.autolink.pro PROJECT_MEDIA allow` (done at app start — see Android
`CLAUDE.md`), so the systemui "Start now" capture dialog never appears.

> The accessibility service is kept (rather than removed) because its presence is what
> routes `launchAutoLink()` through the branch that arms `scheduleConnectionCheck()`, and
> because `finishConnection()` brings our `MainActivity` back to the foreground.

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

## Keep screen awake during the Android Auto session

MediaProjection keeps capturing even after the phone's display times out, so if the
screen sleeps the car shows a blank/locked screen. The `CarConnection` observer ties the
display to the Android Auto session via `ScreenAwakeController` (root, rooted S20+ only):

- **Android Auto connects** → `keepAwake()`: `input keyevent 224` (wake) +
  `wm dismiss-keyguard` (unlock without password) + `svc power stayon true` (hold the
  screen on for the whole session).
- **Android Auto disconnects** → `release()`: `svc power stayon false` (restore the
  normal timeout).

A secure lock (PIN/pattern) still can't be auto-cleared; on a non-rooted device `su`
fails and this is a no-op.

---

## Driving speed & light reminder (moved out of this service)

Vehicle speed and the night light reminder are **no longer owned by `AutoLinkService`**.
They live with the MirrorScreen speed cell so the app keeps a single GPS listener:

- `navigation/DrivingState` is the one app-wide speed source (`speedKmh` + `isMoving`,
  >5 km/h). The MirrorScreen GPS (the speed cell) is the only producer; it resets speed
  to 0 when it stops tracking. `MainActivity` reads `DrivingState.isMoving`.
- The **light reminder** runs in `OdoGpsSpeedCell`: the first time speed exceeds 5 km/h
  **at night** (hour ≥ 18 or < 6) it plays `R.raw.light_reminder` via the shared
  `util/playLightReminder` (ducks other audio with navigation-guidance focus). It plays
  **at most once per Android Auto session** via `LightReminderSession` — a shared gate
  reset on the `CarConnection` PROJECTION event and also honored by
  `CarStatusViewModel`'s ESP32-driven reminder, so the driver hears it once per session
  regardless of which source fires first.

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
| `POLL_INTERVAL_MS` | 500 ms | A11y | Mirroring-notification poll interval |
| `MIRRORING_WAIT_MAX_POLLS` | 60 (≈30 s) | A11y | Max wait for the Mirroring notification before returning anyway |
