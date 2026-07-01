# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an ESP32-based MCU control system for a VinFast VF3 electric car. The project uses PlatformIO with the Arduino framework to manage vehicle sensors, controls, and safety features.

The ESP32 talks to the companion Android app **over WiFi**: it runs an HTTP webserver for commands and serves a `/ws` WebSocket that streams real-time car status (BLE was removed because it can't run alongside WiFi). The app is a plain WiFi client that finds the ESP32 via UDP discovery. See [Car ↔ App Communication](#car--app-communication-websocket-status--http-commands) and the Android side in `android/app/.../data/network/`.

## Build Commands

```bash
# Build the project
pio run

# Upload firmware to ESP32 device
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
- **Connectivity**: WiFi — HTTP webserver + `/ws` WebSocket (car status stream)

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
4. Pump the `/ws` WebSocket (push full/delta status); the webserver applies any received HTTP commands asynchronously
5. Delay 50ms before next cycle

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
7. If the feature exposes state or a command, add it to the WebSocket status / HTTP command protocol below

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
- Only sends a status delta over `/ws` if voltage changes by ±0.1V
- Prevents excessive updates from ADC noise
- Evaluated every 50ms control loop cycle

### Monitoring Battery Health

**Voltage Ranges:**
- **14.0-14.5V**: Charging (alternator active)
- **12.6-13.0V**: Fully charged (engine off)
- **12.0-12.5V**: Partially discharged
- **<12.0V**: Low battery, needs charging
- **<11.5V**: Critical, battery may be failing

The battery voltage is reported to the app in the `S` group of the car-status
frame (`brake,steering,voltage,gear`) — see [Status uplink](#status-uplink--wsipws-esp32--phone).

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

## Car ↔ App Communication (WebSocket status + HTTP commands)

BLE and WiFi cannot run concurrently on the ESP32, so **BLE was dropped**. The
ESP32 runs an `ESPAsyncWebServer` again: real-time status streams from a `/ws`
WebSocket, and the app sends commands as HTTP POSTs. The ESP32 is the **server**;
the Android app is a plain WiFi **client**.

This contract is the source of truth shared with the Android app
(`android/app/.../data/network/WebSocketManager.kt` for status,
`.../data/network/VF3ApiService.kt` for commands). Keep both sides in sync.

- **Transport:** WiFi (HTTP :80 + `ws://<ip>/ws`).
- **Discovery:** the ESP32 UDP-broadcasts on port 8888; the app confirms to stop
  the broadcast (`src/discovery.cpp`).
- **Auth:** all command POSTs require the `X-API-Key` header (or `?api_key=`)
  matching `configured_api_key`; `GET /car/status` and `/ws` are unauthenticated.

### Status uplink — `ws://<ip>/ws` (ESP32 → phone)

The WebSocket carries the **car-status delta protocol** (see `src/websocket.cpp`).
On client connect the ESP32 sends a **full** frame, then **deltas** on change, plus
a full-frame heartbeat every 60 s. All payloads are UTF-8 strings.

```
Full  (on connect, 60 s heartbeat):
  "F|S:<s>|D:<d>|W:<w>|E:<e>|L:<l>|P:<p>|C:<c>|X:<x>"
Delta (only changed groups):
  "U|S:<s>|L:<l>|..."
```

Group field formats (comma-separated):

| Group | Fields |
|---|---|
| `S` | brake, steering, voltage, gear |
| `D` | fl, fr, trunk, locked |
| `W` | left_state, right_state (0=unknown, 1=closed, 2=open) |
| `E` | seat_flo, seat_fro, seatbelt_flo, seatbelt_fro |
| `L` | demi, normal |
| `P` | rear_l, rear_r |
| `C` | brake_pressed, acc_power, cameras, car_lock, car_unlock |
| `X` | charging, lock_state(0/1), wca, wcr_secs, lr, is_night |

(`wca` = window_close_active 0/1, `wcr_secs` = window_close_remaining seconds,
`lr` = light_reminder_enabled 0/1.)

Send the full frame on connect; send `U|...` deltas containing only the groups
whose values changed since the last send. **`/ws` carries only car-status** — not
TPMS pressures or a speed limit. Full state (including TPMS) is also available as
JSON from `GET /car/status`.

### Command downlink — HTTP POST (phone → ESP32)

Commands are HTTP requests to the webserver (`src/webserver/*_endpoint.cpp`).
POSTs are fire-and-forget from the app's view — resulting state changes flow back
over `/ws`.

| Endpoint | Params | Action |
|---|---|---|
| `POST /car/lock` / `/car/unlock` | — | Lock / unlock the car |
| `POST /car/accessory-power` | `state=on\|off\|toggle` | Accessory power |
| `POST /car/inside-cameras` | `state=on\|off\|toggle` | Inside cameras |
| `POST /car/windows/close` / `/stop` | — | Start 30 s auto-close / stop |
| `POST /car/windows/down` / `/up` | `side=left\|right\|both`, `state=on\|off` | Roll window |
| `POST /car/buzzer` | `state=on\|off\|beep`, `duration=<ms>` | Buzzer/horn |
| `POST /car/light-reminder` | `state=on\|off\|toggle` | Headlight reminder |
| `POST /car/charger-unlock` | — | Unlock charger port |
| `POST /car/side-mirrors` | `action=open\|close` | Side mirrors |
| `POST /car/frunk/open` | — | Frunk (see safety below) |
| `GET  /car/status` | — | Full status JSON (no auth) |
| `GET  /tpms/calibrate` | — | Current TPMS sensor-ID assignments |
| `POST /tpms/calibrate` | `action=reset`, or `action=swap&a=<>&b=<>` | Re-learn / swap tires |

When adding a new command, add the endpoint under `src/webserver/` and update
`VF3ApiService` / `VF3Repository` on the Android side.

### Headlight reminder logic

The light reminder beeps periodically when **all** of these hold:
- It's nighttime (6 PM – 6 AM)
- The gear is in Drive (D)
- Normal headlights are off
- Light reminder is enabled (`light-reminder:on`)

(The Android app mirrors this logic for its own voice warning; keep them aligned.)

### Frunk safety / rate limiting

If the firmware exposes a frunk-open command, keep the original safeguards:
- **Disabled while the vehicle is in Drive (D).**
- **2-second cooldown** between operations to protect the relay.

## OTA Updates

OTA via **ArduinoOTA** is available when the ESP32 is joined to a WiFi network
(in addition to the webserver's `POST /ota/update` endpoint):

- **Hostname**: `VF3-Smart`
- **Port**: 3232 (default)

```bash
# Upload firmware via OTA
pio run --target upload --upload-port VF3-Smart.local
# Or by IP
pio run --target upload --upload-port <esp32-ip>
```

If the ESP32 no longer joins WiFi at all in your build, OTA is unavailable —
flash over USB with `pio run --target upload`.

## Factory Reset

Two ways to reset: `POST /factory-reset` on the webserver (API-key auth), or the
**physical BOOT button** below (always works, no network needed).

**Hardware Configuration:**
- **Pin:** GPIO 0 (BOOT button on ESP32 Dev Module)
- **Trigger:** Hold button for 10 seconds
- **Button State:** Active LOW (pressed = LOW)
- **Pull-up:** Internal pull-up enabled (INPUT_PULLUP)

**How to Trigger:**
1. Press and hold BOOT for 10 seconds
2. Serial monitor shows a countdown (release to cancel)
3. The device clears stored configuration (NVS) and restarts

**Why a hardware reset:** no network or app needed, physical access required,
always available as a last resort.

**Wiring (custom PCB):** momentary push button between GPIO 0 and GND (internal
pull-up).

## Dependencies

- **ESPAsyncWebServer** / **AsyncTCP** — HTTP webserver + `/ws` WebSocket
- **ArduinoJson** — JSON responses (`GET /car/status`, TPMS, config). The `/ws`
  delta wire format is delimited text, not JSON.
</content>