# Android App — Claude Code Notes

This file covers the Android companion app for VF3 Smart (Kotlin/Compose).
ESP32 firmware and REST API docs are in the root `CLAUDE.md`.

## Google Assistant Integration (Android Auto)

The VF3 Smart system supports Google Assistant voice control through the Android Auto mobile app. The integration architecture is:

**Voice Command → Android App → ESP32 HTTP API → Car Action**

### Integration Architecture

```
┌─────────────────┐      ┌──────────────────┐      ┌──────────────┐
│  Google         │      │  Android Auto    │      │  ESP32       │
│  Assistant      │─────▶│  App             │─────▶│  VF3 Smart   │
│  (Voice)        │      │  (HTTP Client)   │      │  (REST API)  │
└─────────────────┘      └──────────────────┘      └──────────────┘
```

1. **User speaks command** to Google Assistant (in car or on phone)
2. **Android Auto app intercepts** the voice command
3. **App translates command** to appropriate HTTP API endpoint
4. **ESP32 executes action** and returns response
5. **App provides voice feedback** to user

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

Map Google Assistant voice commands to VF3 Smart API endpoints:

| Voice Command | API Endpoint | Action |
|--------------|--------------|--------|
| "Lock my car" | `POST /car/lock` | Lock the car |
| "Unlock my car" | `POST /car/unlock` | Unlock the car |
| "Close the windows" | `POST /car/windows/close` | Close all windows (30s) |
| "Stop the windows" | `POST /car/windows/stop` | Stop window operation |
| "Open the mirrors" | `POST /car/side-mirrors` (action=open) | Open side mirrors |
| "Close the mirrors" | `POST /car/side-mirrors` (action=close) | Close side mirrors |
| "Honk the horn" | `POST /car/buzzer` (state=beep) | Beep buzzer |
| "Turn on accessory power" | `POST /car/accessory-power` (state=on) | Enable accessories |
| "Turn off accessory power" | `POST /car/accessory-power` (state=off) | Disable accessories |
| "Unlock the charger" | `POST /car/charger-unlock` | Unlock charging port |
| "Open the frunk" | `POST /car/frunk/open` | Unlock and open front trunk |
| "Turn on dashcam" | `POST /car/dashcam` (state=on) | Enable dashcam |

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

        if (status.getBoolean("window_close_active")) {
            val remaining = status.getInt("window_close_remaining_ms") / 1000
            speak("Windows closing, $remaining seconds remaining")
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

**Target hardware**: 9-inch 1080p landscape screen (~960×540dp at xhdpi / ~245 PPI)

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