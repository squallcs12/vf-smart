# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an ESP32-based MCU control system for a VinFast VF3 electric car. The project uses PlatformIO with the Arduino framework to manage vehicle sensors, controls, and safety features.

The ESP32 talks to the companion Android app **exclusively over Bluetooth Low Energy (BLE)**. There is **no webserver, WebSocket, UDP discovery, WiFi onboarding, or API key** — those were removed. The Android app is the BLE **peripheral / GATT server**; the ESP32 is the BLE **client**. See [BLE Communication](#ble-communication) and the Android side in `android/app/.../navigation/VF3GattServer.kt`.

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
- **Connectivity**: BLE client (connects to the Android app's GATT server)

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
4. Push status changes over BLE; apply any received BLE commands
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
7. If the feature exposes state or a command, add it to the BLE status/command protocol below

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
- Only sends a BLE status delta if voltage changes by ±0.1V
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
characteristic (`brake,steering,voltage,gear`) — see [Status uplink](#status-uplink-esp32--phone).

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

## BLE Communication

All car ↔ app communication is BLE GATT. **The Android app is the GATT server /
peripheral and advertises the service; the ESP32 is the GATT client** that scans
for the service, connects, writes status, and subscribes to receive commands.

This contract is the source of truth shared with the Android app
(`android/app/src/main/kotlin/com/daotranbang/vfsmart/navigation/VF3GattServer.kt`).
Keep both sides in sync.

### Service and Characteristics

**Service UUID:** `A1B2C3D4-E5F6-7890-ABCD-EF1234567890`

| Characteristic | UUID | Direction | ESP32 operation |
|---|---|---|---|
| TPMS | `A1B2C3D4-E5F6-7890-ABCD-EF1234567893` | ESP32 → phone | **Write** |
| Speed limit | `A1B2C3D4-E5F6-7890-ABCD-EF1234567894` | ESP32 → phone | **Write** |
| Car status (delta) | `A1B2C3D4-E5F6-7890-ABCD-EF1234567895` | ESP32 → phone | **Write** |
| Command | `A1B2C3D4-E5F6-7890-ABCD-EF1234567896` | phone → ESP32 | **Subscribe (notify)** |

The Command characteristic carries a standard CCCD descriptor
(`00002902-0000-1000-8000-00805f9b34fb`). The ESP32 must **write the CCCD to
enable notifications** after connecting, or it will receive no commands.

### Connection flow (ESP32 side)

1. Scan for the service UUID and connect to the Android app.
2. Discover the four characteristics.
3. Enable notifications on the Command characteristic (write CCCD = `0x0001`).
4. On connect, send a **full** car-status frame (and current TPMS / speed limit).
5. Thereafter, write status **deltas** whenever a value changes; send a full
   frame periodically (e.g. every 60 s) as a heartbeat.
6. Handle incoming command notifications (see [Command downlink](#command-downlink-phone--esp32)).

All payloads are UTF-8 strings.

### Status uplink (ESP32 → phone)

#### TPMS characteristic
```
"FL_KPA,FL_TEMP,FL_ALARM|FR_KPA,FR_TEMP,FR_ALARM|RL_...|RR_..."
```
e.g. `225.5,28,0|227.0,29,0|220.0,27,0|221.5,28,1`
(kPa float, temp °C int, alarm 0/1; four tires separated by `|`).

#### Speed limit characteristic
The current speed limit in km/h as a plain integer string, e.g. `50`.

#### Car status characteristic — delta protocol
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
whose values changed since the last send.

### Command downlink (phone → ESP32)

Commands arrive as notifications on the Command characteristic — UTF-8 strings of
the form `verb[:args]` with comma-separated args. The ESP32 parses the verb and
acts; commands are **fire-and-forget** (no reply is expected, though state changes
will naturally flow back as status deltas).

| Command | Action |
|---|---|
| `lock` / `unlock` | Lock / unlock the car |
| `acc:on` / `acc:off` / `acc:toggle` | Accessory power |
| `cameras:on` / `cameras:off` / `cameras:toggle` | Inside cameras |
| `windows:close` | Start 30-second window auto-close |
| `windows:stop` | Stop window operation |
| `window:down,<side>,<on\|off>` | Roll window down (side: left/right/both) |
| `window:up,<side>,<on\|off>` | Roll window up |
| `buzzer:beep,<ms>` / `buzzer:on` / `buzzer:off` | Buzzer/horn |
| `light-reminder:on` / `off` / `toggle` | Headlight reminder |
| `charger-unlock` | Unlock charger port |
| `mirrors:open` / `mirrors:close` | Side mirrors |
| `odo:on` / `odo:off` / `odo:toggle` | ODO screen |
| `armrest:on` / `armrest:off` / `armrest:toggle` | Armrest |
| `dashcam:on` / `dashcam:off` / `dashcam:toggle` | Dashcam |
| `tpms:reset` | Clear all TPMS sensor-ID assignments (re-learn) |
| `tpms:swap,<a>,<b>` | Swap two tire positions (a/b: fl/fr/rl/rr) |

When adding a new command, update both this table and `VF3GattServer` /
`VF3Repository` on the Android side.

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

OTA via **ArduinoOTA** remains available when the ESP32 is joined to a WiFi
network (it is independent of the removed webserver):

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

## Factory Reset (Physical Button)

With no webserver there is no HTTP factory-reset endpoint. Reset is via the
**physical BOOT button** only.

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

- **NimBLE-Arduino** (or the ESP32 Arduino BLE library) — BLE GATT client
- **ArduinoJson** — only if still used internally; the BLE wire format above is
  delimited text, not JSON, so JSON is no longer required for app communication
</content>