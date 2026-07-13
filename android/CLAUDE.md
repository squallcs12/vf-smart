# Android App — Claude Code Notes

This file covers the Android companion app for VF3 Smart (Kotlin/Compose).
ESP32 firmware and REST API docs are in the root `CLAUDE.md`.

## Target Device

This app runs on a **rooted Samsung Galaxy S20+** phone. The phone connects to the
car over USB and mirrors its own screen onto the car's display via AutoLink Pro
screen mirroring. Optimize layouts, screen sizes, and device-specific behavior for
the S20+.

## UI Language

**All UI strings are Vietnamese** (default locale). Use Android string resources:
- All visible text goes in `app/src/main/res/values/strings.xml` with Vietnamese values
- Kotlin/Compose files use `stringResource(R.string.xxx)` — never hardcode display text
- Non-Composable code (Services, ViewModels) uses `getString(R.string.xxx)` via Context
- `NavDirectionParser.label()` returns hardcoded Vietnamese strings (no Context available)

## Transport: WiFi (HTTP commands + WebSocket status)

**BLE was removed** — the ESP32 can't run BLE and WiFi at once, so it serves its
HTTP webserver again plus a `/ws` WebSocket for real-time car status (firmware
commit "Replace BLE server with WebSocket car-status stream"). The phone is now
a plain WiFi client:

- **Inbound** (car → phone): real-time status streams over `ws://<ip>/ws`,
  handled by `data/network/WebSocketManager.kt`. It speaks the **delta protocol**
  (`F|...` full frame on connect + every 60 s; `U|...` deltas on change) and
  merges it into a single `CarStatus` exposed as `WebSocketManager.statusFlow` /
  `VF3Repository.carStatus`. Connection state is `VF3Repository.connectionState`
  (`data/network/ConnectionState.{Connected,Disconnected}`).
- **Outbound** (phone → car): commands are **HTTP POSTs** to the ESP32 webserver
  via Retrofit `data/network/VF3ApiService.kt`. `AuthInterceptor` adds the
  `X-API-Key` header; `DynamicBaseUrlInterceptor` rewrites the host to the saved
  device IP. `VF3Repository` wraps each call into a typed `suspend` method
  returning `Result<…Response>`.
- **Discovery / setup**: the ESP32 UDP-broadcasts on port 8888;
  `UdpDiscoveryService` finds it, and `SetupScreen` / `SetupViewModel` save the
  IP + API key into `SecurePreferences` (which also keeps the RTSP camera URL).
  `VF3Repository.connectIfConfigured()` opens the WebSocket on app start and on
  power-connect (`VF3Application`).
- **Connecting** is reached from the Home screen Settings icon → `setup` route.

The car-status delta wire format lives in `WebSocketManager.kt`; the HTTP command
table lives in `VF3ApiService.kt` / `VF3Repository.kt`. The `GOOGLE_ASSISTANT.md`
voice flow ends at `VF3Repository`, which emits an HTTP command.

> **Note:** the `/ws` stream carries only car-status — not live TPMS pressures or
> a speed limit (those BLE characteristics are gone). `CarStatus.tpms` stays null;
> TPMS reset/swap still work over `POST /tpms/calibrate`. The odo/armrest/dashcam
> commands were dropped (no firmware HTTP route).

> The `VF3ApiClient` / OkHttp code samples in the Google Assistant sections below
> are illustrative — the real classes are `VF3ApiService` (Retrofit) and
> `WebSocketManager`, not a hand-rolled `VF3ApiClient`.

### Emulator (AVD) testability

A side benefit of the WiFi/WebSocket/HTTP transport: the car-communication path is
now testable in the Android Emulator. BLE never was — the AVD has no Bluetooth
radio (`BluetoothAdapter.getDefaultAdapter()` returns `null`), so it can't scan,
advertise, or open a GATT connection. HTTP + WebSocket are plain TCP sockets, which
the emulator handles fine.

To test against a real ESP32 from an AVD: the emulator can make **outbound**
connections to your LAN through the host's NAT, so point the app straight at the
ESP32's LAN IP — no `adb forward` / port bridging needed, and no need to invert the
client/server roles (the ESP32 stays the server).

The one piece that still doesn't work in an AVD is **UDP discovery** — emulator NAT
drops the port-8888 LAN broadcast, so `UdpDiscoveryService` won't find the device.
Enter the ESP32 IP manually in `SetupScreen` instead.

## AutoLink Auto-Connect

The app auto-connects to AutoLink Pro screen mirroring hands-free: when the user
gets in the car and plugs the phone in, the app launches AutoLink Pro (which is always
in USB mode and auto-mirrors on launch, with screen-capture consent pre-granted via
`appops` so the "Start now" dialog never appears) and then returns to its own screen, so
the phone projects onto the car display. This is handled by `autolink/AutoLinkService`
(foreground service: triggers, debounce, verification, side features) and
`autolink/AutoLinkAccessibilityService` (returns to `MainActivity` after launch + arms
the connection check).

**See [`app/src/main/kotlin/com/daotranbang/vfsmart/autolink/AUTOLINK_FLOW.md`](app/src/main/kotlin/com/daotranbang/vfsmart/autolink/AUTOLINK_FLOW.md)**
for the full flow: the four launch triggers, the return-to-app step, the
connection verification/retry loop, side features (driving tracker, light reminder),
exposed `StateFlow`s, and key constants. This flow assumes a **rooted phone** (the S20+).

## Google Assistant Integration (Android Auto)

The VF3 Smart system supports Google Assistant voice control through the Android Auto mobile app. The integration architecture is:

**Voice Command → Android App → HTTP command → Car Action**

### Integration Architecture

```
┌─────────────────┐      ┌──────────────────┐      ┌──────────────┐
│  Google         │      │  Android App     │      │  ESP32       │
│  Assistant      │─────▶│  VF3ApiService   │─────▶│  VF3 Smart   │
│  (Voice)        │      │  (HTTP client)   │      │  (webserver) │
└─────────────────┘      └──────────────────┘      └──────────────┘
```

1. **User speaks command** to Google Assistant (in car or on phone)
2. **Android app intercepts** the voice command (deep link → `AssistantCommandHandler`)
3. **App translates command** to a `VF3Repository` call
4. **`VF3ApiService` POSTs** the command to the ESP32 webserver (X-API-Key auth)
5. **App provides voice feedback** to user; status changes flow back over `/ws`

### Implementation Approaches

#### Option 1: Google App Actions (Recommended)

Use [Google App Actions](https://developers.google.com/assistant/app/overview) to define custom voice commands in your Android Auto app.

**Example actions.xml:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<actions>
  <!-- Lock car -->
  <action intentName="actions.intent.LOCK">
    <fulfillment urlTemplate="vf3smart://lock" />
  </action>

  <!-- Unlock car -->
  <action intentName="actions.intent.UNLOCK">
    <fulfillment urlTemplate="vf3smart://unlock" />
  </action>

  <!-- Control windows -->
  <action intentName="actions.intent.OPEN">
    <parameter name="object">
      <entity-set-reference entitySetId="WindowEntitySet"/>
    </parameter>
    <fulfillment urlTemplate="vf3smart://windows/open" />
  </action>

  <action intentName="actions.intent.CLOSE">
    <parameter name="object">
      <entity-set-reference entitySetId="WindowEntitySet"/>
    </parameter>
    <fulfillment urlTemplate="vf3smart://windows/close" />
  </action>

  <!-- Control lights -->
  <action intentName="com.vf3smart.TOGGLE_LIGHTS">
    <fulfillment urlTemplate="vf3smart://lights/toggle" />
  </action>
</actions>
```

**Android App Handler (Kotlin Example):**
```kotlin
class VF3SmartActivity : AppCompatActivity() {
    private val apiClient = VF3ApiClient("http://192.168.4.1", "YOUR_API_KEY")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val deepLink = intent.data?.toString() ?: return
                handleDeepLink(deepLink)
            }
        }
    }

    private fun handleDeepLink(deepLink: String) {
        lifecycleScope.launch {
            try {
                when {
                    deepLink.contains("lock") -> {
                        apiClient.lockCar()
                        speak("Car locked")
                    }
                    deepLink.contains("unlock") -> {
                        apiClient.unlockCar()
                        speak("Car unlocked")
                    }
                    deepLink.contains("windows/close") -> {
                        apiClient.closeWindows()
                        speak("Closing windows")
                    }
                    deepLink.contains("lights/toggle") -> {
                        apiClient.toggleLights()
                        speak("Toggling lights")
                    }
                }
            } catch (e: Exception) {
                speak("Error: ${e.message}")
            }
        }
    }

    private fun speak(text: String) {
        val tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }
}
```

**API Client Example:**
```kotlin
class VF3ApiClient(private val baseUrl: String, private val apiKey: String) {
    private val client = OkHttpClient()

    suspend fun lockCar(): Response = withContext(Dispatchers.IO) {
        post("/car/lock")
    }

    suspend fun unlockCar(): Response = withContext(Dispatchers.IO) {
        post("/car/unlock")
    }

    suspend fun closeWindows(): Response = withContext(Dispatchers.IO) {
        post("/car/windows/close")
    }

    suspend fun toggleLights(): Response = withContext(Dispatchers.IO) {
        post("/car/accessory-power", "state=toggle")
    }

    private fun post(endpoint: String, body: String = ""): Response {
        val requestBody = body.toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl$endpoint")
            .addHeader("X-API-Key", apiKey)
            .post(requestBody)
            .build()
        return client.newCall(request).execute()
    }
}
```

#### Option 2: Custom Assistant Commands

Handle custom voice commands directly in your Android Auto app using speech recognition.

**Example with Speech Recognizer:**
```kotlin
class VoiceCommandHandler(private val context: Context) {
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val apiClient = VF3ApiClient("http://192.168.4.1", "YOUR_API_KEY")

    init {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { processCommand(it) }
            }
            // ... other callbacks
        })
    }

    private fun processCommand(command: String) {
        lifecycleScope.launch {
            when {
                command.contains("lock", ignoreCase = true) -> apiClient.lockCar()
                command.contains("unlock", ignoreCase = true) -> apiClient.unlockCar()
                command.contains("close window", ignoreCase = true) -> apiClient.closeWindows()
                command.contains("open mirror", ignoreCase = true) -> apiClient.openMirrors()
                command.contains("honk", ignoreCase = true) -> apiClient.beepHorn()
                // Add more patterns
            }
        }
    }
}
```

### Supported Voice Commands

Map Google Assistant voice commands to VF3 Smart HTTP commands (sent via
`VF3Repository` → `VF3ApiService`):

| Voice Command | Repository call | Action |
|--------------|-------------|--------|
| "Lock my car" | `lock` | Lock the car |
| "Unlock my car" | `unlock` | Unlock the car |
| "Close/open the windows" | — | Windows are press-and-hold, one at a time, from the app UI only — no voice/timed close (the handler asks the user to do it in-app) |
| "Open the mirrors" | `mirrors:open` | Open side mirrors |
| "Close the mirrors" | `mirrors:close` | Close side mirrors |
| "Honk the horn" | `buzzer:beep,1000` | Beep buzzer |
| "Turn on accessory power" | `acc:on` | Enable accessories |
| "Turn off accessory power" | `acc:off` | Disable accessories |
| "Unlock the charger" | `charger-unlock` | Unlock charging port |
| "Turn on dashcam" | `dashcam:on` | Enable dashcam |

### Real-Time Status Feedback

Combine Google Assistant commands with WebSocket for instant feedback:

```kotlin
class VF3StatusMonitor(private val wsUrl: String) {
    private val webSocket: WebSocket by lazy {
        OkHttpClient().newWebSocket(
            Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleStatusUpdate(text)
                }
            }
        )
    }

    private fun handleStatusUpdate(jsonStatus: String) {
        val status = JSONObject(jsonStatus)

        // Provide voice feedback for important events
        if (status.getJSONObject("controls").getInt("car_lock") == 1) {
            speak("Car is now locked")
        }
    }
}
```

### Android Auto Considerations

**1. Network Discovery**

Use UDP discovery to automatically find the VF3 Smart device on the network:

```kotlin
class DeviceDiscovery {
    suspend fun discoverDevice(): String? = withContext(Dispatchers.IO) {
        val socket = DatagramSocket(8888)
        socket.broadcast = true
        socket.soTimeout = 10000 // 10 second timeout

        val buffer = ByteArray(1024)
        val packet = DatagramPacket(buffer, buffer.size)

        try {
            socket.receive(packet)
            val json = JSONObject(String(packet.data, 0, packet.length))
            if (json.getString("device") == "VF3-Smart") {
                val deviceIp = json.getString("ip")
                // Send confirmation to stop broadcasting
                val confirm = """{"command":"confirm"}""".toByteArray()
                val confirmPacket = DatagramPacket(
                    confirm, confirm.size,
                    InetAddress.getByName(deviceIp), 8888
                )
                socket.send(confirmPacket)
                return@withContext deviceIp
            }
        } catch (e: Exception) {
            Log.e("Discovery", "Failed to discover device", e)
        } finally {
            socket.close()
        }
        null
    }
}
```

**2. Car Mode Detection**

Only enable voice commands when Android Auto is active:

```kotlin
class CarModeDetector(context: Context) {
    fun isInCarMode(): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_CAR
    }
}
```

**3. Error Handling**

Provide clear voice feedback for errors:

```kotlin
suspend fun executeSafeCommand(command: suspend () -> Unit, description: String) {
    try {
        command()
        speak("$description completed successfully")
    } catch (e: Exception) {
        when (e) {
            is SocketTimeoutException -> speak("Cannot reach the car, check WiFi connection")
            is HttpException -> {
                if (e.code() == 401) {
                    speak("Authentication failed, check API key")
                } else {
                    speak("Command failed: ${e.message}")
                }
            }
            else -> speak("Error: ${e.message}")
        }
    }
}
```

### Best Practices

1. **Cache Device IP**: Store the discovered device IP to avoid repeated UDP discovery
2. **Background Monitoring**: Keep WebSocket connection alive for real-time status
3. **Command Confirmation**: Always provide voice feedback after command execution
4. **Offline Handling**: Gracefully handle when car WiFi is out of range
5. **Security**: Store API key securely using Android KeyStore
6. **Battery Optimization**: Use WorkManager for periodic status checks instead of continuous WebSocket
7. **User Permissions**: Request microphone and network permissions explicitly

### Testing Voice Commands

**Using ADB for Testing:**
```bash
# Simulate voice command deep link
adb shell am start -a android.intent.action.VIEW -d "vf3smart://lock"

# Test with Google Assistant
adb shell am start -a android.intent.action.ASSIST
```

**Manual Testing in App:**
```kotlin
// Debug menu for testing commands
class DebugCommandActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findViewById<Button>(R.id.btnLock).setOnClickListener {
            simulateCommand("lock")
        }

        findViewById<Button>(R.id.btnUnlock).setOnClickListener {
            simulateCommand("unlock")
        }
    }

    private fun simulateCommand(action: String) {
        val deepLink = Uri.parse("vf3smart://$action")
        val intent = Intent(Intent.ACTION_VIEW, deepLink)
        startActivity(intent)
    }
}
```

### Example Full Implementation

See the complete implementation example in the Android Auto app repository, which demonstrates:
- UDP device discovery with caching
- Google Assistant App Actions integration
- WebSocket real-time status monitoring
- Voice feedback with TextToSpeech
- Secure API key storage with KeyStore
- Car mode detection and lifecycle management


## Android App Architecture Notes

### HomeScreen Dual-Mode Design

The `HomeScreen` has two display modes toggled by a `SmallFloatingActionButton` (bottom-right corner):

**Mirror Mode (default)**
- Full-screen, no header/chrome, edge-to-edge
- Read-only 3×2 status grid — no interactive buttons (screen is not touchable when mirrored)
- System bars (status bar + nav bar) are hidden via `WindowInsetsController`; swipe to peek
- Tiny 8dp connection dot overlay in top-right corner
- `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` allows temporary peek without exiting mirror mode

**Full Mode**
- Standard scrollable layout with `TopAppBar` (Settings + Debug + connection indicator)
- 2-column status grid (fixed 120dp row height, safe inside `verticalScroll`)
- Quick actions card: Lock, Unlock, Beep, Close Windows
- "More Controls" button navigates to `ControlScreen`
- System bars restored on switch

**Toggle implementation** (`HomeScreen.kt`):
```kotlin
var mirrorMode by rememberSaveable { mutableStateOf(true) }

val view = LocalView.current
SideEffect {
    val controller = WindowCompat.getInsetsController((view.context as Activity).window, view)
    if (mirrorMode) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}
```

### Component Sizing

- `StatusCard`: uses `defaultMinSize(minHeight = 72.dp)` — expands to fill parent height when caller passes `fillMaxHeight()`
- `ControlButton`: uses `defaultMinSize(minHeight = 52.dp)` — same pattern

### StatusGrid Columns

`StatusGrid` takes a `columns: Int` parameter:
- `columns = 3` → mirror mode: rows use `Modifier.weight(1f)` to fill height (only valid outside scroll)
- `columns = 2` → full mode: rows use `Modifier.height(120.dp)` (safe inside `verticalScroll`)

### Navigation / Device Connection

- App always starts at `home` (never forced to setup screen)
- Device connection is optional — all screens show `"--"` placeholders when disconnected
- Setup is accessible via the Settings icon in the full-mode TopAppBar

### Mirror Mode: ODO Instrument Cluster Design

**Projection target** (the car display the phone mirrors onto): 9-inch 1080p landscape
screen (~960×540dp at xhdpi / ~245 PPI)

The mirror mode renders as a car instrument cluster (ODO style), not a standard Android UI:

**Layout**: 3×2 grid filling the full screen, separated by 1dp dividers (`Color 0xFF1C1C1C`).
Each cell: centered icon (40dp) → large bold value (28sp, 1sp letter-spacing) → small caps label (10sp, 2sp letter-spacing).

**ODO colour palette** (defined as file-level constants in `HomeScreen.kt`):
| Constant     | Hex         | Usage                                  |
|--------------|-------------|----------------------------------------|
| `OdoBg`      | `#0A0A0A`   | Screen background                      |
| `OdoDivider` | `#1C1C1C`   | Grid dividers                          |
| `OdoLabel`   | `#4A4A4A`   | Dim label text below each value        |
| `OdoInactive`| `#3A3A3A`   | No data / off / neutral state          |
| `OdoNormal`  | `#D0D0D0`   | Active but non-critical (lights on, drive mode) |
| `OdoGood`    | `#4CAF50`   | Positive state (locked, closed, charging) |
| `OdoWarning` | `#FFB300`   | Attention needed (unlocked, window open) |
| `OdoAlert`   | `#EF5350`   | Critical alert (doors open, window open+locked, lights off at night) |

**Per-item colour logic**:
- Car Lock: Good=locked, Warning=unlocked
- Windows: Good=closed, Warning=open, Alert=open while locked
- Charging: Good=charging, Normal=not charging
- Lights: Normal=on, Alert=off at night, Inactive=off during day
- Gear: Normal=drive, Inactive=park
- Doors: Good=all closed, Alert=any open

**No interactive elements** in mirror mode — the mirrored screen is not touchable.

### ODO Screen — Confirmed Items (Work in Progress)

**Layout**: Single row × 3 columns (full screen height per cell — ~540dp tall on 9-inch 1080p)

**Confirmed cells:**
1. **Battery Voltage** — horizontal gauge bar + numeric value, color zones by voltage range
2. **Clock** — large HH:MM from `time.current_time`, sun/moon icon from `time.is_night`
3. **Trip Timer** — elapsed HH:MM since app came to foreground, tracked via `LifecycleEventEffect(ON_START)` + 1-second tick in `HomeScreen`. No icon. Resets each time app is foregrounded.

**Still looking for 3 more features** — must be VF3-specific, not already on VF3 original screen.

### Screen Capture Permission Grant (root)

On every app start (`MainActivity.onCreate`), VF3 Smart silently grants `PROJECT_MEDIA` consent via root so no "Start now" dialog ever appears for the mirroring app:
```
su -c "appops set com.link.autolink.pro PROJECT_MEDIA allow"
```

### ODO Screen — Feature 4: Google Maps Navigation Direction

**Concept**: Display current Google Maps turn-by-turn instruction on the ODO screen.
Example: arrow icon + "300 m" + "TURN LEFT"

**Implementation approach**: `NotificationListenerService`
- Google Maps posts a persistent notification during active navigation
- Parse `Notification.EXTRA_TITLE` (maneuver, e.g. "Turn left") and `Notification.EXTRA_TEXT` (distance, e.g. "In 300 m")
- Filter by package: `com.google.android.apps.maps`
- No Google Maps SDK or API key required
- Permission required: `BIND_NOTIFICATION_LISTENER_SERVICE` (user must grant in Settings > Notification Access)
- Expose parsed data via `StateFlow` in a service/repository
- When no active navigation: show "NO NAVIGATION" in dim inactive color

**ODO cell layout**:
- Large directional arrow icon (derived from maneuver keyword: left/right/straight/roundabout)
- Distance text (e.g. "300 m")
- Maneuver label (e.g. "TURN LEFT") in caps + letter-spacing, ODO style