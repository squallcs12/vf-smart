# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an ESP32-based MCU control system for a VinFast VF3 electric car. The project uses PlatformIO with the Arduino framework to manage vehicle sensors, controls, and safety features.

## Build Commands

```bash
# Build the project
pio run

# Upload firmware to ESP32 device
pio run --target upload

# Upload filesystem (HTML files) to ESP32
pio run --target uploadfs

# Full deployment (firmware + filesystem)
pio run --target uploadfs && pio run --target upload

# Clean build files
pio run --target clean

# Monitor serial output (9600 baud)
pio device monitor

# Build and upload in one command
pio run --target upload && pio device monitor
```

**Note**: The filesystem must be uploaded separately using `uploadfs`. HTML files are stored in the `data/` directory and served via LittleFS.

## System Architecture

### Hardware Platform
- **MCU**: ESP32 (Espressif ESP32 Dev Module)
- **Framework**: Arduino (via espressif32 platform)
- **Serial Communication**: 9600 baud
- **Control Loop**: 50ms cycle time (20Hz)
- **WiFi**: Access Point mode (SSID: VF3_SMART)
- **Web Server**: AsyncWebServer on port 80

### Pin Assignment Architecture

The system is organized into three categories:

1. **Analog Inputs (ADC1 pins)**: Sensors requiring analog-to-digital conversion
   - Brake pedal, steering angle, battery voltage (via 4:1 voltage divider)

2. **Digital Inputs**: Binary state sensors and switches
   - Door sensors, seat occupancy, seatbelt sensors, light switches, proximity sensors

3. **Digital Outputs**: Control relays and actuators
   - Car lock/unlock, turn signals, window controls, accessory power, buzzer

### Control Logic Structure

The main loop follows a read-process-act pattern:
1. Read all analog sensor values
2. Read all digital sensor states
3. Execute control logic through dedicated handler functions
4. Delay 50ms before next cycle

### Function Organization

Control logic is modularized into separate handler functions:
- `handleWindowControl()`: Manages window auto-close when car is locked (30-second timer)
- `handleAccessoryPower()`: Controls accessory power based on lock/unlock state

When adding new features, follow this pattern by creating dedicated handler functions and calling them from the main loop.

## Code Location

All application code is in [src/main.cpp](src/main.cpp) - this is a single-file embedded project.

## GPIO Pin Constraints

When modifying pin assignments:
- Use ADC1 pins (32-39) for analog inputs only
- Avoid boot-sensitive pins (GPIO 0, 2, 5, 12, 15) for critical sensors
- GPIO pins are defined as `#define` constants at the top of main.cpp

## Adding New Features

1. Define GPIO pins as `#define` constants in the pin definitions section
2. Add corresponding global variables for state tracking
3. Initialize pin modes in `setup()`
4. Create a dedicated handler function (e.g., `handleNewFeature()`)
5. Call the handler function from `loop()`
6. Read sensor inputs before processing logic in the loop

## Timer-Based Logic

The codebase uses `millis()` for non-blocking timing (see `window_close_timer`). When implementing time-based features:
- Use `unsigned long` for timer variables
- Compare `millis() - timer_start` against duration constants
- Define duration constants in milliseconds as `#define` values
- Reset timer to 0 when feature is inactive

## State Management Pattern

State is managed through global variables that mirror physical sensor/actuator states. Variable naming convention:
- Input sensors: `vf3_<sensor_name>` (e.g., `vf3_door_fl`)
- Output controls: `vf3_<control_name>` (e.g., `vf3_car_lock`)
- Timer variables: `<feature>_timer` (e.g., `window_close_timer`)

## Battery Voltage Monitoring

The system monitors the 12V car battery voltage using an analog input with a voltage divider circuit.

### Hardware Configuration

- **Pin**: GPIO 38 (ADC1_CH2, input-only)
- **Voltage Divider**: 4:1 ratio (R1=30kΩ, R2=10kΩ)
  - Scales 0-16V battery range to 0-4V
  - ESP32 ADC safe range: 0-3.3V
  - Typical battery voltage: 11-14.5V
- **ADC Resolution**: 12-bit (0-4095)

### Voltage Calculation

```cpp
battery_voltage = (adc_value / 4095.0) * 3.3V * 4.0
```

**Example readings:**
- 12.0V battery → ADC ~3724 → 12.0V reading
- 13.5V battery (charging) → ADC ~4195 → 13.5V reading
- 11.5V battery (low) → ADC ~3569 → 11.5V reading

### Change Detection

The system uses a 0.1V threshold to filter noise:
- Only broadcasts WebSocket update if voltage changes by ±0.1V
- Prevents excessive updates from ADC noise
- Updates occur every 50ms control loop cycle when threshold exceeded

### Monitoring Battery Health

**Voltage Ranges:**
- **14.0-14.5V**: Charging (alternator active)
- **12.6-13.0V**: Fully charged (engine off)
- **12.0-12.5V**: Partially discharged
- **<12.0V**: Low battery, needs charging
- **<11.5V**: Critical, battery may be failing

**API Access:**
```bash
# Get current battery voltage
curl http://192.168.4.1/car/status | jq '.sensors.battery_voltage'

# WebSocket real-time monitoring
ws://192.168.4.1/ws
# Returns: {"sensors": {"battery_voltage": "12.65", ...}}
```

### Troubleshooting

**Voltage reads 0.0V:**
- Check voltage divider connections
- Verify GPIO 38 is not damaged
- Test with multimeter at voltage divider output (should be <3.3V)

**Voltage reads incorrectly:**
- Verify voltage divider resistor values (R1=30k, R2=10k)
- Check for loose connections
- Calibrate using known voltage and adjust divider ratio in code if needed

**Voltage fluctuates excessively:**
- Add capacitor (e.g., 10μF) across R2 to stabilize readings
- Check for electrical noise sources
- Increase change threshold from 0.1V to 0.2V in sensors.cpp

## Web Server API

The system includes an async web server that exposes car status via HTTP endpoints.

### Device Onboarding

On first boot, the device enters **onboarding mode** to allow configuration of WiFi credentials and API key.

#### Onboarding Process

1. **Initial Boot** - Device creates an open WiFi network:
   - **SSID**: `VF3-SETUP`
   - **Password**: `setup123`
   - **IP Address**: `192.168.4.1`

2. **Connect to Setup Network**
   - Connect your phone/computer to `VF3-SETUP`
   - Visit `http://192.168.4.1` in a web browser

3. **Configure Device**
   - Enter your desired WiFi SSID (network name)
   - Enter WiFi password
   - Create an API key (minimum 8 characters)
   - **Important**: Save the API key securely - you'll need it for all control operations

4. **Device Restart**
   - Configuration is saved to flash memory
   - Device automatically restarts
   - After restart, device creates its own AP with your configured credentials

#### Configuration Storage
- All configuration is stored in ESP32 NVS (Non-Volatile Storage)
- Survives power cycles and firmware updates
- To reconfigure, you must erase flash or modify code to reset configuration

### WiFi Configuration (After Onboarding)
- **Mode**: Access Point (AP)
- **SSID**: Configured during onboarding
- **Password**: Configured during onboarding
- **Default IP**: 192.168.4.1 (ESP32 AP default)

### UDP Device Discovery

When connected to WiFi in Station mode, the device broadcasts UDP messages for automatic network discovery.

**Discovery Configuration:**
- **Port**: 8888 (UDP broadcast)
- **Broadcast Address**: 255.255.255.255
- **Interval**: Every 10 seconds
- **Protocol**: JSON over UDP

**Broadcast Message Format:**
```json
{
  "device": "VF3-Smart",
  "type": "car-control",
  "ip": "192.168.1.100",
  "mac": "AA:BB:CC:DD:EE:FF",
  "hostname": "esp32-xxxxxx"
}
```

**Stopping Broadcast with Confirmation:**

Once you discover the device, send a confirmation message to stop further broadcasts and reduce network traffic:

**Confirmation Message Format (either format works):**
```json
{"command": "confirm"}
```
or
```json
{"action": "discovered"}
```

**Listening for Discovery (Python Example):**
```python
import socket
import json

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
sock.bind(('', 8888))

print("Listening for VF3-Smart devices...")
while True:
    data, addr = sock.recvfrom(1024)
    device_info = json.loads(data.decode())
    if device_info.get('device') == 'VF3-Smart':
        print(f"Found device at {device_info['ip']}")
        print(f"MAC: {device_info['mac']}")

        # Send confirmation to stop broadcasting
        confirm = json.dumps({"command": "confirm"})
        sock.sendto(confirm.encode(), (device_info['ip'], 8888))
        print("Confirmation sent - device will stop broadcasting")
        break
```

**Listening for Discovery (Node.js Example):**
```javascript
const dgram = require('dgram');
const server = dgram.createSocket('udp4');

server.on('message', (msg, rinfo) => {
  const deviceInfo = JSON.parse(msg.toString());
  if (deviceInfo.device === 'VF3-Smart') {
    console.log(`Found device at ${deviceInfo.ip}`);
    console.log(`MAC: ${deviceInfo.mac}`);

    // Send confirmation to stop broadcasting
    const confirm = Buffer.from(JSON.stringify({command: 'confirm'}));
    server.send(confirm, 8888, deviceInfo.ip, (err) => {
      if (!err) {
        console.log('Confirmation sent - device will stop broadcasting');
      }
    });
  }
});

server.bind(8888);
console.log('Listening for VF3-Smart devices...');
```

**Note**: UDP discovery only works when the device is in Station mode (connected to your WiFi network). It does not broadcast in AP (Access Point) mode.

### API Authentication
All control endpoints (POST requests) require API key authentication for security.

**API Key**: Configured during onboarding (user-defined)

**Authentication Methods:**
1. **HTTP Header** (Recommended):
   ```bash
   curl -X POST http://192.168.4.1/car/lock \
     -H "X-API-Key: YOUR_API_KEY"
   ```

2. **Query Parameter**:
   ```bash
   curl -X POST "http://192.168.4.1/car/lock?api_key=YOUR_API_KEY"
   ```

**Unauthorized Response** (401):
```json
{
  "success": false,
  "message": "Unauthorized - Invalid or missing API key"
}
```

**Note**: Status endpoints (GET /car/status) do not require authentication.

### API Endpoints

#### Status Endpoints

##### GET /car/status
Returns complete car status as JSON.

**Response Structure:**
```json
{
  "sensors": {
    "brake": 0,
    "steering_angle": 0,
    "battery_voltage": "12.65",
    "gear_drive": 0
  },
  "doors": {
    "front_left": 0,
    "front_right": 0,
    "trunk": 0,
    "locked": 0
  },
  "seats": {
    "front_left_occupied": 0,
    "front_right_occupied": 0,
    "front_left_seatbelt": 0,
    "front_right_seatbelt": 0
  },
  "lights": {
    "demi_light": 0,
    "normal_light": 0
  },
  "proximity": {
    "rear_left": 0,
    "rear_right": 0
  },
  "controls": {
    "brake_pressed": 0,
    "accessory_power": 1,
    "car_lock": 0,
    "car_unlock": 0
  },
  "window_close_active": false,
  "window_close_remaining_ms": 0,
  "light_reminder_enabled": true
}
```

##### GET /
Returns a simple HTML info page with links to all API endpoints.

#### Control Endpoints

##### POST /car/lock
Lock the car.

**Response:**
```json
{
  "success": true,
  "message": "Car locked",
  "car_lock": 1,
  "car_unlock": 0
}
```

##### POST /car/unlock
Unlock the car.

**Response:**
```json
{
  "success": true,
  "message": "Car unlocked",
  "car_lock": 0,
  "car_unlock": 1
}
```

##### POST /car/accessory-power
Control accessory power. Requires `state` parameter.

**Parameters:**
- `state` (required): `on`, `off`, or `toggle`

**Example Request:**
```bash
curl -X POST http://192.168.4.1/car/accessory-power \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "state=on"
```

**Response:**
```json
{
  "success": true,
  "message": "Accessory power updated",
  "accessory_power": 1
}
```

##### POST /car/windows/close
Start closing windows (30-second timer).

**Response:**
```json
{
  "success": true,
  "message": "Windows closing for 30 seconds",
  "window_close_active": true,
  "duration_ms": 30000
}
```

##### POST /car/windows/stop
Stop window operation immediately.

**Response:**
```json
{
  "success": true,
  "message": "Window operation stopped",
  "window_close_active": false
}
```

##### POST /car/buzzer
Control the buzzer/alarm.

**Parameters:**
- `state` (required): `on`, `off`, or `beep`
- `duration` (optional): Duration in milliseconds for `beep` mode

**Example Requests:**
```bash
# Turn on buzzer
curl -X POST http://192.168.4.1/car/buzzer \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "state=on"

# Beep for 500ms
curl -X POST http://192.168.4.1/car/buzzer \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "state=beep&duration=500"

# Turn off buzzer
curl -X POST http://192.168.4.1/car/buzzer \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "state=off"
```

**Response:**
```json
{
  "success": true,
  "message": "Buzzer control executed"
}
```

##### POST /car/turn-signal
Control turn signals.

**Parameters:**
- `side` (required): `left`, `right`, or `both`
- `state` (required): `on` or `off`

**Example Requests:**
```bash
# Turn on left turn signal
curl -X POST http://192.168.4.1/car/turn-signal \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "side=left&state=on"

# Turn off both turn signals
curl -X POST http://192.168.4.1/car/turn-signal \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "side=both&state=off"
```

**Response:**
```json
{
  "success": true,
  "message": "Turn signal updated",
  "side": "left",
  "state": "on"
}
```

##### POST /car/light-reminder
Control the headlight reminder system. The light reminder beeps every 30 seconds when:
- It's nighttime (6 PM - 6 AM)
- The gear is in Drive (D)
- Normal headlights are off
- Light reminder is enabled

**Parameters:**
- `state` (required): `on`, `off`, `enable`, `disable`, or `toggle`

**Example Requests:**
```bash
# Enable light reminder
curl -X POST http://192.168.4.1/car/light-reminder \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "state=on"

# Disable light reminder
curl -X POST http://192.168.4.1/car/light-reminder \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "state=off"

# Toggle light reminder
curl -X POST http://192.168.4.1/car/light-reminder \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "state=toggle"
```

**Response:**
```json
{
  "success": true,
  "message": "Light reminder enabled",
  "light_reminder_enabled": true
}
```

##### POST /car/charger-unlock
Manually unlock the charger port. The charger port also automatically unlocks when charging stops.

**Example Request:**
```bash
# Unlock charger port
curl -X POST http://192.168.4.1/car/charger-unlock \
  -H "X-API-Key: YOUR_API_KEY"
```

**Response:**
```json
{
  "success": true,
  "message": "Charger port unlocked"
}
```

##### POST /car/frunk/open
Unlock and open the front trunk (frunk).

**Safety:** This endpoint is disabled when the vehicle is in Drive (D) gear.

**Rate Limiting:** 2-second cooldown between operations to prevent relay damage.

**Example Request:**
```bash
# Open front trunk
curl -X POST http://192.168.4.1/car/frunk/open \
  -H "X-API-Key: YOUR_API_KEY"
```

**Response:**
```json
{
  "success": true,
  "message": "Front trunk unlocked"
}
```

**Error Responses:**
```json
// Vehicle in Drive
{
  "success": false,
  "message": "Cannot open front trunk while vehicle is in Drive"
}

// Too many requests
{
  "success": false,
  "message": "Too many requests - wait 2 seconds between operations"
}
```

##### POST /car/side-mirrors
Control side mirrors opening and closing.

**Parameters:**
- `action` (required): `open` or `close`

**Example Requests:**
```bash
# Open side mirrors
curl -X POST http://192.168.4.1/car/side-mirrors \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "action=open"

# Close side mirrors
curl -X POST http://192.168.4.1/car/side-mirrors \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "action=close"
```

**Response:**
```json
{
  "success": true,
  "message": "Side mirrors opening",
  "action": "open"
}
```

##### POST /car/dashcam
Control the dashcam.

**Parameters:**
- `state` (required): `on`, `off`, or `toggle`

**Example Requests:**
```bash
# Turn on dashcam
curl -X POST http://192.168.4.1/car/dashcam \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "state=on"

# Turn off dashcam
curl -X POST http://192.168.4.1/car/dashcam \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "state=off"

# Toggle dashcam
curl -X POST http://192.168.4.1/car/dashcam \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "state=toggle"
```

**Response:**
```json
{
  "success": true,
  "message": "Dashcam updated",
  "dashcam": "on"
}
```

#### OTA (Over-The-Air) Update Endpoints

##### GET /ota/status
Get the current OTA update status.

**Response:**
```json
{
  "ota_enabled": true,
  "in_progress": false,
  "progress_percent": 0,
  "error": ""
}
```

##### POST /ota/update
Upload firmware or filesystem image via HTTP. Requires API key authentication.

**Parameters:**
- Upload a `.bin` file (firmware) or `.littlefs.bin` / `.spiffs.bin` file (filesystem)

**Example Request (using curl):**
```bash
# Upload firmware
curl -X POST http://192.168.4.1/ota/update \
  -H "X-API-Key: YOUR_API_KEY" \
  -F "file=@firmware.bin"

# Upload filesystem
curl -X POST http://192.168.4.1/ota/update \
  -H "X-API-Key: YOUR_API_KEY" \
  -F "file=@littlefs.bin"
```

**Response (Success):**
```json
{
  "success": true,
  "message": "Firmware update successful - Rebooting in 3 seconds"
}
```

**Response (Error):**
```json
{
  "success": false,
  "message": "Firmware update failed",
  "error": "Error description"
}
```

**Note**: After a successful firmware update, the device will automatically reboot in 3 seconds.

##### ArduinoOTA Support
The system also supports ArduinoOTA for updates via Arduino IDE or PlatformIO:

- **Hostname**: `VF3-Smart`
- **Port**: 3232 (default)
- **Authentication**: None (use API key on HTTP endpoints)

**Using PlatformIO:**
```bash
# Upload firmware via OTA
pio run --target upload --upload-port VF3-Smart.local

# Or specify IP address
pio run --target upload --upload-port 192.168.4.1
```

**Using Arduino IDE:**
1. Go to Tools > Port
2. Select "VF3-Smart at 192.168.4.1" (Network Port)
3. Click Upload

#### Configuration Endpoints

##### POST /factory-reset
Reset the device to factory defaults and return to onboarding mode.

**⚠️ WARNING:** This endpoint permanently deletes all stored configuration (WiFi credentials and API key). The device will restart in onboarding mode (AP: VF3-SETUP / setup123).

**Authentication:** Requires API key (header or query parameter)

**Example Request:**
```bash
curl -X POST http://192.168.4.1/factory-reset \
  -H "X-API-Key: YOUR_API_KEY"
```

**Response:**
```json
{
  "success": true,
  "message": "Factory reset initiated - Device will restart in onboarding mode"
}
```

**What Happens:**
1. All NVS (Non-Volatile Storage) data is cleared:
   - WiFi SSID and password
   - API key
   - Configuration status
2. Device automatically restarts
3. Device enters onboarding mode:
   - Creates AP: `VF3-SETUP` (password: `setup123`)
   - IP: `192.168.4.1`
   - Web interface available for reconfiguration

**Use Cases:**
- Selling or transferring the device to someone else
- Changing WiFi network completely
- Resetting after forgotten API key
- Starting fresh after configuration errors

**Recovery:**
If you accidentally factory reset the device:
1. Connect to WiFi: `VF3-SETUP` (password: `setup123`)
2. Visit `http://192.168.4.1`
3. Reconfigure WiFi credentials and API key
4. Device will restart and connect to your WiFi

##### Hardware Factory Reset (Physical Button)

In addition to the HTTP endpoint, the device supports **hardware factory reset** via a physical button.

**Hardware Configuration:**
- **Pin:** GPIO 0 (BOOT button on ESP32 Dev Module)
- **Trigger:** Hold button for 10 seconds
- **Button State:** Active LOW (button pressed = pin LOW)
- **Pull-up:** Internal pull-up enabled (INPUT_PULLUP)

**How to Trigger:**
1. Press and hold the BOOT button on the ESP32 dev board
2. Keep holding for 10 seconds
3. Serial monitor will show countdown:
   ```
   Factory reset button pressed - hold for 10 seconds to reset
   Factory reset in 9 seconds... (release to cancel)
   Factory reset in 8 seconds... (release to cancel)
   ...
   Factory reset in 1 seconds... (release to cancel)

   ===========================================
   FACTORY RESET TRIGGERED VIA HARDWARE BUTTON
   ===========================================
   ```
4. Device will clear all configuration and restart in onboarding mode

**Cancel Factory Reset:**
- Release the button before 10 seconds elapse
- Serial monitor will show: `Factory reset cancelled (button released after X seconds)`

**Why Hardware Reset?**
- **No API key needed:** Works even if you forgot the API key
- **No network needed:** Works when WiFi is not configured or unreachable
- **Physical access required:** Prevents remote unauthorized resets
- **Fail-safe recovery:** Always available as last resort

**Use Cases:**
- Forgot API key and can't access HTTP endpoint
- WiFi not connecting and can't reconfigure
- Device in unknown state - need to start fresh
- Physical security: person with device access can reset it

**Wiring (if not using dev board BOOT button):**
```
┌─────────────┐
│   ESP32     │
│             │
│   GPIO 0 ───┼─────┬───── GND
│             │     │
│             │   [Button]
│             │     │
│   (3.3V) ───┼─────┘
└─────────────┘
  (Internal
   pull-up)
```

**Note:** GPIO 0 is the BOOT button on most ESP32 development boards. On custom PCB designs, connect a momentary push button between GPIO 0 and GND.

### Testing the API

```bash
# Connect to your configured WiFi network
# Set your configured API key
API_KEY="YOUR_CONFIGURED_API_KEY"

# Get car status (no auth required)
curl http://192.168.4.1/car/status

# Lock the car
curl -X POST http://192.168.4.1/car/lock \
  -H "X-API-Key: $API_KEY"

# Unlock the car
curl -X POST http://192.168.4.1/car/unlock \
  -H "X-API-Key: $API_KEY"

# Toggle accessory power
curl -X POST http://192.168.4.1/car/accessory-power \
  -H "X-API-Key: $API_KEY" \
  -d "state=toggle"

# Close windows
curl -X POST http://192.168.4.1/car/windows/close \
  -H "X-API-Key: $API_KEY"

# Stop windows
curl -X POST http://192.168.4.1/car/windows/stop \
  -H "X-API-Key: $API_KEY"

# Beep buzzer for 1 second
curl -X POST http://192.168.4.1/car/buzzer \
  -H "X-API-Key: $API_KEY" \
  -d "state=beep&duration=1000"

# Turn on left turn signal
curl -X POST http://192.168.4.1/car/turn-signal \
  -H "X-API-Key: $API_KEY" \
  -d "side=left&state=on"

# Disable light reminder (stops nighttime headlight reminders)
curl -X POST http://192.168.4.1/car/light-reminder \
  -H "X-API-Key: $API_KEY" \
  -d "state=off"

# Enable light reminder
curl -X POST http://192.168.4.1/car/light-reminder \
  -H "X-API-Key: $API_KEY" \
  -d "state=on"

# Unlock charger port
curl -X POST http://192.168.4.1/car/charger-unlock \
  -H "X-API-Key: $API_KEY"

# Open front trunk (frunk)
curl -X POST http://192.168.4.1/car/frunk/open \
  -H "X-API-Key: $API_KEY"

# Open side mirrors
curl -X POST http://192.168.4.1/car/side-mirrors \
  -H "X-API-Key: $API_KEY" \
  -d "action=open"

# Close side mirrors
curl -X POST http://192.168.4.1/car/side-mirrors \
  -H "X-API-Key: $API_KEY" \
  -d "action=close"

# Toggle dashcam
curl -X POST http://192.168.4.1/car/dashcam \
  -H "X-API-Key: $API_KEY" \
  -d "state=toggle"

# Check OTA update status
curl http://192.168.4.1/ota/status

# Upload firmware update
curl -X POST http://192.168.4.1/ota/update \
  -H "X-API-Key: $API_KEY" \
  -F "file=@.pio/build/esp32dev/firmware.bin"

# Factory reset (⚠️ Clears all configuration and restarts in onboarding mode)
curl -X POST http://192.168.4.1/factory-reset \
  -H "X-API-Key: $API_KEY"

# Alternative: Using query parameter for authentication
curl -X POST "http://192.168.4.1/car/lock?api_key=$API_KEY"

# View API documentation in browser
open http://192.168.4.1
```

### Dependencies
- **ESPAsyncWebServer-esphome**: Async web server for ESP32 (includes WebSocket support)
- **ArduinoJson**: JSON serialization/deserialization

## WebSocket Real-Time Communication

The system provides a WebSocket server for **real-time status monitoring only**.

**⚠️ Important: WebSocket is for monitoring, not for sending commands.**
- Use **HTTP API endpoints** (POST requests) to trigger commands and control the car
- Use **WebSocket** only to receive real-time status updates

### WebSocket Connection

- **Endpoint**: `ws://192.168.4.1/ws`
- **Protocol**: WebSocket (RFC 6455)
- **Auto-broadcast**: Status updates sent every 1 second to all connected clients
- **No Authentication Required**: WebSocket is read-only for status monitoring

### Connection Behavior

1. **On Connect**: Server immediately sends current car status as JSON
2. **Periodic Updates**: Status broadcast every 1 second to all clients
3. **Status Format**: Same JSON structure as `GET /car/status` endpoint

### Status Message Format

The WebSocket sends car status updates as JSON with the same structure as `GET /car/status`:

```json
{
  "sensors": {
    "brake": 0,
    "steering_angle": 0,
    "battery_voltage": "12.65",
    "gear_drive": 0
  },
  "doors": {
    "front_left": 0,
    "front_right": 0,
    "trunk": 0,
    "locked": 0
  },
  "seats": {
    "front_left_occupied": 0,
    "front_right_occupied": 0,
    "front_left_seatbelt": 0,
    "front_right_seatbelt": 0
  },
  "lights": {
    "demi_light": 0,
    "normal_light": 0
  },
  "proximity": {
    "rear_left": 0,
    "rear_right": 0
  },
  "controls": {
    "brake_pressed": 0,
    "accessory_power": 1,
    "car_lock": 0,
    "car_unlock": 0
  },
  "window_close_active": false,
  "window_close_remaining_ms": 0,
  "light_reminder_enabled": true,
  "time": {
    "synced": true,
    "current_time": "2026-02-04 10:30:15",
    "boot_time": "2026-02-04 08:00:00",
    "is_night": false
  }
}
```

### JavaScript WebSocket Client Example

```javascript
// Connect to WebSocket for real-time status monitoring
const ws = new WebSocket('ws://192.168.4.1/ws');

// Handle connection open
ws.onopen = function() {
  console.log('Connected to VF3 Smart - Monitoring status');
};

// Handle incoming status updates (received every 1 second)
ws.onmessage = function(event) {
  const status = JSON.parse(event.data);

  // Update your UI with real-time status
  console.log('Speed:', status.sensors.vehicle_speed);
  console.log('Car locked:', status.controls.car_lock === 1);
  console.log('Lights on:', status.lights.normal_light === 1);

  // Example: Display warnings
  if (status.time && status.time.is_night && status.lights.normal_light === 0) {
    console.warn('⚠️ Nighttime - headlights are off!');
  }

  // Example: Monitor window closing
  if (status.window_close_active) {
    console.log(`Windows closing... ${status.window_close_remaining_ms}ms remaining`);
  }
};

// Handle errors
ws.onerror = function(error) {
  console.error('WebSocket error:', error);
};

// Handle disconnection
ws.onclose = function() {
  console.log('Disconnected from VF3 Smart');
  // Optionally implement reconnection logic
  setTimeout(() => location.reload(), 3000);
};

// To send commands, use HTTP API instead:
async function lockCar() {
  const response = await fetch('http://192.168.4.1/car/lock', {
    method: 'POST',
    headers: {'X-API-Key': 'YOUR_API_KEY'}
  });
  const result = await response.json();
  console.log(result);
}
```

### Python WebSocket Client Example

```python
import asyncio
import websockets
import json
import requests

async def monitor_car():
    """Monitor car status in real-time via WebSocket"""
    uri = "ws://192.168.4.1/ws"

    async with websockets.connect(uri) as websocket:
        print("Connected to VF3 Smart - Monitoring status")

        # Receive and process status updates (sent every 1 second)
        while True:
            try:
                status_json = await websocket.recv()
                status = json.loads(status_json)

                # Process real-time status
                speed = status['sensors']['vehicle_speed']
                is_locked = status['controls']['car_lock'] == 1
                gear_drive = status['sensors']['gear_drive'] == 1

                print(f"Speed: {speed}, Locked: {is_locked}, Drive: {gear_drive}")

                # Example: Check for alerts
                if status['time']['is_night'] and status['lights']['normal_light'] == 0:
                    print("⚠️ Warning: Nighttime but headlights are off!")

            except websockets.exceptions.ConnectionClosed:
                print("Connection closed, reconnecting...")
                break

# To send commands, use HTTP API with requests:
def lock_car(api_key):
    """Send lock command via HTTP API"""
    response = requests.post(
        'http://192.168.4.1/car/lock',
        headers={'X-API-Key': api_key}
    )
    return response.json()

# Run the monitor
asyncio.run(monitor_car())
```

### WebSocket vs HTTP API

**Use WebSocket for:**
- ✅ Real-time status monitoring and dashboards
- ✅ Live updates for mobile apps and web interfaces
- ✅ Continuous data streaming (updated every 1 second)
- ✅ Displaying real-time sensor data

**Use HTTP API for:**
- ✅ Sending commands and controlling the car
- ✅ Triggering actions (lock, unlock, windows, buzzer, etc.)
- ✅ Authentication and authorization
- ✅ One-off operations and testing with curl/Postman
- ✅ Integration with automation systems

**Architecture:**
- **WebSocket** = Read-only, no authentication, real-time monitoring
- **HTTP API** = Command/control, requires API key authentication

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
