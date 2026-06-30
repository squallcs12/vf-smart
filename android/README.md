# VF3-Smart Android Auto App

Android application for controlling the VF3-Smart car control device with Android Auto projection support.

The app talks to the car **over WiFi**: real-time status streams from the ESP32's `ws://<ip>/ws` WebSocket, and commands are HTTP POSTs to the ESP32 webserver. The device is found via UDP discovery and set up once (IP + API key).

## Features

- **Full-featured phone app** with all car controls
- **Android Auto projection** for read-only status monitoring while driving
- **Real-time car status over WebSocket** (delta updates pushed by the car)
- **Commands over HTTP** (lock, windows, buzzer, mirrors, …)
- **UDP discovery + one-time setup** (device IP + API key)

## Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/daotranbang/vfsmart/
│   │   │   ├── VF3Application.kt
│   │   │   ├── data/
│   │   │   │   ├── model/          # Data models (CarStatus, etc.)
│   │   │   │   ├── network/        # VF3ApiService, WebSocketManager, UdpDiscoveryService
│   │   │   │   ├── repository/     # VF3Repository — single source of truth
│   │   │   │   └── local/          # SecurePreferences (device IP/key + RTSP URL)
│   │   │   ├── navigation/        # Nav/GPS notification listener
│   │   │   ├── viewmodel/          # ViewModels (Status, Control, TPMS)
│   │   │   ├── di/                 # Hilt dependency injection
│   │   │   ├── ui/                 # Jetpack Compose UI
│   │   │   └── auto/               # Android Auto service
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
└── gradle/
    └── libs.versions.toml
```

## Architecture

- **Pattern**: MVVM + Repository + Hilt DI
- **UI**: Jetpack Compose with Material Design 3
- **Transport**: WiFi — HTTP commands (`VF3ApiService`) + `ws://<ip>/ws` status (`WebSocketManager`)
- **Async**: Kotlin Coroutines + StateFlow
- **Storage**: EncryptedSharedPreferences (device IP + API key, RTSP camera URL)

## Protocol (HTTP + WebSocket)

### Car status — `ws://<ip>/ws`

`WebSocketManager` connects to the ESP32's WebSocket and reads the **delta protocol**:

```
F|S:<s>|D:<d>|W:<w>|E:<e>|L:<l>|P:<p>|C:<c>|X:<x>   full frame (on connect + 60 s heartbeat)
U|S:<s>|L:<l>|...                                   delta (only changed groups)
```

Groups: `S` sensors, `D` doors, `W` windows, `E` seats, `L` lights, `P` proximity,
`C` controls, `X` misc (charging, lock, window-close, light-reminder, night). Frames
merge into a single `CarStatus` (`VF3Repository.carStatus`). The stream is read-only
(no auth). Live TPMS pressures and a speed limit are **not** carried by `/ws`.

### Commands — HTTP POST

Commands are typed `VF3Repository` methods backed by Retrofit (`VF3ApiService`),
POSTed to the ESP32 webserver with an `X-API-Key` header:

| Repository call | Endpoint |
|---|---|
| `lockCar()` / `unlockCar()` | `POST /car/lock` / `/car/unlock` |
| `toggle/setAccessoryPower()` | `POST /car/accessory-power` (`state=on\|off\|toggle`) |
| `toggle/setInsideCameras()` | `POST /car/inside-cameras` |
| `closeWindows()` / `stopWindows()` | `POST /car/windows/close` / `/stop` |
| `controlWindowDown/Up(side,on)` | `POST /car/windows/down` / `/up` (`side`,`state`) |
| `beepHorn(ms)` / `setBuzzer(on)` | `POST /car/buzzer` (`state`,`duration`) |
| `toggle/setLightReminder()` | `POST /car/light-reminder` |
| `unlockCharger()` | `POST /car/charger-unlock` |
| `open/closeSideMirrors()` | `POST /car/side-mirrors` (`action=open\|close`) |
| `tpmsReset()` / `tpmsSwap(a,b)` | `POST /tpms/calibrate` (`action=reset\|swap`) |

Discovery: the ESP32 UDP-broadcasts on port 8888; `UdpDiscoveryService` finds it
and `SetupScreen` saves the IP + API key.

## Build & Run

### Prerequisites

- Android Studio Ladybug | 2024.2.1 or later
- Android SDK 35, minimum SDK 23
- The two target devices: **Samsung Galaxy S20+** (primary) and the **armeabi-v7a head unit** (see `CLAUDE.md`)

### Build / Install / Test

```bash
cd /home/bang/bang/vf3-smart/android
./gradlew assembleDebug
./gradlew installDebug
./gradlew test
```

## Usage

### Phone App

- **Home Screen**: Real-time dashboard with car status and quick actions
- **Controls**: Lock, windows, accessories, buzzer, mirrors, charger, dashcam, TPMS
- **Real-time updates**: streamed by the car over the `/ws` WebSocket

### Android Auto

- **Status monitoring only**: read-only display due to Android Auto restrictions
- **Use phone for controls**: full control features remain on phone

## Security

- **API key** sent as `X-API-Key` on every command; cleartext HTTP is scoped to the local IoT device (`network_security_config.xml`)
- **Secure Storage**: EncryptedSharedPreferences with AES256-GCM (device IP + API key, RTSP URL)

## Troubleshooting

- **"Car not connected" / disconnected indicator**: the phone can't reach `ws://<ip>/ws`. Confirm the phone and ESP32 are on the same WiFi and the device IP is set (Settings → Setup). Check `WebSocketManager` logs (tag `WebSocketManager`).
- **Commands fail with "Unauthorized"**: the saved API key doesn't match the ESP32's `configured_api_key` (set it via the ESP32 `/configure` page).
- **Status not updating**: the WebSocket may be reconnecting — `WebSocketManager` retries with backoff; verify the ESP32 webserver is up (`GET /car/status`).

## License

Part of the VF3-Smart project.
</content>
