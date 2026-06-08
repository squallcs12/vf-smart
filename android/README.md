# VF3-Smart Android Auto App

Android application for controlling the VF3-Smart car control device with Android Auto projection support.

The app talks to the car **entirely over Bluetooth Low Energy (BLE)** — the ESP32 no longer runs an HTTP webserver. The phone acts as the BLE peripheral (GATT server); the ESP32 connects as a client, pushes status, and receives commands.

## Features

- **Full-featured phone app** with all car controls
- **Android Auto projection** for read-only status monitoring while driving
- **Real-time car status over BLE** (delta updates pushed by the car)
- **Commands over BLE** (lock, windows, buzzer, mirrors, …)
- **No pairing/onboarding** — the app advertises and the car connects automatically

## Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/daotranbang/vfsmart/
│   │   │   ├── VF3Application.kt
│   │   │   ├── data/
│   │   │   │   ├── model/          # Data models (CarStatus, etc.)
│   │   │   │   ├── repository/     # VF3Repository — single source of truth
│   │   │   │   └── local/          # SecurePreferences (RTSP URL only)
│   │   │   ├── navigation/
│   │   │   │   └── VF3GattServer.kt # BLE GATT server (status in + commands out)
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
- **Transport**: BLE GATT (`VF3GattServer`) — no HTTP/WebSocket/UDP
- **Async**: Kotlin Coroutines + StateFlow
- **Storage**: EncryptedSharedPreferences (RTSP camera URL only)

## BLE Protocol

The phone advertises service `A1B2C3D4-E5F6-7890-ABCD-EF1234567890`. The ESP32 connects and uses four characteristics:

| Characteristic | UUID suffix | Direction | Properties |
|---|---|---|---|
| TPMS | `…893` | ESP32 → phone | WRITE |
| Speed limit | `…894` | ESP32 → phone | WRITE |
| Car status (delta) | `…895` | ESP32 → phone | WRITE |
| **Command** | `…896` | phone → ESP32 | **NOTIFY** (+ CCCD) |

Wire formats for the inbound status characteristics are documented in `VF3GattServer.kt`.

### Command channel

Commands are UTF-8 strings pushed as notifications on the Command characteristic. The ESP32 subscribes via the CCCD to receive them. Format is `verb[:args]` with comma-separated args:

```
lock                       unlock
acc:on | acc:off | acc:toggle
cameras:toggle
windows:close | windows:stop
window:down,left,on        (up|down, side, on|off)
buzzer:beep,500 | buzzer:on | buzzer:off
light-reminder:toggle
charger-unlock
mirrors:open | mirrors:close
odo:toggle  armrest:toggle  dashcam:toggle
tpms:reset  tpms:swap,fl,fr
```

Commands are **fire-and-forget**: a success result means the notification was dispatched to a subscribed client, not that the car confirmed the action. There is no read-back channel.

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
- **Real-time updates**: pushed by the car over BLE as state changes

### Android Auto

- **Status monitoring only**: read-only display due to Android Auto restrictions
- **Use phone for controls**: full control features remain on phone

## Security

- BLE link only; no IP/API key to manage
- **Secure Storage**: EncryptedSharedPreferences with AES256-GCM (RTSP URL)

## Troubleshooting

- **"Car not connected"**: the ESP32 hasn't connected to the phone's GATT server, or hasn't subscribed to the Command characteristic. Check Bluetooth is on and `BLUETOOTH_CONNECT`/`BLUETOOTH_ADVERTISE` are granted.
- **Status not updating**: confirm the car is connected (connection indicator) and advertising started (`VF3GattServer` logs, tag `VF3GattServer`).
- **Commands ignored**: check logcat for `notifyCommand(...) dispatched=` — `false` means no subscribed client.

## License

Part of the VF3-Smart project.
</content>
