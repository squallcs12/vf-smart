# Rockchip RK312x — Device Info

This is the 9-inch head unit running Android that mirrors the VF3 Smart companion app.

## Connection

- **ADB over TCP/IP**: `192.168.0.102:5555` (DHCP — changes on reconnect; use MAC to find current IP)
- **MAC Address (Wi-Fi)**: `00:e0:4c:12:48:8b`

```bash
# Connect (replace IP with current DHCP address)
adb connect 192.168.0.102:5555

# Verify
adb -s 192.168.0.102:5555 shell getprop ro.product.model
```

## Hardware

| Property       | Value                              |
|----------------|------------------------------------|
| Manufacturer   | Rockchip                           |
| SoC / Board    | RK3126 / rk312x (rk30sdk)          |
| CPU            | ARM Cortex-A7 × 4 cores (ARMv7)   |
| CPU Features   | NEON, VFPv4, IDIV, VFPd32          |
| RAM            | ~1 GB (1 023 448 kB)               |
| ABI            | armeabi-v7a, armeabi                |

## Display

| Property         | Value          |
|------------------|----------------|
| Physical Size    | 1376 × 768 px  |
| Density          | 160 dpi (mdpi) |
| Logical Size     | 1376 × 768 dp  |

## Software

| Property         | Value                                                                  |
|------------------|------------------------------------------------------------------------|
| Android Version  | 6.0.1 (Marshmallow)                                                    |
| API Level        | 23                                                                     |
| Build            | rk312x-userdebug / MXC89K / user.benjamin.20171130                     |
| Build Fingerprint| `rockchip/rk312x/rk312x:6.0.1/MXC89K/…:userdebug/test-keys`          |
| Encryption       | Unencrypted                                                            |
| Timezone         | Asia/Bangkok (UTC+7)                                                   |

## Storage

| Partition  | Size   | Used   | Free   |
|------------|--------|--------|--------|
| /system    | 1.5 GB | 562 MB | 942 MB |
| /data      | 5.2 GB | 1.7 GB | 3.5 GB |
| /cache     | 122 MB | 68 KB  | 122 MB |

## USB / OTG Mode

The DT `compatible` actually reports **`rockchip,rk3128`** (RK3126/RK3128 are the same `rk312x` family). Kernel is **3.10.0** Rockchip BSP, driver `dwc_otg` / platform driver `usb20_otg`.

### Controllers

| Node            | Type            | Role         | Notes                              |
|-----------------|-----------------|--------------|------------------------------------|
| `10180000.usb`  | DWC2 / dwc_otg  | **dual-role (OTG)** | Only client-capable port; has UDC + gadget |
| `101c0000.usb`  | EHCI            | host-only    |                                    |
| `101e0000.usb`  | OHCI            | host-only    |                                    |

Only `10180000.usb` can be a USB **client/peripheral**. The other two are physically host-only.

### Default state (host)

- DT `usb@10180000/rockchip,usb-mode = 0` → configured as OTG/normal (not pinned to host).
- OTG **ID pin reads grounded** (`dmesg: [otg id chg] ... current id 0`), so the driver auto-selects **host** (`/sys/devices/10180000.usb/mode = 0x1`). Host port is powered but empty (`HPRT0=0x1000`, nothing enumerated).
- Android gadget stack is fully present; system already expects this as a device port:
  - `persist.sys.usb.config = mtp,adb`, `sys.usb.configfs = 0` (legacy `android_usb` gadget, not configfs)
  - Functions available: `mtp, ptp, rndis, mass_storage, acm, ffs, accessory, audio_source, midi`
  - Gadget interface: `/sys/class/android_usb/android0/`

### Switching to client/peripheral mode

Override knob (works regardless of the grounded ID pin):

```bash
# /sys/bus/platform/drivers/usb20_otg/force_usb_mode
#   0 = auto (ID-pin)   1 = force host   2 = force device/peripheral
echo 2 > /sys/bus/platform/drivers/usb20_otg/force_usb_mode   # → client
echo 0 > /sys/bus/platform/drivers/usb20_otg/force_usb_mode   # → back to auto/host
```

**Verified live:** writing `2` flipped `/sys/devices/10180000.usb/mode` from `0x1` (host) → `0x0` (device); writing `0` restored host. The write is **not persistent** across reboot — add it to a boot script / init.rc to keep it.

When plugged into a real USB host it enumerates per `sys.usb.config` (default `mtp,adb`). Change the gadget with e.g. `setprop sys.usb.config rndis,adb` or `setprop sys.usb.config mass_storage,adb` (set `persist.sys.usb.config` to persist).

**Caveats:** flipping to device mode drops whatever currently uses that OTG port as host; and the physical connector wired to `10180000.usb` must be reachable by the host you want to connect to.

### What's plugged into the OTG port in practice

After a normal boot with the phone connected, the OTG port (`10180000.usb`, bus `usb3`) enumerates the phone with the head unit as **host**:

```
hprt0 = 0x00001005   # PrtConnSts=1, PrtEna=1, high-speed device connected
/sys/bus/usb/devices/3-1 -> SAMSUNG_Android
```

`SAMSUNG_Android` is the **rooted Galaxy S20+** (the companion phone — the second device in this setup; the Rockchip is the head unit). It connects over the OTG port for phone-projection mirroring. This is why **host is the correct default**: forcing client mode (`force_usb_mode=2`) would drop the phone connection. (When no phone is attached the port reads `hprt0=0x1000` — powered but empty.)

## App Deployment Notes

- **Min SDK in app must be ≤ 23** (Android 6.0). The current `minSdk` should be verified in `app/build.gradle`.
- Only `armeabi-v7a` ABI is supported — no arm64. The app APK must include 32-bit native libs if any are used.
- Screen is **1376 × 768 at 160 dpi** → rendered as 1376 × 768 dp. Mirror mode layout is designed for this resolution (see `HomeScreen.kt` ODO cluster notes in `CLAUDE.md`).
- Head unit is rooted / `userdebug` build — `su` commands work (used for the screen-capture permission grant). The companion Galaxy S20+ phone is also rooted.
- Install APK:
  ```bash
  adb -s 192.168.0.102:5555 install -r app/build/outputs/apk/debug/app-debug.apk
  ```
- View logs:
  ```bash
  adb -s 192.168.0.102:5555 logcat -s VF3Smart
  ```

## AUX App (com.android.aux)

System app in `/system/app/AUX/AUX.apk`. Displays external CCD camera input in fullscreen.

### Key facts

- **Package**: `com.android.aux` — `AUXActivity` is the only activity
- **Target SDK**: 19 (KitKat) — Camera1 API, `SurfaceView`-based
- **Camera**: Uses `Camera.open(0)` → Camera HAL v1 → `/dev/video0`
- **No trigger needed**: launches manually from the launcher icon; reverse-gear wire only triggers it automatically

### Video input architecture

The AV-IN path on this device is **NOT** through the Camera/CIF pipeline. It goes through the LCDC (display controller) directly:

```
External CCD (CVBS/RCA)
    → CVBS decoder chip (I2C bus 2, addr 0x21, registered as "ov2659" in DTS)
    → LCDC hardware overlay (Win0 buffer)
    → Display
```

The CIF (`/dev/video0`) normally delivers zero frames. However when the CVBS decoder is properly initialized (see GPIO reset below), the CIF CAN deliver frames at ~15fps. The LCDC overlay is the primary display path; CIF is secondary.

### AUDIOSWB device

`/dev/AUDIOSWB` (char device 243:0) is the key to enabling AV-IN display mode.

The AUX app does this sequence on every launch:
1. `open("/dev/AUDIOSWB", O_RDWR)`
2. `ioctl(fd, 0x5404, 1)` — switches LCDC to decode CVBS into the display buffer
3. `Camera.open(0)` + `setPreviewDisplay(surfaceHolder)` + `startPreview()`

**Without the `ioctl 0x5404` call, no camera image appears** — the LCDC does not route CVBS to the surface.

### Camera parameters

| Parameter    | Value                                                               |
|--------------|---------------------------------------------------------------------|
| Preview size | 720 × 480 (NTSC — confirmed live; 720×576 PAL not in supported list)|
| All supported| 1280×720, 800×600, 720×480, 640×480, 352×288, 320×240, 176×144     |
| Format       | yuv420sp                                                            |
| Antibanding  | `off` fails / `60hz` fails / **`50hz` accepted** when chip is properly initialized |
| Supported AB | `auto`, `50hz`, `60hz`, `off` — only `50hz` actually works at driver level |

### Root cause of blue screen — GPIO not initialized

**The ov2659 DTS is missing `rockchip,powerdown` and `rockchip,hwreset` GPIO entries.**

Every camera close triggers:
```
rk_cam_io(1099): SENSOR_PWRSEQ_PWRDN failed
rk_cam_io(1088): SENSOR_PWRSEQ_HWRST failed
```

This leaves the chip in **powerdown + hardware reset** after every session:
- GPIO 107 (PWDN, GPIO3 bit 11) = HIGH → chip in powerdown
- GPIO 119 (HWRST, GPIO3 bit 23) = LOW → chip in hardware reset ← **the critical bug**

Without the GPIO reset, the chip never initializes properly:
- `antibanding=50hz` fails (I2C can't reach chip in reset)
- CIF delivers 0 fps
- LCDC shows blue (decoder in reset outputs blue)

### GPIO reset sequence (required before every camera open)

GPIO3 base address: `0x20088000`
- PWDN = bit 11, HWRST = bit 23

```
Step 1: PWDN=HIGH, HWRST=LOW  (ensure in powerdown+reset)  → wait 10ms
Step 2: PWDN=LOW              (wake chip, keep in reset)    → wait 10ms
Step 3: HWRST=HIGH            (release reset, chip boots)   → wait 50ms
```

After proper reset:
- `antibanding=50hz` is accepted by Camera HAL
- CIF delivers ~15fps
- LCDC shows live CVBS video (if signal present and decoder locks)

### setuid helper binary

`/system/xbin/sensor_reset` (chmod 4755, owner root) — does the GPIO reset sequence.
Source: `tools/sensor_reset.c`. Install:
```bash
adb push sensor_reset /data/local/tmp/
adb shell "su 0 cp /data/local/tmp/sensor_reset /system/xbin/ && su 0 chmod 4755 /system/xbin/sensor_reset"
```

Called from `AudioSwb.enable()` via `Runtime.getRuntime().exec("/system/xbin/sensor_reset")` before Camera.open().

### Signal detection

**`/proc/interrupts` IRQ 40 (`rk312x-camera`)** increments while camera is open (~26-28/sec).
This detects "camera is open" not "signal present".

**Framebuffer pixel sampling** (`/dev/graphics/fb0`): read 16 pixels across the screen.
- All pixels `rgb(<40, <50, >130)` → NO_SIGNAL (decoder outputs blue)
- Any pixel outside that range → SIGNAL (real video content)

Implemented in `tools/cvbs_detect.c` and `tools/signal_detect.sh`.

### Symptom reference

| Symptom | Cause |
|---------|-------|
| Blue screen | Decoder active, not locked to CVBS signal — OR chip in reset (HWRST=LOW) |
| Black screen in camera view | AUDIOSWB not fired (ioctl 0x5404 not called) |
| `antibanding(50hz) failed` | Chip in reset/powerdown, I2C not responding |
| `antibanding(50hz)` no error | Chip properly initialized, I2C working |
| CIF fps=0 | Chip in reset OR no CVBS signal lock |
| CIF fps=15 | Chip properly initialized, partial signal |
| `SENSOR_PWRSEQ_PWRDN failed` | Normal — DTS missing GPIO entry, driver can't control it |
| `SENSOR_PWRSEQ_HWRST failed` | Normal — same cause; but means chip went back to reset |
| `mANativeWindow is NULL` | Camera HAL cleanup log — not a bug |

### GPIO state (camera-related)

| GPIO | Label             | Boot state | Note |
|------|-------------------|-----------|------|
| 74   | camera power      | out lo    | Claimed by LCDC; LOW = active |
| 107  | camera powerdown  | out hi    | PWDN HIGH = chip powerdown; DTS missing so driver can't change it |
| 119  | camera reset      | out lo    | **HWRST LOW = chip in hardware reset** — this is the bug |
| 127  | camera powerdown  | out hi    | MUX unclaimed |

### AUDIOSWB channel values

`ioctl(fd, 0x5404, channel)` — confirmed via `libAUXctl.so` (`ch4052 ch = %d`):

| Channel | Effect |
|---------|--------|
| 0 | Disable LCDC overlay — normal Android UI visible |
| 1 | Enable CVBS overlay (LCDC win0 routed to display) |

Source: `Java_com_android_aux_AUXActivity_ch4052` in `/system/lib/libAUXctl.so`.

### Diagnostic tools built

| Binary | Source | Purpose |
|--------|--------|---------|
| `gpio_pwdn` | `tools/gpio_pwdn.c` | Read/set GPIO107 (CVBS PWDN) via /dev/mem mmap |
| `audioswb_probe` | `tools/audioswb_probe.c` | Send ioctl(0x5404, ch) to /dev/AUDIOSWB |

Push with: `adb push <bin> /data/local/tmp/ && adb shell chmod 755 /data/local/tmp/<bin>`
Run as root: `adb shell su 0 /data/local/tmp/<bin>`

### Our replacement (CameraPreviewScreen)

Lives in `ui/screens/CameraPreviewScreen.kt`. Key differences from system AUX app:

- Uses `TextureView` + `SurfaceTextureListener` (fixes `mANativeWindow is NULL` race)
- Calls `AudioSwb.enable()` (`ioctl 0x5404`) before `Camera.open()` — the missing piece
- Sets `antibanding = 50hz` (PAL hint for Vietnam)
- Signal watchdog via `onSurfaceTextureUpdated` — zero allocation (no `setPreviewCallback`)
- Shows "KHÔNG CÓ TÍN HIỆU" overlay when no frames arrive within 3 s

Native layer: `app/src/main/cpp/audioswb.cpp` → `libvfsmart_native.so` (armeabi-v7a)