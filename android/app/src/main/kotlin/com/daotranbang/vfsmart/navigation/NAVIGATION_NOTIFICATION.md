# Navigation Notification Reading Logic

## Overview

The app reads Google Maps turn-by-turn directions without using the Maps SDK or any API key.
It works by intercepting the persistent status bar notification that Maps posts during active navigation.

**Class:** `NavigationNotificationService` (`NotificationListenerService`)
**State output:** `NavigationNotificationService.navigationState: StateFlow<NavigationState>`

---

## How It Works

### 1. Permission

The user must grant **Notification Access** in Android Settings (`Settings > Apps > Special app access > Notification access`). Without it, the service never receives callbacks.

The app prompts for this on launch via `HomeScreen` and shows a banner if not granted.

### 2. Service Lifecycle

`NotificationListenerService` is a bound service managed by Android. The system connects it automatically when the app has notification access permission.

| Callback | What happens |
|---|---|
| `onListenerConnected()` | Scans all currently active notifications and processes any existing Maps notification immediately (handles the case where Maps was already navigating when the app started) |
| `onListenerDisconnected()` | Nothing — state is left as-is until reconnected |
| `onNotificationPosted(sbn)` | Called on every notification update; processes if from Maps |
| `onNotificationRemoved(sbn)` | If Maps notification disappears, resets `navigationState` to default (inactive) |

### 3. Filtering

Every notification is filtered by package name:

```
com.google.android.apps.maps
```

All other packages are ignored immediately.

### 4. Extracting Data from the Notification

Google Maps encodes navigation data into two notification extras:

| Extra | Key | Content | Example |
|---|---|---|---|
| Title | `Notification.EXTRA_TITLE` | Distance to next maneuver | `"300 m"`, `"1.2 km"` |
| Text | `Notification.EXTRA_TEXT` | Street name / instruction | `"Turn left onto Lê Lợi"` |

**Important:** Maps stores these as `CharSequence`, not `String`. Using `getString()` always returns `null`. The code uses `getCharSequence()` and converts to String.

If `distance` (title) is null or blank, the notification is not a navigation update and is ignored.

### 5. Direction Detection

Direction is determined by two methods, tried in order:

#### Method 1 — Icon Pixel Analysis (API 23+)

Maps posts a large icon in the notification that shows the turn arrow. The direction is read from this bitmap:

1. Load the large icon via `notification.getLargeIcon()`
2. Render it to a `128×128` bitmap using `RGB_565` (2 bytes/px to save memory)
3. Sample only the **top third** of the bitmap — this is where the arrowhead points
4. Count non-transparent pixels (`alpha > 32`) in the **left half** vs **right half**
5. Compute `leftRatio = leftCount / total`

| `leftRatio` | Direction |
|---|---|
| `> 0.60` | LEFT (arrowhead is mostly on left side) |
| `< 0.40` | RIGHT (arrowhead is mostly on right side) |
| `0.40 – 0.60` | STRAIGHT |
| `total < 20` | Inconclusive → fall back to text |

**Why top third only?** The arrowhead (the pointy tip) reliably indicates direction and is always in the top portion of a turn arrow icon. The stem of the arrow in the bottom half is centered and would skew the count toward equal.

**Why non-transparent instead of brightness?** Maps uses coloured arrows (blue, green) that are not "bright" in luma terms, so brightness thresholding would miss them.

#### Method 2 — Text Keyword Fallback

Used when: API < 23, icon is null, icon is fully transparent, or icon analysis throws an exception.

Scans `EXTRA_TEXT` (the street/instruction string) for keywords:

| Keyword | Direction |
|---|---|
| `"u-turn"` / `"uturn"` | U_TURN |
| `"left"` | LEFT |
| `"right"` | RIGHT |
| `"roundabout"` / `"rotary"` | ROUNDABOUT |
| *(anything else)* | STRAIGHT |

### 6. State Output

After parsing, `NavigationState` is updated:

```kotlin
data class NavigationState(
    val isActive: Boolean,        // true when navigation is running
    val maneuver: String,         // "TURN LEFT", "TURN RIGHT", "U-TURN", "ROUNDABOUT", "CONTINUE"
    val distance: String,         // raw string from Maps, e.g. "300 m"
    val direction: Direction      // enum: LEFT, RIGHT, STRAIGHT, U_TURN, ROUNDABOUT
)
```

`isActive = false` (default) when no Maps notification is present.

The state is a `StateFlow` on the companion object so it can be collected from any composable without needing a ViewModel.

---

## Known Limitations

- **Maps notification format can change** without notice. If Google updates Maps, icon pixel layout or extra keys may shift, breaking direction detection.
- **Icon analysis cannot distinguish U-turn** — a U-turn arrow is symmetric, so it always falls to STRAIGHT from pixel counting. U-turn is only detected via the text fallback.
- **Roundabout** is likewise symmetric and text-only.
- **Language-dependent text fallback** — keyword matching is English only. If Maps is in another language, text fallback always returns STRAIGHT.
- **No distance unit conversion** — the distance string is passed through as-is from Maps (metric or imperial depending on device locale).
