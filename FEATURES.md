# VF3 Smart - Features Checklist

## Hardware
- [x] ESP32 microcontroller
- [x] PCF8575 I2C I/O expander (16-pin digital outputs)
- [x] 4 analog sensors (brake, steering, battery voltage, accelerator)
- [x] 15 digital input sensors (doors, seats, seatbelts, lights, proximity, speed, gear, charging status)
- [x] 16 digital output controls via PCF8575 (lock, unlock, windows, signals, buzzer, mirrors, charger, accessories)

## Network
- [x] WiFi connectivity (Station mode)
- [x] UDP device discovery (port 8888)
- [x] NTP time synchronization
- [x] Onboarding mode (AP: VF3-SETUP / setup123)

## Web Interface
- [x] HTTP REST API (port 80)
- [x] WebSocket real-time updates (event-driven, state changes only)
- [x] Web dashboard with controls
- [x] API key authentication (header or query parameter)

## Control Features
- [x] Auto-close windows when locked (30 seconds)
- [x] Auto-off accessory power when locked
- [x] Auto-on accessory power when unlocked
- [x] Automatic accessory activation (dashcam, ODO screen, armrest with accessory power)
- [x] Automatic side mirror control (open when power on, close when power off)
- [x] Automatic charger unlock when charging stops
- [x] Remote lock/unlock
- [x] Window control (close/stop)
- [x] Manual charger unlock control
- [x] Turn signals (left/right/both off)
- [x] Buzzer control (on/off/beep with duration)

## Safety Features
- [x] Headlight reminder at night when driving (configurable on/off)
- [x] Real-time sensor monitoring (50ms control loop)
- [x] Change detection with analog noise filtering (±10 threshold)
- [x] Event-driven status broadcasting
- [ ] Collision detection alerts

## Configuration
- [x] Persistent WiFi credentials (NVS storage)
- [x] Persistent API key (minimum 8 characters)
- [x] Web-based reconfiguration
- [x] Automatic AP fallback on connection failure
- [x] OTA (Over-The-Air) firmware updates
- [x] Factory reset option (HTTP endpoint with API key auth)

## Monitoring
- [x] Real-time sensor data via HTTP/WebSocket
- [x] Control state monitoring (accessory power, dashcam, ODO screen, armrest)
- [x] Charging status monitoring (real-time charging detection)
- [x] Time tracking (boot time, current time, night mode)
- [x] Event-driven WebSocket broadcasts (state changes only)
- [x] Battery voltage monitoring (12V battery via voltage divider)

## Integration
- [x] Mobile app (iOS/Android)
- [x] Voice assistant (Google Assistant via Android Auto)

## Advanced Features
- [x] Charging status monitoring with auto-unlock
- [ ] Tire pressure monitoring
