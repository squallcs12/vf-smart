# VF3 Smart User Manual

**ESP32-Based Smart Control System for VinFast VF3**

Version 1.0 | Last Updated: April 2026

---

## Table of Contents

1. [Overview](#overview)
2. [Hardware Setup](#hardware-setup)
3. [Initial Configuration](#initial-configuration)
4. [Remote Control Features](#remote-control-features)
5. [Web Interface & API](#web-interface--api)
6. [Mobile App Integration](#mobile-app-integration)
7. [Voice Control (Google Assistant)](#voice-control-google-assistant)
8. [Safety Features](#safety-features)
9. [Troubleshooting](#troubleshooting)
10. [Technical Specifications](#technical-specifications)

---

## Overview

VF3 Smart is an advanced aftermarket control system that adds intelligent features to your VinFast VF3 electric vehicle. The system integrates with your car's existing remote control and provides additional functionality through WiFi connectivity, web APIs, and mobile applications.

### Key Features

- **Enhanced Remote Control**: Use your existing car remote with new gesture-based controls
- **WiFi Connectivity**: Control your car from anywhere via smartphone or web browser
- **Voice Control**: Integration with Google Assistant for hands-free operation
- **Real-Time Monitoring**: Track battery voltage, charging status, and vehicle sensors
- **Automatic Controls**: Smart window closing, mirror folding, and light reminders
- **Over-The-Air Updates**: Firmware updates without physical access

---

## Hardware Setup

### Components Required

1. **ESP32 Development Board**
2. **PCF8575 I/O Expander** (I2C address: 0x20)
3. **Voltage Divider Circuit** (4:1 ratio for battery monitoring)
4. **Relay Module** (for car control outputs)
5. **Wiring Harness** (connects to car's control systems)

### Pin Connections

#### ESP32 GPIO Pins (Inputs)

| Pin | Function | Description |
|-----|----------|-------------|
| GPIO 36 | Steering Angle | Analog steering wheel position sensor |
| GPIO 37 | Gear Drive | Detects when car is in Drive (D) |
| GPIO 38 | Battery Voltage | 12V battery monitoring (via voltage divider) |
| GPIO 39 | Brake Pedal | Analog brake pressure sensor |
| GPIO 18 | **Remote Lock Press** | Car remote lock button detection |
| GPIO 19 | **Remote Unlock Press** | Car remote unlock button detection |
| GPIO 34/35 | Window State | Left/right window position sensors |
| GPIO 4/16/17 | Door Sensors | Front left, front right, trunk |
| GPIO 13 | Charging Status | Detects when car is charging |
| GPIO 0 | Factory Reset | BOOT button (hold 10s to reset) |

#### PCF8575 I/O Expander (Outputs)

| Pin | Function | Pulse Duration |
|-----|----------|----------------|
| P0 | Car Lock | 1 second |
| P1 | Car Unlock | 1 second |
| P2 | Buzzer/Horn | Variable |
| P3/P4 | Window Up | Continuous |
| P5 | Door Lock | 1 second |
| P6 | Accessory Power | Toggle |
| P7 | **Front Trunk Unlock** | **8 seconds** |
| P8 | Inside Cameras | Toggle |
| P9 | **Side Mirrors Open** | 1 second |
| P10 | **Side Mirrors Close** | 1 second |
| P13 | Charger Unlock | 1 second |
| P14/P15 | Window Down | Continuous |

### Installation Steps

1. **Mount ESP32**: Install in a dry location inside the car (recommended: under dashboard)
2. **Connect Power**: Wire to car's 12V accessory power with inline fuse (5A recommended)
3. **Install Voltage Divider**: Connect to battery positive terminal for voltage monitoring
4. **Wire Sensors**: Connect all input sensors according to pin diagram
5. **Connect PCF8575**: Wire I2C bus (SDA=GPIO 21, SCL=GPIO 22) and all relay outputs
6. **Test Connections**: Verify all pins before closing panels

⚠️ **Warning**: Incorrect wiring can damage your vehicle's electrical system. Professional installation is recommended.

---

## Initial Configuration

### First Boot - Onboarding Mode

When you power on VF3 Smart for the first time, it enters **onboarding mode**:

1. **Connect to WiFi**:
   - SSID: `VF3-SETUP`
   - Password: `setup123`

2. **Open Web Browser**:
   - Navigate to: `http://192.168.4.1`
   - You'll see the configuration page

3. **Configure Settings**:
   - Enter your home WiFi SSID (network name)
   - Enter your home WiFi password
   - **Create an API Key** (minimum 8 characters)
     - ⚠️ **Save this key securely** - you'll need it for all control operations
     - Example: `MySecureKey2026!`

4. **Restart Device**:
   - Device automatically restarts
   - Connects to your WiFi network
   - Creates its own access point with your configured credentials

### Network Discovery

Once connected to your WiFi, the device broadcasts UDP discovery messages every 10 seconds on port 8888. Your mobile app or computer can automatically detect the device.

**Discovery Message Format**:
```json
{
  "device": "VF3-Smart",
  "type": "car-control",
  "ip": "192.168.1.100",
  "mac": "AA:BB:CC:DD:EE:FF",
  "hostname": "esp32-xxxxxx"
}
```

To stop broadcasting after discovery, send confirmation:
```json
{"command": "confirm"}
```

---

## Remote Control Features

VF3 Smart enhances your existing car remote with gesture-based controls. No additional remote required!

### 🔑 Single Press (Normal Operation)

**Lock Button** / **Unlock Button**
- Functions normally (locks/unlocks car as usual)
- System monitors button presses for gesture detection

### 🔑🔑 Double Press (Side Mirrors)

Control your side mirrors using double-press gestures:

#### Close Side Mirrors
1. Press **LOCK** button twice quickly (within 500ms)
2. Mirrors will close automatically
3. LED confirmation (if wired)

#### Open Side Mirrors
1. Press **UNLOCK** button twice quickly (within 500ms)
2. Mirrors will open automatically
3. LED confirmation (if wired)

**Tips**:
- Press buttons rapidly (less than 0.5 seconds apart)
- Works even when car is already locked/unlocked
- 2-second cooldown between operations prevents accidental triggers

### 🔑⏱️ Hold for 1 Second (Front Trunk)

Open your front trunk (frunk) without using the key:

1. Press and **hold UNLOCK button** for 1 second
2. Release after 1 second
3. Front trunk will unlock and open (8-second pulse)

**Safety Features**:
- ⚠️ Disabled when car is in Drive (D) gear
- 2-second cooldown between operations
- Does not trigger if car is moving

### Automatic Features

#### Smart Window Closing
When you lock your car 3 times in a row (within 2 seconds each):
- Both windows automatically close
- Operation runs for 30 seconds
- Press unlock to stop early

#### Accessory Power Control
- **Automatically turns ON** when car is unlocked
- **Automatically turns OFF** when car is locked
- Controls: dashcam, inside cameras, accessories

#### Charger Port Auto-Unlock
When charging stops (charging status goes from ON to OFF):
- Charger port automatically unlocks
- Ready for cable removal
- Manual unlock also available via API

#### Nighttime Light Reminder
Between 6 PM and 6 AM, if you're driving without headlights:
- Buzzer beeps every 30 seconds
- Only when car is in Drive (D) gear
- Only when normal headlights are OFF
- Can be disabled via API or mobile app

---

## Web Interface & API

### Accessing the Web Interface

1. **Connect to WiFi** (your configured home network or device AP)
2. **Find Device IP**:
   - Check your router's DHCP client list
   - Use UDP discovery (see Network Discovery section)
   - Typical IP: `192.168.1.100` or `192.168.4.1` (AP mode)
3. **Open Browser**: Navigate to `http://[DEVICE_IP]`

### API Endpoints

All control endpoints require API key authentication:
- **Header**: `X-API-Key: YOUR_API_KEY`
- **Query Parameter**: `?api_key=YOUR_API_KEY`

#### Status Monitoring

##### `GET /car/status`
Get complete car status (no authentication required).

**Response**:
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
  "windows": {
    "left_state": 0,
    "right_state": 0
  },
  "controls": {
    "remote_lock_press": 0,
    "remote_unlock_press": 0,
    "accessory_power": 1,
    "inside_cameras": 0
  },
  "charging_status": 0,
  "car_lock_state": "unlocked",
  "light_reminder_enabled": true,
  "time": {
    "synced": true,
    "current_time": "2026-04-02 14:30:00",
    "is_night": false
  }
}
```

#### Vehicle Control

##### `POST /car/lock`
Lock the car.

```bash
curl -X POST http://192.168.1.100/car/lock \
  -H "X-API-Key: YOUR_API_KEY"
```

##### `POST /car/unlock`
Unlock the car.

```bash
curl -X POST http://192.168.1.100/car/unlock \
  -H "X-API-Key: YOUR_API_KEY"
```

##### `POST /car/frunk/open`
Open the front trunk (frunk).

```bash
curl -X POST http://192.168.1.100/car/frunk/open \
  -H "X-API-Key: YOUR_API_KEY"
```

**Safety**: Disabled when vehicle is in Drive (D) gear.

##### `POST /car/side-mirrors`
Control side mirrors.

**Parameters**: `action=open` or `action=close`

```bash
# Open mirrors
curl -X POST http://192.168.1.100/car/side-mirrors \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "action=open"

# Close mirrors
curl -X POST http://192.168.1.100/car/side-mirrors \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "action=close"
```

##### `POST /car/windows/close`
Close windows (30-second operation).

```bash
curl -X POST http://192.168.1.100/car/windows/close \
  -H "X-API-Key: YOUR_API_KEY"
```

##### `POST /car/windows/stop`
Stop window operation immediately.

```bash
curl -X POST http://192.168.1.100/car/windows/stop \
  -H "X-API-Key: YOUR_API_KEY"
```

##### `POST /car/accessory-power`
Control accessory power (cameras, dashcam, etc).

**Parameters**: `state=on`, `off`, or `toggle`

```bash
curl -X POST http://192.168.1.100/car/accessory-power \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "state=toggle"
```

##### `POST /car/buzzer`
Control the buzzer/horn.

**Parameters**:
- `state=on`, `off`, or `beep`
- `duration=milliseconds` (for beep mode)

```bash
# Beep for 1 second
curl -X POST http://192.168.1.100/car/buzzer \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "state=beep&duration=1000"
```

##### `POST /car/light-reminder`
Enable/disable nighttime headlight reminder.

**Parameters**: `state=on`, `off`, `enable`, `disable`, or `toggle`

```bash
curl -X POST http://192.168.1.100/car/light-reminder \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "state=off"
```

##### `POST /car/charger-unlock`
Manually unlock the charger port.

```bash
curl -X POST http://192.168.1.100/car/charger-unlock \
  -H "X-API-Key: YOUR_API_KEY"
```

##### `POST /car/dashcam`
Control inside cameras.

**Parameters**: `state=on`, `off`, or `toggle`

```bash
curl -X POST http://192.168.1.100/car/dashcam \
  -H "X-API-Key: YOUR_API_KEY" \
  -d "state=on"
```

### WebSocket Real-Time Monitoring

Connect to WebSocket for live status updates (broadcast every 1 second).

**Endpoint**: `ws://[DEVICE_IP]/ws`

**JavaScript Example**:
```javascript
const ws = new WebSocket('ws://192.168.1.100/ws');

ws.onmessage = function(event) {
  const status = JSON.parse(event.data);
  console.log('Battery:', status.sensors.battery_voltage);
  console.log('Remote Lock Pressed:', status.controls.remote_lock_press);
};
```

**No authentication required** - WebSocket is read-only for monitoring.

### OTA Firmware Updates

##### `GET /ota/status`
Check OTA update status.

##### `POST /ota/update`
Upload firmware binary (requires API key).

```bash
curl -X POST http://192.168.1.100/ota/update \
  -H "X-API-Key: YOUR_API_KEY" \
  -F "file=@firmware.bin"
```

**Device reboots automatically** after successful update (3 seconds).

### Factory Reset

##### `POST /factory-reset`
⚠️ **Deletes all configuration** - returns device to onboarding mode.

```bash
curl -X POST http://192.168.1.100/factory-reset \
  -H "X-API-Key: YOUR_API_KEY"
```

**Hardware Factory Reset**: Press and hold BOOT button (GPIO 0) for 10 seconds.

---

## Mobile App Integration

### Android Auto App

VF3 Smart includes an Android Auto mobile application for:
- Real-time vehicle monitoring
- Remote control via Google Assistant
- Push notifications for vehicle events
- Automatic device discovery via UDP

**App Features**:
- Lock/unlock car
- Open front trunk
- Control mirrors
- Monitor battery voltage
- Enable/disable features
- Voice control integration

### Discovery & Connection

The app automatically discovers VF3 Smart on your network:
1. Open the app
2. Wait for automatic discovery (UDP broadcast)
3. App connects and displays vehicle status
4. Configure API key in app settings (one-time setup)

---

## Voice Control (Google Assistant)

### Supported Voice Commands

| Voice Command | Action |
|--------------|--------|
| "Lock my car" | Lock the vehicle |
| "Unlock my car" | Unlock the vehicle |
| "Open the frunk" | Open front trunk |
| "Close the windows" | Close all windows |
| "Open the mirrors" | Open side mirrors |
| "Close the mirrors" | Close side mirrors |
| "Honk the horn" | Beep buzzer |
| "Turn on accessory power" | Enable accessories |
| "Unlock the charger" | Unlock charging port |

### Setup Instructions

1. **Install Android Auto App**
2. **Configure Google App Actions**:
   - Grant microphone permission
   - Enable vehicle control actions
   - Link to VF3 Smart via WiFi
3. **Test Commands**:
   - Say "Hey Google, lock my car"
   - App sends command to VF3 Smart
   - Voice feedback confirms action

**Requirements**:
- Android 8.0 or higher
- Google Play Services
- VF3 Smart and phone on same WiFi (or internet access)

---

## Safety Features

### Automatic Safety Checks

1. **Drive Gear Protection**:
   - Front trunk opening disabled when in Drive (D)
   - Prevents accidental trunk opening while moving

2. **Rate Limiting**:
   - 2-second cooldown between front trunk operations
   - Prevents relay damage from rapid cycling
   - Protects against accidental multiple triggers

3. **Battery Voltage Monitoring**:
   - Real-time 12V battery voltage tracking
   - Alerts when voltage drops below 12.0V
   - Prevents deep discharge

4. **Charging Port Auto-Unlock**:
   - Automatically unlocks when charging stops
   - Prevents cable lock-in situations
   - Manual unlock always available

5. **Nighttime Light Reminder**:
   - Beeps every 30 seconds if driving without lights
   - Only active 6 PM - 6 AM
   - Can be disabled if not needed

### Hardware Safety

1. **Voltage Divider Circuit**:
   - 4:1 ratio protects ESP32 from high voltage
   - Safe range: 0-16V input → 0-4V output
   - ESP32 ADC safe range: 0-3.3V

2. **Fuse Protection**:
   - 5A inline fuse recommended
   - Protects car's electrical system
   - Prevents short circuit damage

3. **Factory Reset Options**:
   - **Software**: Via HTTP API (requires API key)
   - **Hardware**: Hold BOOT button for 10 seconds (no key required)
   - Use hardware reset if API key is lost

---

## Troubleshooting

### Device Won't Connect to WiFi

**Problem**: Device stays in AP mode (VF3-SETUP) after configuration.

**Solutions**:
1. Verify WiFi credentials are correct
2. Check WiFi network is 2.4GHz (ESP32 doesn't support 5GHz)
3. Move device closer to router
4. Check router MAC filtering/security settings
5. Factory reset and reconfigure

### Cannot Control Car Features

**Problem**: API returns "Unauthorized" error.

**Solutions**:
1. Verify API key is correct (check for typos)
2. Ensure API key is sent in header: `X-API-Key: YOUR_KEY`
3. Check API key was configured during onboarding
4. Factory reset if API key is lost

### Battery Voltage Reads 0.0V

**Problem**: Battery voltage always shows 0.0V in status.

**Solutions**:
1. Check voltage divider connections
2. Verify GPIO 38 is properly connected
3. Test with multimeter at voltage divider output (should be <3.3V)
4. Check resistor values (R1=30kΩ, R2=10kΩ)

### Remote Button Press Not Detected

**Problem**: Double-press or hold features don't work.

**Solutions**:
1. Verify GPIO 18 (lock) and GPIO 19 (unlock) connections
2. Check wiring to car's remote receiver
3. Monitor serial output for button press detection
4. Test with multimeter (should show HIGH when pressed)

### Front Trunk Won't Open

**Problem**: Remote hold or API command doesn't open trunk.

**Solutions**:
1. Check car is not in Drive (D) gear (safety feature)
2. Verify PCF8575 P7 connection to trunk relay
3. Check 8-second pulse reaches trunk unlock mechanism
4. Test relay manually with multimeter
5. Verify 2-second cooldown has elapsed since last operation

### Side Mirrors Don't Respond

**Problem**: Double-press doesn't open/close mirrors.

**Solutions**:
1. Verify button presses are within 500ms window
2. Check PCF8575 P9 (open) and P10 (close) connections
3. Monitor serial output for double-press detection
4. Test relays manually
5. Check 2-second cooldown has elapsed

### Time Shows Wrong or "synced: false"

**Problem**: Nighttime light reminder doesn't work correctly.

**Solutions**:
1. Verify device has internet connection (NTP sync requires internet)
2. Check firewall/router allows NTP traffic (UDP port 123)
3. Wait a few minutes after boot for NTP sync
4. Manually sync time via API (if available)

### OTA Update Fails

**Problem**: Firmware upload returns error or device doesn't reboot.

**Solutions**:
1. Verify firmware binary is correct (.bin file)
2. Check file size is reasonable (<2MB)
3. Ensure stable WiFi connection during upload
4. Don't interrupt upload process
5. Factory reset if device is bricked (requires USB/serial connection)

### Serial Monitor Shows "FATAL ERROR: Failed to initialize PCF8575!"

**Problem**: I/O expander not detected on I2C bus.

**Solutions**:
1. Verify PCF8575 I2C address is 0x20
2. Check SDA (GPIO 21) and SCL (GPIO 22) connections
3. Verify PCF8575 power supply (3.3V or 5V depending on module)
4. Test I2C bus with I2C scanner sketch
5. Check for loose connections or solder bridges

---

## Technical Specifications

### Hardware

- **Microcontroller**: ESP32 (Espressif ESP32 Dev Module)
- **Clock Speed**: 240 MHz (dual-core)
- **Flash Memory**: 4MB
- **RAM**: 520 KB SRAM
- **WiFi**: 802.11 b/g/n (2.4GHz only)
- **I/O Expander**: PCF8575 (16-bit, I2C)
- **Operating Voltage**: 3.3V logic, 5V USB power
- **Power Input**: 12V DC (via car accessory power)
- **Power Consumption**: ~150mA typical, ~300mA peak

### Software

- **Framework**: Arduino (via PlatformIO)
- **Serial Baud Rate**: 9600
- **Control Loop Cycle**: 50ms (20Hz)
- **WebSocket Broadcast**: 1 second interval
- **UDP Discovery**: Port 8888, 10-second interval
- **NTP Time Sync**: Automatic on boot (requires internet)

### Timing Parameters

| Feature | Duration/Window |
|---------|----------------|
| Front trunk pulse | 8 seconds |
| Mirror control pulse | 1 second |
| Lock/unlock pulse | 1 second |
| Window close operation | 30 seconds |
| Double-press window | 500ms |
| Remote hold threshold | 1 second |
| Factory reset hold | 10 seconds |
| Rate limit cooldown | 2 seconds |
| Light reminder interval | 30 seconds |

### Network

- **WiFi Modes**: Station (STA) and Access Point (AP)
- **Default AP SSID**: VF3-SETUP
- **Default AP Password**: setup123
- **Default AP IP**: 192.168.4.1
- **Web Server Port**: 80 (HTTP)
- **WebSocket Port**: 80 (same as HTTP)
- **UDP Discovery Port**: 8888
- **OTA Port**: 3232 (ArduinoOTA)

### Battery Monitoring

- **Input Range**: 0-16V (car battery)
- **Voltage Divider Ratio**: 4:1
- **ADC Resolution**: 12-bit (0-4095)
- **Update Threshold**: ±0.1V
- **Typical Range**: 11.5V - 14.5V
- **Low Battery Alert**: <12.0V

---

## Warranty & Support

### Warranty Information

VF3 Smart is provided as an open-source project. Hardware modifications to your vehicle may void manufacturer warranties. Install at your own risk.

### Support Resources

- **GitHub Repository**: https://github.com/yourusername/vf3-smart
- **Issue Tracker**: Report bugs and feature requests on GitHub
- **Documentation**: See CLAUDE.md for developer documentation
- **Community Forum**: [Link to forum if available]

### Safety Disclaimer

⚠️ **Important Safety Notice**:
- Professional installation is strongly recommended
- Improper installation can damage vehicle electrical systems
- Always disconnect car battery before working on electrical systems
- Test all features in a safe environment before regular use
- Do not rely solely on automated systems - always use visual confirmation
- The developer assumes no liability for damage or injury resulting from use

---

## Appendix

### Firmware Update via PlatformIO

For developers who want to update firmware via USB:

```bash
# Build and upload firmware
pio run --target upload

# Upload filesystem (HTML files)
pio run --target uploadfs

# Monitor serial output
pio device monitor
```

### GPIO Pin Reference

**Input-Only Pins** (no pull-up/down): 34, 35, 36, 37, 38, 39
**Boot-Sensitive Pins** (avoid for critical sensors): 0, 2, 5, 12, 15
**I2C Default Pins**: SDA=21, SCL=22
**UART Pins** (used for programming): TX=1, RX=3

### PCF8575 I2C Address Selection

Default address: 0x20

Change address by wiring A0, A1, A2 pins:
- A0=GND, A1=GND, A2=GND → 0x20
- A0=VCC, A1=GND, A2=GND → 0x21
- A0=GND, A1=VCC, A2=GND → 0x22
- (and so on...)

### Battery Voltage Formula

```
V_battery = (ADC_value / 4095) × 3.3V × 4.0
```

Example:
- 12.0V battery → ADC ~3724 → 12.0V reading
- 13.5V battery (charging) → ADC ~4195 → 13.5V reading

---

**End of User Manual**

For technical development details, see [CLAUDE.md](CLAUDE.md)
For source code, see [GitHub Repository](https://github.com/yourusername/vf3-smart)

Last Updated: April 2, 2026
Version: 1.0
