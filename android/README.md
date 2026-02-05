# VF3-Smart Android Auto App

Android application for controlling the VF3-Smart ESP32 car control device with Android Auto projection support.

## Features

- **Full-featured phone app** with all car controls
- **Android Auto projection** for read-only status monitoring while driving
- **Real-time WebSocket updates** (1-second refresh)
- **UDP device discovery** for easy setup
- **Secure API key storage** using EncryptedSharedPreferences

## Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/vinfast/vf3smart/
│   │   │   ├── VF3Application.kt
│   │   │   ├── data/
│   │   │   │   ├── model/          # Data models (CarStatus, etc.)
│   │   │   │   ├── network/        # Retrofit, WebSocket, UDP discovery
│   │   │   │   ├── repository/     # Repository pattern
│   │   │   │   └── local/          # Secure preferences
│   │   │   ├── viewmodel/          # ViewModels (Setup, Status, Control)
│   │   │   ├── di/                 # Hilt dependency injection
│   │   │   ├── ui/                 # Jetpack Compose UI
│   │   │   │   ├── screens/        # Setup, Home, Control screens
│   │   │   │   ├── components/     # Reusable UI components
│   │   │   │   └── theme/          # Material Design 3 theme
│   │   │   └── auto/               # Android Auto service
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
└── gradle/
    └── libs.versions.toml
```

## Architecture

- **Pattern**: MVVM + Repository + Hilt DI
- **UI**: Jetpack Compose with Material Design 3
- **Networking**: Retrofit + OkHttp + WebSocket
- **Async**: Kotlin Coroutines + StateFlow
- **Security**: EncryptedSharedPreferences

## Build & Run

### Prerequisites

- Android Studio Ladybug | 2024.2.1 or later
- Android SDK 35
- Minimum SDK: 28 (Android 9.0)
- Gradle 8.11

### Build

```bash
cd /home/bang/bang/vf3-smart/android
./gradlew build
```

### Install on Device

```bash
./gradlew installDebug
```

### Run Tests

```bash
./gradlew test
```

## Setup Instructions

1. **Connect to WiFi**: Ensure your phone is on the same network as the ESP32 device
2. **Launch app**: Open VF3 Smart app
3. **Auto-discover**: Tap "Discover Device" (waits up to 30 seconds for UDP broadcast)
   - Alternative: Enter device IP manually (e.g., `192.168.4.1`)
4. **Enter API key**: Input your API key configured during ESP32 onboarding
5. **Test connection**: App will test connection before saving
6. **Success**: Navigate to home screen with real-time car status

## Usage

### Phone App

- **Home Screen**: Real-time dashboard with car status and quick actions
- **Controls**: Detailed controls for lock, windows, accessories, buzzer, charger
- **Real-time Updates**: WebSocket connection provides 1-second status updates

### Android Auto

- **Status monitoring only**: Read-only display due to Android Auto restrictions
- **Safe while driving**: Large icons, minimal text, no complex controls
- **Use phone for controls**: Full control features remain on phone

## API Endpoints Used

All endpoints defined in `VF3ApiService.kt`:

- `GET /car/status` - Get complete car status (no auth)
- `POST /car/lock` - Lock the car
- `POST /car/unlock` - Unlock the car
- `POST /car/windows/close` - Start auto-close (30s timer)
- `POST /car/windows/stop` - Stop window operation
- `POST /car/windows/down` - Roll down windows
- `POST /car/buzzer` - Control horn/buzzer
- `POST /car/accessory-power` - Toggle accessory power
- `POST /car/inside-cameras` - Toggle inside cameras
- `POST /car/light-reminder` - Toggle light reminder
- `POST /car/charger-unlock` - Unlock charger port

## WebSocket

- **Endpoint**: `ws://<device-ip>/ws`
- **Update frequency**: Every 1 second
- **No authentication**: Read-only real-time monitoring
- **Auto-reconnect**: 5-second delay with exponential backoff

## UDP Discovery

- **Port**: 8888
- **Protocol**: UDP broadcast
- **Message format**: `{"device":"VF3-Smart","type":"car-control","ip":"...","mac":"...","hostname":"..."}`
- **Confirmation**: Sends `{"command":"confirm"}` to stop broadcasting

## Security

- **API Key**: Required for all control operations
- **Secure Storage**: EncryptedSharedPreferences with AES256-GCM
- **No plaintext storage**: Device IP and API key are encrypted at rest
- **Network**: HTTP only (local network, no internet exposure)

## TODO: Remaining UI Implementation

The core architecture is complete. Remaining tasks:

1. **UI Screens** (Jetpack Compose):
   - `SetupScreen.kt` - Device discovery and configuration UI
   - `HomeScreen.kt` - Main dashboard with status cards
   - `ControlScreen.kt` - Detailed control UI with categories

2. **UI Components**:
   - `StatusCard.kt` - Reusable status display card
   - `ControlButton.kt` - Large touch-friendly control buttons
   - `LoadingIndicator.kt` - Loading state UI
   - `ErrorDialog.kt` - Error handling UI

3. **Android Auto**:
   - `VF3AutoService.kt` - CarAppService implementation
   - `VF3AutoSession.kt` - Session management
   - `StatusScreen.kt` - Read-only status display with GridTemplate

## Testing

Connect to ESP32 device and verify:

- [ ] UDP device discovery (10-30 seconds)
- [ ] Manual IP entry fallback
- [ ] API key authentication
- [ ] WebSocket connection and real-time updates
- [ ] Lock/unlock car
- [ ] Close windows (30s timer)
- [ ] Stop windows
- [ ] Beep horn
- [ ] Toggle accessory power
- [ ] Android Auto projection (status only)
- [ ] Error handling (wrong API key, network failure, timeout)

## Troubleshooting

- **Device not found**: Ensure phone and ESP32 are on same WiFi network
- **401 Unauthorized**: Check API key is correct
- **Connection timeout**: Verify ESP32 is powered on and connected to WiFi
- **WebSocket disconnects**: Check network stability, app will auto-reconnect

## License

Part of the VF3-Smart project.
