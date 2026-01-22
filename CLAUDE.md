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
