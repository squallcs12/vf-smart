# Capturing the Android Auto display past `FLAG_SECURE` (Route 3)

> Goal: on a **rooted phone**, capture the Android Auto projected UI (rendered to a
> secure `VirtualDisplay` owned by Google Play Services) even though its layers are
> marked `FLAG_SECURE`. This note documents **Route 3 only** — the programmatic
> `SurfaceControl.captureDisplay` approach. To be done later.

## Why this works

`FLAG_SECURE` is not cryptographic. SurfaceFlinger simply *excludes* secure layers
from a capture **unless the capture explicitly asks for secure layers** via
`setCaptureSecureLayers(true)`. That flag requires the `CAPTURE_SECURE_LAYERS`
(signature/system) permission, which only a root/system context can hold.

So Route 3 = make *our* capture call privileged, rather than patching SurfaceFlinger
globally (that's Route 2).

## Steps

### 1. Find the Android Auto virtual display id / token

```bash
adb shell dumpsys SurfaceFlinger --display-id
adb shell dumpsys display          # cross-check: AA creates a VirtualDisplay during projection
```

Identify the AA virtual display (appears only while projecting to the head unit).
Note its **display id** (for `screenrecord`) and its **display token** (for the
reflection API).

### 2. Programmatic capture via hidden `SurfaceControl` API

Skip `MediaProjection` (it only captures display 0 and respects secure flags).
Use the hidden `SurfaceControl` screenshot API with secure capture enabled.
Run the process as **root/system** so `CAPTURE_SECURE_LAYERS` is granted.

```kotlin
// android.view.SurfaceControl — signatures vary by API level (see note below)
val args = SurfaceControl.DisplayCaptureArgs.Builder(displayToken)
    .setCaptureSecureLayers(true)   // <-- the key bit
    .build()
val buffer = SurfaceControl.captureDisplay(args)   // ScreenshotHardwareBuffer
val bitmap = buffer.asBitmap()                      // or feed HardwareBuffer straight to ML pipeline
```

Get `displayToken` via reflection on `SurfaceControl.getInternalDisplayToken()` /
`getPhysicalDisplayToken()`, or build a virtual-display token matching the id from step 1.

### ⚠️ API-level caveat (the main maintenance cost)

The hidden `SurfaceControl` / `DisplayCaptureArgs` / `captureDisplay` signatures
changed substantially across **Android 10 → 12 → 14**. Reflect against the exact
overload for the phone's API level. Also subject to the hidden-API blocklist — a
root/system context or an `@hiddenapi` bypass is needed.

### 3. (Alternative quick test) stock screenrecord

Once you've confirmed root can read secure layers, the framework `screenrecord`
binary may also work for a one-off capture of that display id:

```bash
adb shell screenrecord --display-id <id> /sdcard/aa.mp4
```

> If `screenrecord` still returns black, it's because the **stock** binary does NOT
> set `captureSecureLayers` — that's exactly the gap Route 3's custom call fills.
> (Route 2 / Smali Patcher fixes `screenrecord` too by patching SurfaceFlinger.)

## Pros / cons (why we picked this to revisit, not adopt yet)

**Pros**
- No system image modification — no `services.jar` patch, survives OTAs better than Route 2.
- Capture is scoped to our own process, not a device-wide security downgrade.

**Cons**
- Fragile reflection: signatures shift per Android version; breaks on major OS updates.
- `CAPTURE_SECURE_LAYERS` is a system/signature permission — process must run as root/system.
- More code to write and maintain vs. Route 2's flash-and-forget `screenrecord`.

## Context / alternatives

- **Route 1** — LSPosed "Disable FLAG_SECURE" module scoped to GMS/AA. No reflection,
  but can trip Play Integrity / break AA launch.
- **Route 2** — Smali Patcher patches SurfaceFlinger to ignore secure flag system-wide;
  then plain `screenrecord --display-id` works. Most reliable, but ROM-specific re-patch
  after each OTA, and weakens secure-layer protection device-wide.
- **Easier path entirely** — capture on the **head unit** side instead: the projected
  frames are already decoded there and are **not** secure (this project already does
  head-unit capture for AutoLink). Only do phone-side secure capture if the pixels are
  needed on the phone before streaming.