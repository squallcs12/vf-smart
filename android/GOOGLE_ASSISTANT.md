# Google Assistant Integration for VF3 Smart

This document describes the Google Assistant voice control integration for the VF3 Smart Android Auto app.

## Overview

The VF3 Smart app integrates with Google Assistant using **Google App Actions**, allowing users to control their car using natural voice commands.

**Architecture:**
```
Voice Command → Google Assistant → App Actions → VoiceAssistantActivity → AssistantCommandHandler → VF3Repository → HTTP command → ESP32
```

The final hop is an HTTP POST to the ESP32 webserver via `VF3ApiService` (status changes flow back over the `/ws` WebSocket).

## Supported Voice Commands

| Voice Command | Action | Command |
|--------------|--------|-------------|
| "Hey Google, lock my car" | Lock the car | `lock` |
| "Hey Google, unlock my car" | Unlock the car | `unlock` |
| "Hey Google, close the windows" | Close all windows (30s) | `windows:close` |
| "Hey Google, open the mirrors" | Open side mirrors | `mirrors:open` |
| "Hey Google, close the mirrors" | Close side mirrors | `mirrors:close` |
| "Hey Google, honk the horn" | Beep horn for 1 second | `buzzer:beep,1000` |
| "Hey Google, turn on accessory power" | Enable accessory power | `acc:on` |
| "Hey Google, turn off accessory power" | Disable accessory power | `acc:off` |
| "Hey Google, unlock the charger" | Unlock charging port | `charger-unlock` |

## Implementation Components

### 1. App Actions Configuration

**File:** `app/src/main/res/xml/actions.xml`

Defines the mapping between Google Assistant intents and app deep links:
- Built-in Intents (BII): `actions.intent.LOCK`, `actions.intent.UNLOCK`, `actions.intent.OPEN`, `actions.intent.CLOSE`
- Custom Intents: `com.vinfast.vf3smart.HONK`, `com.vinfast.vf3smart.ACCESSORY_POWER`

### 2. Entity Sets

**File:** `app/src/main/res/xml/entities.xml`

Defines parameters for voice commands:
- `VehicleObjectEntitySet`: windows, mirrors, side_mirrors
- `PowerStateEntitySet`: on, off, toggle

**File:** `app/src/main/res/values/assistant_entities.xml`

Provides entity names and synonyms for natural language understanding.

### 3. AssistantCommandHandler

**File:** `app/src/main/kotlin/com/vinfast/vf3smart/assistant/AssistantCommandHandler.kt`

Singleton service that:
- Processes deep link commands from Google Assistant
- Translates voice commands to API calls via VF3Repository
- Provides voice feedback using TextToSpeech
- Handles errors gracefully with user-friendly messages

### 4. VoiceAssistantActivity

**File:** `app/src/main/kotlin/com/vinfast/vf3smart/ui/VoiceAssistantActivity.kt`

Transparent activity that:
- Receives deep links from Google Assistant
- Parses URI parameters
- Delegates to AssistantCommandHandler
- Finishes immediately (invisible to user)

### 5. App Shortcuts

**File:** `app/src/main/res/xml/shortcuts.xml`

Provides suggested voice commands in Google Assistant:
- Lock/unlock car
- Close windows
- Open/close mirrors
- Honk horn
- Unlock charger

## Deep Link Format

The app handles deep links in this format:

```
vf3smart://command/<action>?param1=value1&param2=value2
```

**Examples:**
- `vf3smart://command/lock`
- `vf3smart://command/unlock`
- `vf3smart://command/close?object=windows`
- `vf3smart://command/open?object=mirrors`
- `vf3smart://command/honk`
- `vf3smart://command/accessory?state=on`
- `vf3smart://command/charger/unlock`

## Testing Voice Commands

### Using Google Assistant Test Tool

1. Open Android Studio
2. Go to **Tools → App Actions → App Actions Test Tool**
3. Select the VF3 Smart app
4. Choose an action to test
5. Fill in parameters
6. Click "Run App Action"

### Using ADB Commands

```bash
# Test lock command
adb shell am start -a android.intent.action.VIEW -d "vf3smart://command/lock"

# Test unlock command
adb shell am start -a android.intent.action.VIEW -d "vf3smart://command/unlock"

# Test close windows
adb shell am start -a android.intent.action.VIEW -d "vf3smart://command/close?object=windows"

# Test open mirrors
adb shell am start -a android.intent.action.VIEW -d "vf3smart://command/open?object=mirrors"

# Test honk
adb shell am start -a android.intent.action.VIEW -d "vf3smart://command/honk"

# Test accessory power
adb shell am start -a android.intent.action.VIEW -d "vf3smart://command/accessory?state=on"
```

### Manual Testing on Device

1. Connect your Android device
2. Install the app
3. Say "Hey Google" to activate Assistant
4. Try voice commands: "Lock my car", "Close the windows", etc.
5. Check logcat for debug output:
   ```bash
   adb logcat -s AssistantCommandHandler VoiceAssistantActivity
   ```

## Voice Feedback

The app provides voice feedback for all commands:

✅ **Success feedback:**
- "Car locked successfully"
- "Closing windows for 30 seconds"
- "Opening side mirrors"
- "Honking horn"

❌ **Error feedback:**
- "Failed to lock car: Car not connected"
- "Failed to unlock car: Car not connected" (WiFi/WebSocket down or wrong IP/API key)

## Requirements

### Dependencies

Add these dependencies to `app/build.gradle.kts`:

```kotlin
dependencies {
    // Google Assistant App Actions
    implementation("com.google.assistant.appactions:suggestions:1.1.0")

    // Hilt for dependency injection (already included)
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
}
```

### Permissions

Car control uses WiFi, so the relevant permissions in `AndroidManifest.xml` are:
- `android.permission.INTERNET` / `ACCESS_NETWORK_STATE` — HTTP commands + `/ws` status (and RTSP camera streaming)

### Device Requirements

- Android 6.0 (API 23) or higher
- Google app with Assistant enabled
- Device configured with user's Google account

## Google Assistant Setup

### For Users

1. Install VF3 Smart app and run setup (Settings → Setup) to discover the car and save its IP + API key
2. Make sure the phone is on the car's WiFi
3. Say "Hey Google" to activate Assistant
4. Try voice commands: "Lock my car", "Close the windows", etc.
5. Assistant will automatically discover available commands from the app

### For Developers

1. **Test on Device:** Voice commands work immediately during development
2. **App Actions Console (Optional):** For production release, submit app actions for review:
   - Go to [App Actions Console](https://console.actions.google.com/)
   - Create new project
   - Upload actions.xml
   - Test with Assistant preview

## How It Works

### Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│ User: "Hey Google, lock my car"                                  │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ Google Assistant                                                 │
│ - Recognizes speech                                             │
│ - Matches to actions.intent.LOCK                                │
│ - Generates deep link: vf3smart://command/lock                  │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ Android System                                                   │
│ - Finds app with matching intent-filter                        │
│ - Launches VoiceAssistantActivity                               │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ VoiceAssistantActivity                                           │
│ - Parses URI: vf3smart://command/lock                          │
│ - Extracts action: "lock"                                       │
│ - Calls commandHandler.handleCommand("lock")                    │
│ - Finishes immediately                                          │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ AssistantCommandHandler                                          │
│ - Calls repository.lockCar()                                    │
│ - Waits for result                                              │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ VF3Repository                                                    │
│ - Calls repository.lockCar()                                    │
│ - VF3ApiService.lockCar() → HTTP POST /car/lock                 │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ ESP32 VF3 Smart Device                                          │
│ - Webserver handles POST /car/lock                              │
│ - Triggers relay to lock car                                    │
│ (status change streams back over /ws)                           │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ AssistantCommandHandler                                          │
│ - Receives success result                                       │
│ - Speaks: "Car locked successfully"                            │
└─────────────────────────────────────────────────────────────────┘
```

## Error Handling

The integration handles errors gracefully:

### Connection Errors
- **Scenario:** car not reachable over WiFi (WebSocket down or wrong IP)
- **Feedback:** "Failed to <action>: Car not connected"

### Unknown Commands
- **Scenario:** Unsupported action
- **Feedback:** "Unknown command: <action>"

### Command Errors
- **Scenario:** HTTP command failed (network error or non-2xx response)
- **Feedback:** "Failed to <action>: <error message>"

## Android Auto Integration

Voice commands work seamlessly in Android Auto:

1. Connect phone to car via USB or wireless Android Auto
2. Use steering wheel voice button or say "Hey Google"
3. Give voice command: "Lock my car"
4. Hear confirmation: "Car locked successfully"
5. Android Auto displays VF3 Smart app status

## Best Practices

1. **User Experience:**
   - Keep voice feedback concise and clear
   - Provide error messages that users can act on
   - Test all commands in noisy car environments

2. **Performance:**
   - Handle commands asynchronously (already implemented)
   - Finish VoiceAssistantActivity immediately
   - Use TextToSpeech efficiently

3. **Security:**
   - Car control is over the local WiFi link only (API-key auth, private-IP device)
   - Validate all input parameters

4. **Testing:**
   - Test all voice commands on physical device
   - Test in Android Auto (DHU or real car)
   - Test error scenarios (no network, invalid API key)

## Troubleshooting

### Voice commands not recognized

**Check:**
1. Google app is up to date
2. Device has internet connection
3. User is signed in to Google account
4. VF3 Smart app is installed

**Debug:**
```bash
adb logcat -s AssistantCommandHandler
```

### Commands execute but no voice feedback

**Check:**
1. TextToSpeech is initialized (check logs)
2. Device volume is not muted
3. TTS language is set to English

**Debug:**
```bash
adb logcat -s TextToSpeech
```

### Deep links not opening app

**Check:**
1. `VoiceAssistantActivity` is registered in AndroidManifest
2. Intent filter matches `vf3smart://command` scheme
3. Activity is exported (`android:exported="true"`)

**Test:**
```bash
adb shell am start -a android.intent.action.VIEW -d "vf3smart://command/lock"
```

## Future Enhancements

- [ ] Add more voice commands (turn signals, lights, etc.)
- [ ] Support conversation mode ("OK Google, close windows, then lock car")
- [ ] Add voice status queries ("What's my car status?")
- [ ] Integrate with Google Assistant routines
- [ ] Add multi-language support
- [ ] Context-aware commands (only show relevant commands when near car)

## Resources

- [Google App Actions Documentation](https://developers.google.com/assistant/app/overview)
- [Built-in Intents Reference](https://developers.google.com/assistant/app/reference/built-in-intents)
- [App Actions Test Tool](https://developers.google.com/assistant/app/test-tool)
- [Android Deep Links](https://developer.android.com/training/app-links/deep-linking)
