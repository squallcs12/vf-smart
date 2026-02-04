# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an ESP32-based MCU control system for a VinFast VF3 electric car. The project uses PlatformIO with the Arduino framework to manage vehicle sensors, controls, and safety features.

## Build Commands

```bash
# Build the project
pio run

# Upload to ESP32 device
pio run --target upload

# Clean build files
pio run --target clean

# Monitor serial output (9600 baud)
pio device monitor

# Build and upload in one command
pio run --target upload && pio device monitor
```

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
   - Motor temperature, accelerator pedal, brake pedal, steering angle

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
    "motor_temp": 0,
    "accelerator": 0,
    "brake": 0,
    "steering_angle": 0,
    "vehicle_speed": 0
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
    "accelerator": 0,
    "brake": 0,
    "steering_angle": 0,
    "vehicle_speed": 0,
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
